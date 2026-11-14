package com.churoviaje.backend.infraestructura.pg;

import com.churoviaje.backend.config.ConfiguracionAccesoDatos;
import com.churoviaje.backend.dominio.catalogo.CatalogoRepository;
import com.churoviaje.backend.dominio.catalogo.CatalogosRespuesta.TipoCombustibleDto;
import com.churoviaje.backend.dominio.catalogo.CatalogosRespuesta.TipoEventoDto;
import com.churoviaje.backend.dominio.catalogo.TipoCombustible;
import com.churoviaje.backend.dominio.catalogo.TipoEvento;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

/** {@link CatalogoRepository} con JDBC y sentencias preparadas (D-37) sobre las tablas de la migracion V1. */
@Repository
public class PgCatalogoRepository implements CatalogoRepository {

    private static final String TIPOS_DE_EVENTO =
            "select codigo, nombre, color, vigencia_minutos, reportable from tipo_evento";
    private static final String TIPOS_DE_COMBUSTIBLE = "select codigo, nombre from tipo_combustible";

    private final DataSource conexiones;

    public PgCatalogoRepository(
            @Qualifier(ConfiguracionAccesoDatos.CONEXIONES_DE_LA_TRANSACCION) DataSource conexiones) {
        this.conexiones = conexiones;
    }

    @Override
    public Optional<TipoEvento> buscarTipoDeEvento(String codigo) {
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(TIPOS_DE_EVENTO + " where codigo = ?")) {
            sentencia.setString(1, codigo);
            try (ResultSet fila = sentencia.executeQuery()) {
                return fila.next() ? Optional.of(tipoDeEvento(fila)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new ErrorDeBaseDeDatos("leer el tipo de evento", e);
        }
    }

    @Override
    public List<TipoEventoDto> tiposDeEventoParaMostrar() {
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(TIPOS_DE_EVENTO);
                ResultSet fila = sentencia.executeQuery()) {
            List<TipoEventoDto> tipos = new ArrayList<>();
            while (fila.next()) {
                tipos.add(TipoEventoDto.de(tipoDeEvento(fila)));
            }
            return tipos;
        } catch (SQLException e) {
            throw new ErrorDeBaseDeDatos("leer los tipos de evento", e);
        }
    }

    @Override
    public List<TipoCombustibleDto> tiposDeCombustibleParaMostrar() {
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(TIPOS_DE_COMBUSTIBLE);
                ResultSet fila = sentencia.executeQuery()) {
            List<TipoCombustibleDto> tipos = new ArrayList<>();
            while (fila.next()) {
                // El tipo de combustible decide si el conductor puede elegirlo (RN-05.9).
                tipos.add(TipoCombustibleDto.de(new TipoCombustible(fila.getString("codigo"), fila.getString("nombre"))));
            }
            return tipos;
        } catch (SQLException e) {
            throw new ErrorDeBaseDeDatos("leer los tipos de combustible", e);
        }
    }

    private static TipoEvento tipoDeEvento(ResultSet fila) throws SQLException {
        return new TipoEvento(fila.getString("codigo"), fila.getString("nombre"), fila.getString("color"),
                fila.getInt("vigencia_minutos"), fila.getBoolean("reportable"));
    }
}
