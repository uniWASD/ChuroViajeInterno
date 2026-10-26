package bo.edu.ucb.churoviaje.infraestructura.persistencia;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.junit.jupiter.api.Assertions.*;

/** Prueba de componentes con registros TEMPORALES dentro de Testcontainers. No es V2. */
@Testcontainers
class CatalogosJdbcPostgisTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse("postgis/postgis:16-3.6-alpine")
            .asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("t089_catalogos")
        .withUsername("usuario_prueba")
        .withPassword("clave_temporal_prueba");

    @Test
    void consultaCatalogosAliasYOrdenGeografico() {
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("1.1"))
            .cleanDisabled(true).load().migrate();
        var datasource = new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var jdbc = new JdbcTemplate(datasource);
        var catalogos = new CatalogosJdbc(jdbc);
        assertEquals(5, catalogos.tiposEvento().size());
        assertEquals(5, catalogos.tiposCombustible().size());
        Long cercano = jdbc.queryForObject("""
            INSERT INTO surtidor (nombre, ubicacion)
            VALUES ('T089_PRUEBA_CERCANO',
                ST_SetSRID(ST_MakePoint(-64.7296, -21.5355), 4326)::geography)
            RETURNING id
            """, Long.class);
        Long lejano = jdbc.queryForObject("""
            INSERT INTO surtidor (nombre, ubicacion)
            VALUES ('T089_PRUEBA_LEJANO',
                ST_SetSRID(ST_MakePoint(-64.7800, -21.5800), 4326)::geography)
            RETURNING id
            """, Long.class);
        jdbc.update("INSERT INTO surtidor_alias (surtidor_id, alias) VALUES (?, ?)", cercano, "prueba unica");
        assertEquals(cercano, catalogos.buscarPorNombreOAlias("PRUEBA ÚNICA").orElseThrow());
        assertTrue(catalogos.buscarPorNombreOAlias("alias inexistente").isEmpty());
        var resultados = catalogos.surtidoresCercanos(-21.5355, -64.7296);
        assertEquals(2, resultados.size());
        assertEquals(cercano.longValue(), resultados.get(0).id());
        assertEquals(lejano.longValue(), resultados.get(1).id());
        assertTrue(resultados.get(0).aliases().contains("prueba unica"));
    }
}
