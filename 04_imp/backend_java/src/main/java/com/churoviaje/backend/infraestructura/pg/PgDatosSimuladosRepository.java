package com.churoviaje.backend.infraestructura.pg;

import com.churoviaje.backend.config.ConfiguracionAccesoDatos;
import com.churoviaje.backend.dominio.DatosSimuladosRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

/**
 * {@link DatosSimuladosRepository} con JDBC y sentencias preparadas (D-37). Cada sentencia filtra por
 * {@code simulado = true}: ninguna toca una fila real (RN-07.8, RN-07.9). El orden importa: primero los votos, después
 * los eventos y al final los conductores, así ningún borrado en cascada alcanza algo que no esté marcado.
 */
@Repository
public class PgDatosSimuladosRepository implements DatosSimuladosRepository {

    private final DataSource conexiones;

    public PgDatosSimuladosRepository(
            @Qualifier(ConfiguracionAccesoDatos.CONEXIONES_DE_LA_TRANSACCION) DataSource conexiones) {
        this.conexiones = conexiones;
    }

    @Override
    public Borrado borrarTodo() {
        try (Connection conexion = conexiones.getConnection()) {
            List<Long> seMostraban = new ArrayList<>();
            try (PreparedStatement sentencia = conexion.prepareStatement(
                    "select id from evento_vial where simulado = true and estado in ('ACTIVO', 'CONFIRMADO') order by id");
                    ResultSet fila = sentencia.executeQuery()) {
                while (fila.next()) {
                    seMostraban.add(fila.getLong(1));
                }
            }
            int votos = ejecutar(conexion, "delete from voto where simulado = true");
            int eventos = ejecutar(conexion, "delete from evento_vial where simulado = true");
            int conductores = ejecutar(conexion, "delete from conductor where simulado = true");
            return new Borrado(votos, eventos, conductores, List.copyOf(seMostraban));
        } catch (SQLException e) {
            throw new ErrorDeBaseDeDatos("borrar los datos simulados", e);
        }
    }

    @Override
    public long contarFilasSimuladas() {
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement("""
                        select (select count(*) from voto where simulado = true)
                             + (select count(*) from evento_vial where simulado = true)
                             + (select count(*) from conductor where simulado = true)
                        """);
                ResultSet fila = sentencia.executeQuery()) {
            fila.next();
            return fila.getLong(1);
        } catch (SQLException e) {
            throw new ErrorDeBaseDeDatos("contar los datos simulados", e);
        }
    }

    private static int ejecutar(Connection conexion, String sql) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            return sentencia.executeUpdate();
        }
    }
}
