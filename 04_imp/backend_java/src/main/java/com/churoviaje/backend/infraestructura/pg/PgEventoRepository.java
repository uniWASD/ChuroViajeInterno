package com.churoviaje.backend.infraestructura.pg;

import static com.churoviaje.backend.infraestructura.pg.ValoresSql.leerInstante;
import static com.churoviaje.backend.infraestructura.pg.ValoresSql.leerLongONulo;
import static com.churoviaje.backend.infraestructura.pg.ValoresSql.ponerInstante;
import static com.churoviaje.backend.infraestructura.pg.ValoresSql.ponerLongONulo;

import com.churoviaje.backend.config.ConfiguracionAccesoDatos;
import com.churoviaje.backend.dominio.EstadoEvento;
import com.churoviaje.backend.dominio.EventoRepository;
import com.churoviaje.backend.dominio.EventoVial;
import com.churoviaje.backend.dominio.EventoVialRespuesta;
import com.churoviaje.backend.dominio.OrigenEvento;
import com.churoviaje.backend.dominio.Ubicacion;
import com.churoviaje.backend.dominio.catalogo.TipoEvento;
import com.churoviaje.backend.infraestructura.pg.ValoresSql.LectorFila;
import com.churoviaje.backend.infraestructura.pg.ValoresSql.Parametros;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

/**
 * {@link EventoRepository} con JDBC y sentencias preparadas (D-37) sobre la tabla {@code evento_vial} (V4, V5). La
 * ubicacion se guarda como punto PostGIS: {@code ST_MakePoint} recibe primero la longitud y despues la latitud.
 */
@Repository
public class PgEventoRepository implements EventoRepository {

    /** Columnas de un evento con su tipo; la ubicacion sale como latitud y longitud. */
    private static final String SELECCION = """
            select e.id, e.ocurrio_en, e.registrado_en, e.reportante_id, e.origen, e.nivel, e.estado,
                   e.votos_real, e.votos_falso, e.simulado,
                   ST_Y(e.ubicacion::geometry) as latitud, ST_X(e.ubicacion::geometry) as longitud,
                   t.codigo, t.nombre, t.color, t.vigencia_minutos, t.reportable
            from evento_vial e join tipo_evento t on t.codigo = e.tipo_codigo
            """;
    /** Momento en que vence cada evento (RN-02.3): cuando ocurrio mas la vigencia de su tipo. */
    private static final String VENCE = "(e.ocurrio_en + make_interval(mins => t.vigencia_minutos))";
    private static final String SE_MUESTRA = "e.estado in ('ACTIVO', 'CONFIRMADO')";

    private final DataSource conexiones;

    public PgEventoRepository(@Qualifier(ConfiguracionAccesoDatos.CONEXIONES_DE_LA_TRANSACCION) DataSource conexiones) {
        this.conexiones = conexiones;
    }

    @Override
    public long contarReportesDesde(long conductorId, Instant desde) {
        List<Long> cantidad = listar("contar los reportes del conductor",
                "select count(*) from evento_vial where reportante_id = ? and registrado_en >= ?",
                sentencia -> {
                    sentencia.setLong(1, conductorId);
                    ponerInstante(sentencia, 2, desde);
                },
                fila -> fila.getLong(1));
        return cantidad.get(0);
    }

    @Override
    public boolean existeDuplicado(EventoVial nuevo) {
        List<Boolean> existe = listar("buscar un reporte duplicado", """
                select exists (
                    select 1 from evento_vial
                    where reportante_id = ? and tipo_codigo = ?
                      and ocurrio_en > ? and ocurrio_en < ?
                      and ST_DWithin(ubicacion, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?))
                """,
                sentencia -> {
                    ponerLongONulo(sentencia, 1, nuevo.getReportanteId());
                    sentencia.setString(2, nuevo.getTipo().codigo());
                    ponerInstante(sentencia, 3, nuevo.getOcurrioEn().minus(EventoVial.VENTANA_DUPLICADO));
                    ponerInstante(sentencia, 4, nuevo.getOcurrioEn().plus(EventoVial.VENTANA_DUPLICADO));
                    sentencia.setBigDecimal(5, nuevo.getUbicacion().longitud());
                    sentencia.setBigDecimal(6, nuevo.getUbicacion().latitud());
                    sentencia.setDouble(7, EventoVial.DISTANCIA_DUPLICADO_METROS);
                },
                fila -> fila.getBoolean(1));
        return existe.get(0);
    }

