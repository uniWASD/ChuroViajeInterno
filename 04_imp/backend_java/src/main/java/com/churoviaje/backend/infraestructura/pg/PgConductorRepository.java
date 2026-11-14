package com.churoviaje.backend.infraestructura.pg;

import static com.churoviaje.backend.infraestructura.pg.ValoresSql.leerInstante;
import static com.churoviaje.backend.infraestructura.pg.ValoresSql.ponerInstante;

import com.churoviaje.backend.config.ConfiguracionAccesoDatos;
import com.churoviaje.backend.dominio.Conductor;
import com.churoviaje.backend.dominio.ConductorRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

/** {@link ConductorRepository} con JDBC y sentencias preparadas (D-37) sobre la tabla {@code conductor} (V3). */
@Repository
public class PgConductorRepository implements ConductorRepository {

    private static final String SELECCION = "select id, google_id, fecha_alta, simulado from conductor";

    private final DataSource conexiones;

    public PgConductorRepository(
            @Qualifier(ConfiguracionAccesoDatos.CONEXIONES_DE_LA_TRANSACCION) DataSource conexiones) {
        this.conexiones = conexiones;
    }

    @Override
    public Optional<Conductor> buscarPorId(long id) {
        return buscarUno(SELECCION + " where id = ?", id);
    }

    /** {@code for no key update} bloquea la fila sin impedir que se guarden eventos y votos que apuntan a ella. */
    @Override
    public Optional<Conductor> buscarYBloquear(long id) {
        return buscarUno(SELECCION + " where id = ? for no key update", id);
    }

    @Override
    public Optional<Conductor> buscarPorGoogleId(String googleId) {
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(SELECCION + " where google_id = ?")) {
            sentencia.setString(1, googleId);
            return leerUno(sentencia);
        } catch (SQLException e) {
            throw new ErrorDeBaseDeDatos("buscar al conductor por su cuenta de Google", e);
        }
    }

    /**
     * {@code on conflict do nothing} es la segunda barrera de RN-08.4: si otro primer ingreso de la misma cuenta
     * guardo al conductor antes, el alta no hace nada y se lee el que quedo guardado.
     */
    @Override
    public Conductor guardarNuevo(Conductor conductor) {
        if (conductor.getId() != null) {
            throw new IllegalArgumentException("El conductor ya esta guardado");
        }
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement("""
                        insert into conductor (google_id, fecha_alta, simulado) values (?, ?, ?)
                        on conflict (google_id) do nothing
                        returning id
                        """)) {
            sentencia.setString(1, conductor.getGoogleId());
            ponerInstante(sentencia, 2, conductor.getFechaAlta());
            sentencia.setBoolean(3, conductor.isSimulado());
            try (ResultSet fila = sentencia.executeQuery()) {
                if (fila.next()) {
                    return Conductor.desdeRegistro(fila.getLong("id"), conductor.getGoogleId(),
                            conductor.getFechaAlta(), conductor.isSimulado());
                }
            }
        } catch (SQLException e) {
            throw new ErrorDeBaseDeDatos("guardar al conductor", e);
        }
        return buscarPorGoogleId(conductor.getGoogleId())
                .orElseThrow(() -> new IllegalStateException("El conductor de esa cuenta de Google ya no existe"));
    }

    private Optional<Conductor> buscarUno(String sql, long id) {
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, id);
            return leerUno(sentencia);
        } catch (SQLException e) {
            throw new ErrorDeBaseDeDatos("buscar al conductor", e);
        }
    }

    private static Optional<Conductor> leerUno(PreparedStatement sentencia) throws SQLException {
        try (ResultSet fila = sentencia.executeQuery()) {
            return fila.next() ? Optional.of(conductor(fila)) : Optional.empty();
        }
    }

    static Conductor conductor(ResultSet fila) throws SQLException {
        return Conductor.desdeRegistro(fila.getLong("id"), fila.getString("google_id"),
                leerInstante(fila, "fecha_alta"), fila.getBoolean("simulado"));
    }
}
