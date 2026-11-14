package com.churoviaje.backend.infraestructura.pg;

import static com.churoviaje.backend.infraestructura.pg.ValoresSql.leerInstante;
import static com.churoviaje.backend.infraestructura.pg.ValoresSql.ponerInstante;

import com.churoviaje.backend.config.ConfiguracionAccesoDatos;
import com.churoviaje.backend.dominio.Conductor;
import com.churoviaje.backend.dominio.Sesion;
import com.churoviaje.backend.dominio.SesionRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

/** {@link SesionRepository} con JDBC y sentencias preparadas (D-37) sobre la tabla {@code sesion} (V3). */
@Repository
public class PgSesionRepository implements SesionRepository {

    private final DataSource conexiones;

    public PgSesionRepository(
            @Qualifier(ConfiguracionAccesoDatos.CONEXIONES_DE_LA_TRANSACCION) DataSource conexiones) {
        this.conexiones = conexiones;
    }

    @Override
    public void guardar(Sesion sesion) {
        Long conductorId = Objects.requireNonNull(sesion.getConductor().getId(),
                "El conductor de la sesion todavia no esta guardado");
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement("""
                        insert into sesion (conductor_id, token_renovacion_hash, emitida_en, expira_en)
                        values (?, ?, ?, ?)
                        """)) {
            sentencia.setLong(1, conductorId);
            sentencia.setString(2, sesion.getTokenRenovacionHash());
            ponerInstante(sentencia, 3, sesion.getEmitidaEn());
            ponerInstante(sentencia, 4, sesion.getExpiraEn());
            sentencia.executeUpdate();
        } catch (SQLException e) {
            throw new ErrorDeBaseDeDatos("guardar la sesion", e);
        }
    }

    @Override
    public Optional<Sesion> buscarPorHuellaDeToken(String huella) {
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement("""
                        select s.id as sesion_id, s.token_renovacion_hash, s.emitida_en, s.expira_en,
                               c.id, c.google_id, c.fecha_alta, c.simulado
                        from sesion s join conductor c on c.id = s.conductor_id
                        where s.token_renovacion_hash = ?
                        """)) {
            sentencia.setString(1, huella);
            try (ResultSet fila = sentencia.executeQuery()) {
                if (!fila.next()) {
                    return Optional.empty();
                }
                Conductor conductor = PgConductorRepository.conductor(fila);
                return Optional.of(Sesion.desdeRegistro(fila.getLong("sesion_id"), conductor,
                        fila.getString("token_renovacion_hash"), leerInstante(fila, "emitida_en"),
                        leerInstante(fila, "expira_en")));
            }
        } catch (SQLException e) {
            throw new ErrorDeBaseDeDatos("buscar la sesion", e);
        }
    }

    @Override
    public void borrarPorHuellaDeToken(String huella) {
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(
                        "delete from sesion where token_renovacion_hash = ?")) {
            sentencia.setString(1, huella);
            sentencia.executeUpdate();
        } catch (SQLException e) {
            throw new ErrorDeBaseDeDatos("borrar la sesion", e);
        }
    }
}
