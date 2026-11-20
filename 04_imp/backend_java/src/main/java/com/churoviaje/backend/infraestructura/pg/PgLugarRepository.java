package com.churoviaje.backend.infraestructura.pg;

import com.churoviaje.backend.config.ConfiguracionAccesoDatos;
import com.churoviaje.backend.dominio.Ubicacion;
import com.churoviaje.backend.dominio.busqueda.CategoriaLugar;
import com.churoviaje.backend.dominio.busqueda.Lugar;
import com.churoviaje.backend.dominio.busqueda.LugarRepository;
import com.churoviaje.backend.dominio.busqueda.TextoBusqueda;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

/** {@link LugarRepository} con JDBC y sentencias preparadas (D-37) sobre las tablas de V10. */
@Repository
public class PgLugarRepository implements LugarRepository {

    private static final String EXTRACTO = "select fecha_extracto, cantidad from lugar_extracto where id = 1";

    private static final String INSERTAR = """
            insert into lugar (clave, nombre, nombre_busqueda, categoria, ubicacion)
            values (?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography)""";

    private static final String GUARDAR_EXTRACTO = """
            insert into lugar_extracto (id, fecha_extracto, cantidad, cargado_en) values (1, ?, ?, now())
            on conflict (id) do update
            set fecha_extracto = excluded.fecha_extracto, cantidad = excluded.cantidad, cargado_en = now()""";

    private final DataSource conexiones;

    public PgLugarRepository(@Qualifier(ConfiguracionAccesoDatos.CONEXIONES_DE_LA_TRANSACCION) DataSource conexiones) {
        this.conexiones = conexiones;
    }

    @Override
    public Optional<Extracto> extractoCargado() {
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(EXTRACTO);
                ResultSet fila = sentencia.executeQuery()) {
            return fila.next()
                    ? Optional.of(new Extracto(fila.getDate("fecha_extracto").toLocalDate(), fila.getInt("cantidad")))
                    : Optional.empty();
        } catch (SQLException e) {
            throw new ErrorDeBaseDeDatos("leer el extracto de lugares", e);
        }
    }

    @Override
    public void reemplazar(java.time.LocalDate fechaExtracto, List<Lugar> lugares) {
        try (Connection conexion = conexiones.getConnection()) {
            try (Statement borrar = conexion.createStatement()) {
                borrar.executeUpdate("delete from lugar");
            }
            try (PreparedStatement insertar = conexion.prepareStatement(INSERTAR)) {
                for (Lugar lugar : lugares) {
                    insertar.setString(1, lugar.clave());
                    insertar.setString(2, lugar.nombre());
                    insertar.setString(3, TextoBusqueda.normalizar(lugar.nombre()));
                    insertar.setString(4, lugar.categoria().name());
                    insertar.setBigDecimal(5, lugar.ubicacion().longitud());
                    insertar.setBigDecimal(6, lugar.ubicacion().latitud());
                    insertar.addBatch();
                }
                insertar.executeBatch();
            }
            try (PreparedStatement guardar = conexion.prepareStatement(GUARDAR_EXTRACTO)) {
                guardar.setDate(1, Date.valueOf(fechaExtracto));
                guardar.setInt(2, lugares.size());
                guardar.executeUpdate();
            }
        } catch (SQLException e) {
            throw new ErrorDeBaseDeDatos("guardar los lugares", e);
        }
    }

    @Override
    public List<Encontrado> buscar(List<String> palabras, Ubicacion cerca, int limite) {
        if (palabras.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("select nombre, nombre_busqueda, categoria, ")
                .append("ST_Y(ubicacion::geometry) as latitud, ST_X(ubicacion::geometry) as longitud, ")
                .append(cerca == null ? "null::float8"
                        : "ST_Distance(ubicacion, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography)")
                .append(" as distancia from lugar where true");
        // Las palabras ya vienen normalizadas: solo letras y números, sin comodines de LIKE.
        palabras.forEach(palabra -> sql.append(" and (' ' || nombre_busqueda) like ?"));
        sql.append(cerca == null ? " order by nombre" : " order by distancia, nombre").append(" limit ?");
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(sql.toString())) {
            int i = 1;
            if (cerca != null) {
                sentencia.setBigDecimal(i++, cerca.longitud());
                sentencia.setBigDecimal(i++, cerca.latitud());
            }
            for (String palabra : palabras) {
                sentencia.setString(i++, "% " + palabra + "%");
            }
            sentencia.setInt(i, limite);
            try (ResultSet fila = sentencia.executeQuery()) {
                List<Encontrado> encontrados = new ArrayList<>();
                while (fila.next()) {
                    double distancia = fila.getDouble("distancia");
                    Double distanciaM = fila.wasNull() ? null : distancia;
                    encontrados.add(new Encontrado(fila.getString("nombre"), fila.getString("nombre_busqueda"),
                            CategoriaLugar.valueOf(fila.getString("categoria")),
                            new Ubicacion(BigDecimal.valueOf(fila.getDouble("latitud")),
                                    BigDecimal.valueOf(fila.getDouble("longitud"))),
                            distanciaM));
                }
                return encontrados;
            }
        } catch (SQLException e) {
            throw new ErrorDeBaseDeDatos("buscar lugares", e);
        }
    }
}
