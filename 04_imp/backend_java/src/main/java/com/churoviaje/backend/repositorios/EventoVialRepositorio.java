package com.churoviaje.backend.repositorios;

import com.churoviaje.backend.dominio.EstadoEvento;
import com.churoviaje.backend.dominio.EventoVial;
import com.churoviaje.backend.dominio.OrigenEvento;
import com.churoviaje.backend.dominio.Ubicacion;
import com.churoviaje.backend.dominio.Voto;
import com.churoviaje.backend.dominio.catalogo.TipoEvento;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Guarda y busca eventos viales con sentencias preparadas (D-37). La ubicacion se guarda como punto PostGIS:
 * {@code ST_MakePoint} recibe primero la longitud y despues la latitud.
 */
@Repository
public class EventoVialRepositorio {

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

    private static final RowMapper<EventoVial> MAPEO = EventoVialRepositorio::mapear;

    private final JdbcTemplate jdbc;

    public EventoVialRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Bloquea al conductor hasta que termine la transaccion, para que dos reportes suyos que llegan juntos se
     * revisen de a uno (RN-01.8, RN-01.9). Devuelve si es simulado, o vacio si el conductor ya no existe.
     */
    public Optional<Boolean> bloquearConductor(long conductorId) {
        return jdbc.query("select simulado from conductor where id = ? for no key update",
                (fila, n) -> fila.getBoolean("simulado"), conductorId).stream().findFirst();
    }

    /** RN-01.9: cuantos reportes registro el conductor desde {@code desde}. */
    public long contarReportesDesde(long conductorId, Instant desde) {
        Long cantidad = jdbc.queryForObject(
                "select count(*) from evento_vial where reportante_id = ? and registrado_en >= ?",
                Long.class, conductorId, Timestamp.from(desde));
        return cantidad == null ? 0 : cantidad;
    }

    /** RN-01.8: si el conductor ya tiene un evento del mismo tipo cerca y en el mismo rato que {@code nuevo}. */
    public boolean existeDuplicado(EventoVial nuevo) {
        Boolean existe = jdbc.queryForObject("""
                select exists (
                    select 1 from evento_vial
                    where reportante_id = ? and tipo_codigo = ?
                      and ocurrio_en > ? and ocurrio_en < ?
                      and ST_DWithin(ubicacion, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?))
                """, Boolean.class,
                nuevo.getReportanteId(), nuevo.getTipo().codigo(),
                Timestamp.from(nuevo.getOcurrioEn().minus(EventoVial.VENTANA_DUPLICADO)),
                Timestamp.from(nuevo.getOcurrioEn().plus(EventoVial.VENTANA_DUPLICADO)),
                nuevo.getUbicacion().longitud(), nuevo.getUbicacion().latitud(),
                EventoVial.DISTANCIA_DUPLICADO_METROS);
        return Boolean.TRUE.equals(existe);
    }

    /** Guarda el evento y devuelve su identificador. */
    public long insertar(EventoVial evento) {
        Long id = jdbc.queryForObject("""
                insert into evento_vial (tipo_codigo, ubicacion, ocurrio_en, registrado_en, actualizado_en,
                                         reportante_id, origen, nivel, estado, votos_real, votos_falso, simulado)
                values (?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                returning id
                """, Long.class,
                evento.getTipo().codigo(), evento.getUbicacion().longitud(), evento.getUbicacion().latitud(),
                Timestamp.from(evento.getOcurrioEn()), Timestamp.from(evento.getRegistradoEn()),
                Timestamp.from(evento.getRegistradoEn()), evento.getReportanteId(), evento.getOrigen().name(), evento.getNivel(), evento.getEstado().name(),
                evento.getVotosReal(), evento.getVotosFalso(), evento.isSimulado());
        if (id == null) {
            throw new IllegalStateException("La base no devolvio el identificador del evento");
        }
        return id;
    }
    /** RN-02.2 y RN-02.3: los eventos Activos o Confirmados que todavia no vencieron. */
    public List<EventoVial> vigentes(Instant ahora) {
        return jdbc.query(SELECCION + " where " + SE_MUESTRA + " and " + VENCE + " > ? order by e.id",
                MAPEO, Timestamp.from(ahora));
    }

    /**
     * RN-02.6: los eventos vigentes que nacieron o cambiaron despues de {@code marca} (por ejemplo, los que se
     * confirmaron).
     */
    public List<EventoVial> vigentesCambiadosDesde(Instant marca, Instant ahora) {
        return jdbc.query(SELECCION + " where " + SE_MUESTRA + " and " + VENCE + " > ? and e.actualizado_en > ?"
                + " order by e.id", MAPEO, Timestamp.from(ahora), Timestamp.from(marca));
    }

