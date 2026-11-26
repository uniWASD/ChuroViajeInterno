package com.churoviaje.backend.infraestructura.pg;

import static com.churoviaje.backend.infraestructura.pg.ValoresSql.leerInstante;
import static com.churoviaje.backend.infraestructura.pg.ValoresSql.ponerInstante;

import com.churoviaje.backend.config.ConfiguracionAccesoDatos;
import com.churoviaje.backend.dominio.EstadoCombustible;
import com.churoviaje.backend.dominio.EstadoCombustibleRepository;
import com.churoviaje.backend.dominio.EstadoCombustibleRepository.ResultadoAnh;
import com.churoviaje.backend.dominio.OrigenCombustible;
import com.churoviaje.backend.dominio.catalogo.ValorFila;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

/** {@link EstadoCombustibleRepository} con JDBC y sentencias preparadas (D-37) sobre la tabla de V8. */
@Repository
public class PgEstadoCombustibleRepository implements EstadoCombustibleRepository {

    /**
     * RN-06.17: el {@code where} del {@code on conflict} deja el dato que hay si el nuevo no es más reciente. Lo
     * decide la base en la misma sentencia, así dos lotes a la vez no se pisan.
     */
    private static final String GUARDAR = """
            insert into estado_combustible (surtidor_id, tipo_codigo, origen, disponible, fila, fecha_dato, litros,
                                            estado_anh, consultado_en, actualizado_en)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (surtidor_id, tipo_codigo, origen) do update
               set disponible = excluded.disponible, fila = excluded.fila, fecha_dato = excluded.fecha_dato,
                   litros = excluded.litros, estado_anh = excluded.estado_anh,
                   consultado_en = excluded.consultado_en, actualizado_en = excluded.actualizado_en
             where estado_combustible.fecha_dato < excluded.fecha_dato""";

    /**
     * RN-09.14: una lectura de ANH con un {@code updated_at} más viejo que el último aceptado no entra (el
     * {@code where} deja la fila como está y no devuelve nada); con el mismo, solo cambia el momento de la lectura.
     * Con uno más nuevo se guardan los litros y el estado de ANH, pero {@code fecha_dato} (desde cuándo ANH dice lo
     * mismo) solo pasa al {@code updated_at} nuevo si cambió la disponibilidad o subieron los litros (D-50). Así el
     * {@code updated_at} que ANH renueva sin que cambie nada no tapa a WhatsApp ni a los conductores (RN-05.3).
     * {@code previo} ve la fila de antes, para saber qué pasó. Una fila sin {@code anh_actualizado} (la guardó la
     * versión anterior a V15) usa {@code fecha_dato} en su lugar.
     */
    private static final String GUARDAR_DE_ANH = """
            with previo as (
                select coalesce(anh_actualizado, fecha_dato) as anh_actualizado from estado_combustible
                 where surtidor_id = ? and tipo_codigo = ? and origen = 'ANH')
            insert into estado_combustible as e (surtidor_id, tipo_codigo, origen, disponible, fila, fecha_dato, litros,
                                                 estado_anh, consultado_en, actualizado_en, anh_actualizado)
            values (?, ?, 'ANH', ?, null, ?, ?, ?, ?, ?, ?)
            on conflict (surtidor_id, tipo_codigo, origen) do update
               set fecha_dato = case
                       when coalesce(e.anh_actualizado, e.fecha_dato) = excluded.anh_actualizado then e.fecha_dato
                       when e.disponible <> excluded.disponible then excluded.fecha_dato
                       when excluded.litros > e.litros then excluded.fecha_dato
                       else e.fecha_dato end,
                   disponible = case
                       when coalesce(e.anh_actualizado, e.fecha_dato) = excluded.anh_actualizado then e.disponible
                       else excluded.disponible end,
                   litros = case
                       when coalesce(e.anh_actualizado, e.fecha_dato) = excluded.anh_actualizado then e.litros
                       else excluded.litros end,
                   estado_anh = case
                       when coalesce(e.anh_actualizado, e.fecha_dato) = excluded.anh_actualizado then e.estado_anh
                       else excluded.estado_anh end,
                   anh_actualizado = excluded.anh_actualizado,
                   consultado_en = excluded.consultado_en,
                   actualizado_en = case
                       when coalesce(e.anh_actualizado, e.fecha_dato) = excluded.anh_actualizado then e.actualizado_en
                       else excluded.actualizado_en end
             where coalesce(e.anh_actualizado, e.fecha_dato) <= excluded.anh_actualizado
            returning (select anh_actualizado from previo) as anh_previo""";

