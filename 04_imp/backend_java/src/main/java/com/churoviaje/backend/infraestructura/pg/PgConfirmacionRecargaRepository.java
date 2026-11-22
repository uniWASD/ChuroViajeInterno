package com.churoviaje.backend.infraestructura.pg;

import static com.churoviaje.backend.infraestructura.pg.ValoresSql.ponerInstante;

import com.churoviaje.backend.config.ConfiguracionAccesoDatos;
import com.churoviaje.backend.dominio.ConfirmacionRecarga;
import com.churoviaje.backend.dominio.ConfirmacionRecargaRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

/** {@link ConfirmacionRecargaRepository} con JDBC y sentencias preparadas (D-37) sobre {@code confirmacion_recarga} (V11). */
@Repository
public class PgConfirmacionRecargaRepository implements ConfirmacionRecargaRepository {

    private final DataSource conexiones;

    public PgConfirmacionRecargaRepository(
            @Qualifier(ConfiguracionAccesoDatos.CONEXIONES_DE_LA_TRANSACCION) DataSource conexiones) {
        this.conexiones = conexiones;
    }

    @Override
    public boolean existeDesde(long conductorId, long surtidorId, String tipoCodigo, Instant desde) {
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement("""
                        select exists (select 1 from confirmacion_recarga
                                       where conductor_id = ? and surtidor_id = ? and tipo_codigo = ? and fecha > ?)
                        """)) {
            sentencia.setLong(1, conductorId);
            sentencia.setLong(2, surtidorId);
            sentencia.setString(3, tipoCodigo);
            ponerInstante(sentencia, 4, desde);
            try (ResultSet fila = sentencia.executeQuery()) {
                fila.next();
                return fila.getBoolean(1);
            }
        } catch (SQLException e) {
            throw new ErrorDeBaseDeDatos("revisar si la confirmación es repetida", e);
        }
    }

    @Override
    public void guardar(ConfirmacionRecarga confirmacion) {
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(
                        "insert into confirmacion_recarga (conductor_id, surtidor_id, tipo_codigo, fecha) values (?, ?, ?, ?)")) {
            sentencia.setLong(1, confirmacion.conductorId());
            sentencia.setLong(2, confirmacion.surtidorId());
            sentencia.setString(3, confirmacion.tipoCodigo());
            ponerInstante(sentencia, 4, confirmacion.fecha());
            sentencia.executeUpdate();
        } catch (SQLException e) {
            throw new ErrorDeBaseDeDatos("guardar la confirmación de recarga", e);
        }
    }

    @Override
    public int borrarAnterioresA(Instant limite) {
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(
                        "delete from confirmacion_recarga where fecha < ?")) {
            ponerInstante(sentencia, 1, limite);
            return sentencia.executeUpdate();
        } catch (SQLException e) {
            throw new ErrorDeBaseDeDatos("borrar las confirmaciones viejas", e);
        }
    }
}