    /**
     * RN-02.4, RN-02.13 y RN-04.11: los identificadores de los eventos que dejaron de mostrarse entre
     * {@code marca} y {@code ahora}. Un retirado cuenta desde que se retiro; uno vencido, desde que vencio,
     * aunque la tarea periodica todavia no lo haya marcado (RN-02.4).
     */
    public List<Long> quitadosEntre(Instant marca, Instant ahora) {
        return jdbc.queryForList("""
                select e.id from evento_vial e join tipo_evento t on t.codigo = e.tipo_codigo
                where (e.estado = 'RETIRADO' and e.actualizado_en > ? and e.actualizado_en <= ?)
                   or (e.estado <> 'RETIRADO' and %s > ? and %s <= ?)
                order by e.id
                """.formatted(VENCE, VENCE), Long.class,
                Timestamp.from(marca), Timestamp.from(ahora), Timestamp.from(marca), Timestamp.from(ahora));
    }

    /** RN-02.4: marca como Vencido todo evento que supero su vigencia y devuelve cuales fueron. */
    public List<Long> vencerHasta(Instant ahora) {
        return jdbc.queryForList("""
                update evento_vial e set estado = 'VENCIDO', actualizado_en = greatest(?, e.registrado_en)
                from tipo_evento t
                where t.codigo = e.tipo_codigo and %s and %s <= ?
                returning e.id
                """.formatted(SE_MUESTRA, VENCE), Long.class, Timestamp.from(ahora), Timestamp.from(ahora));
    }

    /**
     * Trae el evento y lo bloquea hasta que termine la transaccion: dos votos que llegan juntos se cuentan de a
     * uno y ninguno se pierde (RN-02.13, RN-02.14).
     */
    public Optional<EventoVial> buscarParaVotar(long id) {
        return jdbc.query(SELECCION + " where e.id = ? for update of e", MAPEO, id).stream().findFirst();
    }

    /** Si el conductor esta registrado, devuelve si es simulado (RN-02.16). */
    public Optional<Boolean> conductorSimulado(long conductorId) {
        return jdbc.query("select simulado from conductor where id = ?",
                (fila, n) -> fila.getBoolean("simulado"), conductorId).stream().findFirst();
    }

    /** RN-02.11: si el conductor ya voto este evento. */
    public boolean yaVoto(long eventoId, long conductorId) {
        Boolean existe = jdbc.queryForObject(
                "select exists (select 1 from voto where evento_id = ? and conductor_id = ?)",
                Boolean.class, eventoId, conductorId);
        return Boolean.TRUE.equals(existe);
    }

    public void insertarVoto(Voto voto) {
        jdbc.update("insert into voto (evento_id, conductor_id, valor, fecha, simulado) values (?, ?, ?, ?, ?)",
                voto.eventoId(), voto.conductorId(), voto.valor().name(), Timestamp.from(voto.fecha()), voto.simulado());
    }

    /**
     * Guarda los votos y el estado del evento. Si el estado cambio, tambien su marca de cambio, para que la
     * consulta por cambios lo entregue (RN-02.6).
     */
    public void actualizarVotos(EventoVial evento, boolean cambioDeEstado, Instant ahora) {
        jdbc.update("""
                update evento_vial set votos_real = ?, votos_falso = ?, estado = ?,
                       actualizado_en = case when ? then ? else actualizado_en end
                where id = ?
                """, evento.getVotosReal(), evento.getVotosFalso(), evento.getEstado().name(), cambioDeEstado,
                Timestamp.from(ahora), evento.getId());
    }

    private static EventoVial mapear(ResultSet fila, int numero) throws SQLException {
        TipoEvento tipo = new TipoEvento(fila.getString("codigo"), fila.getString("nombre"), fila.getString("color"),
                fila.getInt("vigencia_minutos"), fila.getBoolean("reportable"));
        Ubicacion ubicacion = new Ubicacion(BigDecimal.valueOf(fila.getDouble("latitud")),
                BigDecimal.valueOf(fila.getDouble("longitud")));
        long idReportante = fila.getLong("reportante_id");
        Long reportante = fila.wasNull() ? null : idReportante;
        return EventoVial.desdeRegistro(fila.getLong("id"), tipo, ubicacion,
                fila.getTimestamp("ocurrio_en").toInstant(), fila.getTimestamp("registrado_en").toInstant(),
                reportante, OrigenEvento.valueOf(fila.getString("origen")),
                fila.getString("nivel"), EstadoEvento.valueOf(fila.getString("estado")), fila.getInt("votos_real"),
                fila.getInt("votos_falso"), fila.getBoolean("simulado"));
    }
}
