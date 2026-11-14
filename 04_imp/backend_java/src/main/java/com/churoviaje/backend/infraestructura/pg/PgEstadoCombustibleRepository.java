package com.churoviaje.backend.infraestructura.pg;

import static com.churoviaje.backend.infraestructura.pg.ValoresSql.ponerInstante;

import com.churoviaje.backend.config.ConfiguracionAccesoDatos;
import com.churoviaje.backend.dominio.EstadoCombustible;
import com.churoviaje.backend.dominio.EstadoCombustibleRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
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
}
