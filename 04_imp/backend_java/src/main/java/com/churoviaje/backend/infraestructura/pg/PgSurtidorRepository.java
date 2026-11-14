package com.churoviaje.backend.infraestructura.pg;

import com.churoviaje.backend.config.ConfiguracionAccesoDatos;
import com.churoviaje.backend.dominio.Surtidor;
import com.churoviaje.backend.dominio.SurtidorRepository;
import com.churoviaje.backend.dominio.Ubicacion;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

/** {@link SurtidorRepository} con JDBC y sentencias preparadas (D-37) sobre las tablas de V1 y V2. */
@Repository
public class PgSurtidorRepository implements SurtidorRepository {

    private static final String TODOS = """
            select s.id, s.nombre, ST_Y(s.ubicacion::geometry) as latitud, ST_X(s.ubicacion::geometry) as longitud,
                   coalesce(array_agg(a.alias order by a.alias) filter (where a.alias is not null), '{}') as alias
            from surtidor s
            left join surtidor_alias a on a.surtidor_id = s.id
            group by s.id
            order by s.nombre""";

    private final DataSource conexiones;

    public PgSurtidorRepository(
            @Qualifier(ConfiguracionAccesoDatos.CONEXIONES_DE_LA_TRANSACCION) DataSource conexiones) {
        this.conexiones = conexiones;
    }

    @Override
    public List<Surtidor> todos() {
        try (Connection conexion = conexiones.getConnection();
                PreparedStatement sentencia = conexion.prepareStatement(TODOS);
                ResultSet fila = sentencia.executeQuery()) {
            List<Surtidor> surtidores = new ArrayList<>();
            while (fila.next()) {
                surtidores.add(surtidor(fila));
            }
            return surtidores;
        } catch (SQLException e) {
            throw new ErrorDeBaseDeDatos("leer los surtidores", e);
        }
    }

    static Surtidor surtidor(ResultSet fila) throws SQLException {
        Array alias = fila.getArray("alias");
        List<String> lista = Arrays.asList((String[]) alias.getArray());
        alias.free();
        return new Surtidor(fila.getLong("id"), fila.getString("nombre"),
                new Ubicacion(BigDecimal.valueOf(fila.getDouble("latitud")), BigDecimal.valueOf(fila.getDouble("longitud"))),
                lista);
    }
}