    @Override
    public long guardarNuevo(EventoVial evento) {
        List<Long> id = listar("guardar el evento", """
                insert into evento_vial (tipo_codigo, ubicacion, ocurrio_en, registrado_en, actualizado_en,
                                         reportante_id, origen, nivel, estado, votos_real, votos_falso, simulado)
                values (?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                returning id
                """,
                sentencia -> {
                    sentencia.setString(1, evento.getTipo().codigo());
                    sentencia.setBigDecimal(2, evento.getUbicacion().longitud());
                    sentencia.setBigDecimal(3, evento.getUbicacion().latitud());
                    ponerInstante(sentencia, 4, evento.getOcurrioEn());
                    ponerInstante(sentencia, 5, evento.getRegistradoEn());
                    ponerInstante(sentencia, 6, evento.getRegistradoEn());
                    ponerLongONulo(sentencia, 7, evento.getReportanteId());
                    sentencia.setString(8, evento.getOrigen().name());
                    sentencia.setString(9, evento.getNivel());
                    sentencia.setString(10, evento.getEstado().name());
                    sentencia.setInt(11, evento.getVotosReal());
                    sentencia.setInt(12, evento.getVotosFalso());
                    sentencia.setBoolean(13, evento.isSimulado());
                },
                fila -> fila.getLong("id"));
        if (id.isEmpty()) {
            throw new IllegalStateException("La base no devolvio el identificador del evento");
        }
        return id.get(0);
    }

    /** RN-02.2 y RN-02.3: los eventos Activos o Confirmados que todavia no vencieron. */
    @Override
    public List<EventoVial> vigentes(Instant ahora) {
        return listar("buscar los eventos vigentes",
                SELECCION + " where " + SE_MUESTRA + " and " + VENCE + " > ? order by e.id",
                sentencia -> ponerInstante(sentencia, 1, ahora),
                PgEventoRepository::evento);
    }

    @Override
    public List<EventoVialRespuesta> vigentesParaMostrar(Instant ahora) {
        return paraMostrar(vigentes(ahora), ahora);
    }

    @Override
    public List<EventoVialRespuesta> vigentesCambiadosDesdeParaMostrar(Instant marca, Instant ahora) {
        List<EventoVial> cambiados = listar("buscar los eventos que cambiaron",
                SELECCION + " where " + SE_MUESTRA + " and " + VENCE + " > ? and e.actualizado_en > ? order by e.id",
                sentencia -> {
                    ponerInstante(sentencia, 1, ahora);
                    ponerInstante(sentencia, 2, marca);
                },
                PgEventoRepository::evento);
        return paraMostrar(cambiados, ahora);
    }

    @Override
    public List<Long> quitadosEntre(Instant marca, Instant ahora) {
        return listar("buscar los eventos que dejaron de mostrarse", """
                select e.id from evento_vial e join tipo_evento t on t.codigo = e.tipo_codigo
                where (e.estado = 'RETIRADO' and e.actualizado_en > ? and e.actualizado_en <= ?)
                   or (e.estado <> 'RETIRADO' and %s > ? and %s <= ?)
                order by e.id
                """.formatted(VENCE, VENCE),
                sentencia -> {
                    ponerInstante(sentencia, 1, marca);
                    ponerInstante(sentencia, 2, ahora);
                    ponerInstante(sentencia, 3, marca);
                    ponerInstante(sentencia, 4, ahora);
                },
                fila -> fila.getLong("id"));
    }

