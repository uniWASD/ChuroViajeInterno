package com.churoviaje.backend.infraestructura.pg;

import static com.churoviaje.backend.infraestructura.pg.ValoresSql.ponerInstante;

import com.churoviaje.backend.config.ConfiguracionAccesoDatos;
import com.churoviaje.backend.dominio.Voto;
import com.churoviaje.backend.dominio.VotoRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

/** {@link VotoRepository} con JDBC y sentencias preparadas (D-37) sobre la tabla {@code voto} (V6). */
@Repository
public class PgVotoRepository implements VotoRepository {

    private final DataSource conexiones;

    public PgVotoRepository(@Qualifier(ConfiguracionAccesoDatos.CONEXIONES_DE_LA_TRANSACCION) DataSource conexiones) {
        this.conexiones = conexiones;
    }

    @Override
    public boolean yaVoto(long eventoId, long conductorId) {
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(
                        "select exists (select 1 from voto where evento_id = ? and conductor_id = ?)")) {
            sentencia.setLong(1, eventoId);
            sentencia.setLong(2, conductorId);
            try (ResultSet fila = sentencia.executeQuery()) {
                fila.next();
                return fila.getBoolean(1);
            }
        } catch (SQLException e) {
            throw new ErrorDeBaseDeDatos("revisar si el conductor ya voto", e);
        }
    }

    /** {@code on conflict do nothing} sobre {@code uq_voto_evento_conductor}: el voto repetido no se guarda. */
    @Override
    public boolean guardarSiNoVoto(Voto voto) {
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement("""
                        insert into voto (evento_id, conductor_id, valor, fecha, simulado) values (?, ?, ?, ?, ?)
                        on conflict (evento_id, conductor_id) do nothing
                        """)) {
            sentencia.setLong(1, voto.eventoId());
            sentencia.setLong(2, voto.conductorId());
            sentencia.setString(3, voto.valor().name());
            ponerInstante(sentencia, 4, voto.fecha());
            sentencia.setBoolean(5, voto.simulado());
            return sentencia.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new ErrorDeBaseDeDatos("guardar el voto", e);
        }
    }
}
