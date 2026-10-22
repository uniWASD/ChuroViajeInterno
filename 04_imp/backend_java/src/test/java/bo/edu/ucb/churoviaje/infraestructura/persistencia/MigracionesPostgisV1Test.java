package bo.edu.ucb.churoviaje.infraestructura.persistencia;

import java.sql.DriverManager;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
class MigracionesPostgisV1Test {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.6-alpine")
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("base_del_contenedor")
            .withUsername("churoviaje_pruebas")
            .withPassword("temporal_no_real");

    @Test
    void migracionV1FuncionaEnDosBasesLimpiasYEsReproducible() throws Exception {
        String primera = crearBaseVacia("t089_v1_primera");
        String segunda = crearBaseVacia("t089_v1_segunda");

        for (String url : List.of(primera, segunda)) {
            assertEquals(0, contar(url, "SELECT COUNT(*) FROM pg_extension WHERE extname = 'postgis'"));

            Flyway flyway = Flyway.configure()
                    .dataSource(url, postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("1"))
                    .cleanDisabled(true)
                    .load();

            assertEquals(1, flyway.migrate().migrationsExecuted);
            flyway.validate();
            assertEquals(0, flyway.migrate().migrationsExecuted);
            comprobarV1(url);
        }

        assertEquals(firma(primera), firma(segunda));
    }

    private String crearBaseVacia(String nombre) throws Exception {
        try (var conexion = DriverManager.getConnection(postgres.getJdbcUrl(),
                postgres.getUsername(), postgres.getPassword());
             var sentencia = conexion.createStatement()) {
            sentencia.execute("CREATE DATABASE " + nombre + " TEMPLATE template0");
        }
        return postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + nombre);
    }

    private void comprobarV1(String url) throws Exception {
        assertEquals(1, contar(url, "SELECT COUNT(*) FROM pg_extension WHERE extname = 'postgis'"));
        assertEquals(1, contar(url, "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success"));

        for (String tabla : List.of("tipo_evento", "tipo_combustible", "surtidor", "surtidor_alias")) {
            assertEquals(1, contar(url, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = '" + tabla + "'"));
        }

        assertEquals(5, contar(url, "SELECT COUNT(*) FROM tipo_evento"));
        assertEquals(4, contar(url, "SELECT COUNT(*) FROM tipo_evento WHERE reportable AND vigencia_minutos = 60"));
        assertEquals(1, contar(url, "SELECT COUNT(*) FROM tipo_evento WHERE codigo = 'CONGESTION' AND NOT reportable AND vigencia_minutos = 30"));
        assertEquals(5, contar(url, "SELECT COUNT(*) FROM tipo_combustible"));

        for (String codigo : List.of("GASOLINA", "DIESEL", "GASOLINA_PREMIUM", "DIESEL_ULS", "DESCONOCIDO")) {
            assertEquals(1, contar(url, "SELECT COUNT(*) FROM tipo_combustible WHERE codigo = '" + codigo + "'"));
        }

        assertEquals(1, contar(url, "SELECT COUNT(*) FROM pg_constraint WHERE conname = 'fk_surtidor_alias_surtidor' AND contype = 'f'"));
        assertEquals(1, contar(url, "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'surtidor' AND indexname = 'idx_surtidor_ubicacion'"));
        assertEquals(1, contar(url, "SELECT CASE WHEN ST_DWithin(ST_SetSRID(ST_MakePoint(-64.734297, -21.533912), 4326)::geography, ST_SetSRID(ST_MakePoint(-64.734297, -21.533912), 4326)::geography, 1) THEN 1 ELSE 0 END"));
        assertEquals(0, contar(url, "SELECT COUNT(*) FROM surtidor"));
        assertEquals(0, contar(url, "SELECT COUNT(*) FROM surtidor_alias"));
    }

    private int contar(String url, String sql) throws Exception {
        try (var conexion = DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
             var sentencia = conexion.createStatement();
             var filas = sentencia.executeQuery(sql)) {
            filas.next();
            return filas.getInt(1);
        }
    }

    private String firma(String url) throws Exception {
        try (var conexion = DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
             var sentencia = conexion.createStatement();
             var filas = sentencia.executeQuery("""
                    SELECT
                      (SELECT string_agg(codigo || ':' || nombre || ':' || color || ':' || vigencia_minutos || ':' || reportable, ';' ORDER BY codigo) FROM tipo_evento)
                      || '|'
                      || (SELECT string_agg(codigo || ':' || nombre, ';' ORDER BY codigo) FROM tipo_combustible)
                    """)) {
            filas.next();
            return filas.getString(1);
        }
    }
}