    @Override
    public List<Long> vencerHasta(Instant ahora) {
        return listar("marcar los eventos vencidos", """
                update evento_vial e set estado = 'VENCIDO', actualizado_en = greatest(?, e.registrado_en)
                from tipo_evento t
                where t.codigo = e.tipo_codigo and %s and %s <= ?
                returning e.id
                """.formatted(SE_MUESTRA, VENCE),
                sentencia -> {
                    ponerInstante(sentencia, 1, ahora);
                    ponerInstante(sentencia, 2, ahora);
                },
                fila -> fila.getLong("id"));
    }

    @Override
    public Optional<EventoVial> buscarParaVotar(long id) {
        return listar("buscar el evento para votar", SELECCION + " where e.id = ? for update of e",
                sentencia -> sentencia.setLong(1, id),
                PgEventoRepository::evento).stream().findFirst();
    }

    @Override
    public void actualizarVotos(EventoVial evento, boolean cambioDeEstado, Instant ahora) {
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement("""
                        update evento_vial set votos_real = ?, votos_falso = ?, estado = ?,
                               actualizado_en = case when ? then ? else actualizado_en end
                        where id = ?
                        """)) {
            sentencia.setInt(1, evento.getVotosReal());
            sentencia.setInt(2, evento.getVotosFalso());
            sentencia.setString(3, evento.getEstado().name());
            sentencia.setBoolean(4, cambioDeEstado);
            ponerInstante(sentencia, 5, ahora);
            sentencia.setLong(6, evento.getId());
            sentencia.executeUpdate();
        } catch (SQLException e) {
            throw new ErrorDeBaseDeDatos("guardar los votos del evento", e);
        }
    }

    /**
     * La consulta ya trae solo los vigentes; igual, cada {@link EventoVial} responde si sigue vigente (RN-02.2 a
     * RN-02.4) y sale como DTO, sin datos de conductores (RN-02.5).
     */
    private static List<EventoVialRespuesta> paraMostrar(List<EventoVial> eventos, Instant ahora) {
        return eventos.stream().filter(evento -> evento.estaVigente(ahora)).map(EventoVialRespuesta::de).toList();
    }

    /**
     * Ejecuta una sentencia que devuelve filas ({@code select}, o {@code insert} y {@code update} con
     * {@code returning}) y convierte cada fila. La conexion se pide y se cierra aqui (D-37).
     */
    private <T> List<T> listar(String operacion, String sql, Parametros parametros, LectorFila<T> lector) {
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            parametros.poner(sentencia);
            try (ResultSet fila = sentencia.executeQuery()) {
                List<T> resultado = new ArrayList<>();
                while (fila.next()) {
                    resultado.add(lector.leer(fila));
                }
                return resultado;
            }
        } catch (SQLException e) {
            throw new ErrorDeBaseDeDatos(operacion, e);
        }
    }

    private static EventoVial evento(ResultSet fila) throws SQLException {
        TipoEvento tipo = new TipoEvento(fila.getString("codigo"), fila.getString("nombre"), fila.getString("color"),
                fila.getInt("vigencia_minutos"), fila.getBoolean("reportable"));
        Ubicacion ubicacion = new Ubicacion(BigDecimal.valueOf(fila.getDouble("latitud")),
                BigDecimal.valueOf(fila.getDouble("longitud")));
        return EventoVial.desdeRegistro(fila.getLong("id"), tipo, ubicacion, leerInstante(fila, "ocurrio_en"),
                leerInstante(fila, "registrado_en"), leerLongONulo(fila, "reportante_id"),
                OrigenEvento.valueOf(fila.getString("origen")), fila.getString("nivel"),
                EstadoEvento.valueOf(fila.getString("estado")), fila.getInt("votos_real"), fila.getInt("votos_falso"),
                fila.getBoolean("simulado"));
    }
}
