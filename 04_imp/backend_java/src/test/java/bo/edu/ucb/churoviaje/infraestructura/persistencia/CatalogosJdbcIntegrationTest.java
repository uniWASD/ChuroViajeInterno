package bo.edu.ucb.churoviaje.infraestructura.persistencia;

import bo.edu.ucb.churoviaje.dominio.catalogo.ClaveAlias;
import bo.edu.ucb.churoviaje.dominio.catalogo.ServicioBusquedaSurtidores;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.junit.jupiter.api.Assertions.*;

/** CU-05 / CU-06 · Se ejecuta únicamente cuando V2 contiene surtidores ANH aprobados. */
@Testcontainers
class CatalogosJdbcIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.6-alpine")
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("churoviaje_catalogos")
            .withUsername("churoviaje_pruebas")
            .withPassword("temporal_no_real");

    @Test
    void consultasCatalogosDistanciaYAliasOficiales() {
        // No inyectar datos ficticios: migraciones en base vacía + estaciones SOLO desde ANH.
        var flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(true).load();
        assertTrue(flyway.migrate().migrationsExecuted >= 2,
                "CP-23 sigue bloqueado hasta disponer de V2 con estaciones ANH aprobadas");
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        var catalogos = new CatalogosJdbc(jdbc);
        assertEquals(5, catalogos.tiposEvento().size());
        assertEquals(5, catalogos.tiposCombustible().size());
        assertNull(catalogos.tiposEvento().stream().filter(e -> e.codigo().equals("CONGESTION"))
                .findFirst().orElseThrow().color(), "P-06 pendiente; no simular color");

        double latitud = -21.5355;
        double longitud = -64.7296;
        var cercanos = catalogos.surtidoresCercanos(latitud, longitud);
        assertFalse(cercanos.isEmpty(), "No hay inventario ANH aprobado; NO se usan datos artificiales");
        var idsDirectos = jdbc.queryForList("""
                SELECT id FROM surtidor
                ORDER BY ST_Distance(ubicacion,
                  ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography), id
                """, Long.class, longitud, latitud);
        // Empates improbables: ordenar por id también en CatalogosJdbc.
        assertEquals(idsDirectos, cercanos.stream().map(s -> s.id()).toList());
        var servicio = new ServicioBusquedaSurtidores(catalogos);
        for (var surtidor : cercanos) {
            assertFalse(surtidor.aliases().isEmpty(), "Falta nombre canónico normalizado");
            assertTrue(surtidor.aliases().contains(ClaveAlias.normalizar(surtidor.nombre())));
            assertEquals(surtidor.id(), servicio.buscarPorNombreOAlias(surtidor.nombre())
                    .orElseThrow());
        }
        // Alias coloquiales múltiples NO se testean con datos inventados: quedan pendientes
        // hasta que una fuente autorizada aporte alias verificables.
    }
}
