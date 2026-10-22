package bo.edu.ucb.churoviaje.infraestructura.persistencia;

import bo.edu.ucb.churoviaje.dominio.catalogo.ClaveAlias;
import bo.edu.ucb.churoviaje.dominio.catalogo.Surtidor;
import bo.edu.ucb.churoviaje.dominio.catalogo.TipoCombustible;
import bo.edu.ucb.churoviaje.dominio.catalogo.TipoEvento;
import bo.edu.ucb.churoviaje.dominio.ubicacion.Coordenada;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogosJdbc {
    private final JdbcTemplate jdbc;

    public CatalogosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TipoEvento> tiposEvento() {
        return jdbc.query("SELECT codigo, nombre, color, vigencia_minutos, reportable FROM tipo_evento ORDER BY codigo",
                (fila, indice) -> new TipoEvento(fila.getString("codigo"), fila.getString("nombre"),
                        fila.getString("color"), fila.getInt("vigencia_minutos"),
                        fila.getBoolean("reportable")));
    }

    public List<TipoCombustible> tiposCombustible() {
        return jdbc.query("SELECT codigo, nombre FROM tipo_combustible ORDER BY codigo",
                (fila, indice) -> new TipoCombustible(fila.getString("codigo"), fila.getString("nombre")));
    }

    public List<Surtidor> surtidoresCercanos(double latitud, double longitud) {
        new Coordenada(longitud, latitud);
        Map<Long, List<String>> aliases = new HashMap<>();
        jdbc.query("SELECT surtidor_id, alias FROM surtidor_alias ORDER BY alias", filas -> {
            while (filas.next()) {
                aliases.computeIfAbsent(filas.getLong("surtidor_id"), clave -> new ArrayList<>())
                        .add(filas.getString("alias"));
            }
            return null;
        });
        return jdbc.query("""
                SELECT s.id, s.nombre, ST_X(s.ubicacion::geometry) AS longitud,
                       ST_Y(s.ubicacion::geometry) AS latitud
                FROM surtidor s
                ORDER BY ST_Distance(s.ubicacion, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography)
                """, (fila, indice) -> new Surtidor(fila.getLong("id"), fila.getString("nombre"),
                new Coordenada(fila.getDouble("longitud"), fila.getDouble("latitud")),
                aliases.getOrDefault(fila.getLong("id"), List.of())), longitud, latitud);
    }

    public Optional<Long> buscarPorNombreOAlias(String texto) {
        String clave = ClaveAlias.normalizar(texto);
        List<Long> ids = jdbc.queryForList("""
                SELECT s.id FROM surtidor s
                JOIN surtidor_alias a ON a.surtidor_id = s.id
                WHERE a.alias = ?
                """, Long.class, clave);
        return ids.stream().findFirst();
    }

}