    private final DataSource conexiones;

    public PgEstadoCombustibleRepository(
            @Qualifier(ConfiguracionAccesoDatos.CONEXIONES_DE_LA_TRANSACCION) DataSource conexiones) {
        this.conexiones = conexiones;
    }

    @Override
    public boolean guardarSiEsMasReciente(EstadoCombustible estado, java.time.Instant ahora) {
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(GUARDAR)) {
            sentencia.setLong(1, estado.surtidorId());
            sentencia.setString(2, estado.tipoCodigo());
            sentencia.setString(3, estado.origen().name());
            sentencia.setBoolean(4, estado.disponible());
            sentencia.setString(5, estado.fila() == null ? null : estado.fila().name());
            ponerInstante(sentencia, 6, estado.fecha());
            sentencia.setBigDecimal(7, estado.litros());
            sentencia.setString(8, estado.estadoAnh());
            if (estado.consultadoEn() == null) {
                sentencia.setNull(9, Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                ponerInstante(sentencia, 9, estado.consultadoEn());
            }
            ponerInstante(sentencia, 10, ahora);
            return sentencia.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new ErrorDeBaseDeDatos("guardar el estado de combustible", e);
        }
    }

    @Override
    public ResultadoAnh guardarDeAnh(EstadoCombustible estado, java.time.Instant ahora) {
        if (estado.origen() != OrigenCombustible.ANH) {
            throw new IllegalArgumentException("Solo datos de ANH");
        }
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(GUARDAR_DE_ANH)) {
            sentencia.setLong(1, estado.surtidorId());
            sentencia.setString(2, estado.tipoCodigo());
            sentencia.setLong(3, estado.surtidorId());
            sentencia.setString(4, estado.tipoCodigo());
            sentencia.setBoolean(5, estado.disponible());
            ponerInstante(sentencia, 6, estado.fecha());
            sentencia.setBigDecimal(7, estado.litros());
            sentencia.setString(8, estado.estadoAnh());
            ponerInstante(sentencia, 9, estado.consultadoEn());
            ponerInstante(sentencia, 10, ahora);
            // Al entrar, fecha_dato y anh_actualizado son el mismo updated_at (RN-09.13).
            ponerInstante(sentencia, 11, estado.fecha());
            try (ResultSet fila = sentencia.executeQuery()) {
                if (!fila.next()) {
                    return ResultadoAnh.MAS_VIEJO;
                }
                OffsetDateTime previa = fila.getObject("anh_previo", OffsetDateTime.class);
                return previa != null && previa.toInstant().equals(estado.fecha())
                        ? ResultadoAnh.RELEIDO : ResultadoAnh.GUARDADO;
            }
        } catch (SQLException e) {
            throw new ErrorDeBaseDeDatos("guardar el estado de combustible de ANH", e);
        }
    }

    @Override
    public List<EstadoCombustible> todos() {
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement("""
                        select surtidor_id, tipo_codigo, origen, disponible, fila, fecha_dato, litros, estado_anh,
                               consultado_en
                        from estado_combustible""");
                ResultSet fila = sentencia.executeQuery()) {
            List<EstadoCombustible> estados = new ArrayList<>();
            while (fila.next()) {
                String valorFila = fila.getString("fila");
                OffsetDateTime consultado = fila.getObject("consultado_en", OffsetDateTime.class);
                estados.add(new EstadoCombustible(fila.getLong("surtidor_id"), fila.getString("tipo_codigo"),
                        OrigenCombustible.valueOf(fila.getString("origen")), fila.getBoolean("disponible"),
                        valorFila == null ? null : ValorFila.valueOf(valorFila), leerInstante(fila, "fecha_dato"),
                        fila.getBigDecimal("litros"), fila.getString("estado_anh"),
                        consultado == null ? null : consultado.toInstant()));
            }
            return estados;
        } catch (SQLException e) {
            throw new ErrorDeBaseDeDatos("leer los estados de combustible", e);
        }
    }
}
