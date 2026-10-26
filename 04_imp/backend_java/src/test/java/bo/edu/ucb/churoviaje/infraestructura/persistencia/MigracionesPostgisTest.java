package bo.edu.ucb.churoviaje.infraestructura.persistencia;

import java.sql.DriverManager;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class MigracionesPostgisTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.6-alpine")
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("base_del_contenedor")
            .withUsername("churoviaje_pruebas")
            .withPassword("temporal_no_real");

    @Test
    void cp23_baseVaciaCreaExtensionYCatalogosCompletos() throws Exception {
        // CP-23 · CU-01/CU-05/CU-06 · catálogo oficial y migraciones desde base vacía.
        String primeraBase = crearBaseVacia("cp23_primera");
        String segundaBase = crearBaseVacia("cp23_segunda");
        assertEquals(0, contar(primeraBase, "SELECT COUNT(*) FROM pg_extension WHERE extname = 'postgis'"));
        assertEquals(0, contar(segundaBase, "SELECT COUNT(*) FROM pg_extension WHERE extname = 'postgis'"));
        aplicarMigraciones(primeraBase);
        aplicarMigraciones(segundaBase);
        verificarCatalogos(primeraBase);
        verificarCatalogos(segundaBase);
        assertEquals(firma(primeraBase), firma(segundaBase));
    }

    private String crearBaseVacia(String nombre) throws Exception {
        try (var conexion = DriverManager.getConnection(postgres.getJdbcUrl(),
                postgres.getUsername(), postgres.getPassword());
             var sentencia = conexion.createStatement()) {
            sentencia.execute("CREATE DATABASE " + nombre + " TEMPLATE template0");
        }
        return postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + nombre);
    }

    private void aplicarMigraciones(String url) {
        Flyway flyway = Flyway.configure()
                .dataSource(url, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .load();
        assertTrue(flyway.migrate().migrationsExecuted >= 2);
    }

    private int contar(String url, String sql) throws Exception {
        try (var conexion = DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
             var sentencia = conexion.createStatement();
             var filas = sentencia.executeQuery(sql)) {
            filas.next();
            return filas.getInt(1);
        }
    }

    private void verificarCatalogos(String url) throws Exception {
        assertEquals(1, contar(url, "SELECT COUNT(*) FROM pg_extension WHERE extname = 'postgis'"));
        for (String tabla : List.of("tipo_evento", "tipo_combustible", "surtidor", "surtidor_alias")) {
            assertEquals(1, contar(url, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = '" + tabla + "'"));
        }
        assertEquals(5, contar(url, "SELECT COUNT(*) FROM tipo_evento"));
        assertEquals(4, contar(url, "SELECT COUNT(*) FROM tipo_evento WHERE reportable AND vigencia_minutos = 60"));
        assertEquals(1, contar(url, "SELECT COUNT(*) FROM tipo_evento WHERE codigo = 'CONGESTION' AND NOT reportable AND vigencia_minutos = 30"));
        assertEquals(5, contar(url, "SELECT COUNT(*) FROM tipo_combustible"));
        assertEquals(5, contar(url, "SELECT COUNT(*) FROM tipo_evento WHERE color IS NULL"));
        for (String codigo : List.of("GASOLINA", "DIESEL", "GASOLINA_PREMIUM", "DIESEL_ULS", "DESCONOCIDO")) {
            assertEquals(1, contar(url, "SELECT COUNT(*) FROM tipo_combustible WHERE codigo = '" + codigo + "'"));
        }
        assertTrue(contar(url, "SELECT COUNT(*) FROM surtidor") > 0);
        assertEquals(0, contar(url, "SELECT COUNT(*) FROM surtidor s LEFT JOIN surtidor_alias a ON a.surtidor_id = s.id WHERE a.id IS NULL"));
        assertEquals(0, contar(url, "SELECT COUNT(*) FROM surtidor_alias a LEFT JOIN surtidor s ON s.id = a.surtidor_id WHERE s.id IS NULL"));
        assertEquals(1, contar(url, "SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_name = 'surtidor_alias' AND constraint_type = 'FOREIGN KEY'"));
        assertTrue(contar(url, "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'idx_surtidor_ubicacion'") == 1);
        assertEquals(0, contar(url, "SELECT COUNT(*) FROM surtidor_alias WHERE alias = 'ypfb'"));
        try (var conexion = DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
             var sentencia = conexion.createStatement();
             var filas = sentencia.executeQuery("SELECT ST_Distance(ST_SetSRID(ST_MakePoint(-64.734297, -21.533912),4326)::geography, ST_SetSRID(ST_MakePoint(-64.734297, -21.533912),4326)::geography)")) {
            assertTrue(filas.next());
            assertEquals(0.0, filas.getDouble(1), 0.001);
        }
        // CP-23 requiere surtidores reales de ANH: si falta V2 por falta de aprobación,
        // esta prueba DEBE fallar hasta completar el flujo de revisión. No se simulan INSERTs.
    }

    private String firma(String url) throws Exception {
        try (var conexion = DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
             var sentencia = conexion.createStatement();
             var filas = sentencia.executeQuery("""
                     SELECT (SELECT string_agg(codigo || ':' || nombre || ':' || COALESCE(color, '[PENDIENTE_P06]') || ':' || vigencia_minutos || ':' || reportable, ';' ORDER BY codigo) FROM tipo_evento)
                     || '|' || (SELECT string_agg(codigo || ':' || nombre, ';' ORDER BY codigo) FROM tipo_combustible)
                     || '|' || (SELECT string_agg(nombre || ':' || ST_AsText(ubicacion::geometry), ';' ORDER BY nombre) FROM surtidor)
                     || '|' || (SELECT string_agg(s.nombre || ':' || a.alias, ';' ORDER BY s.nombre, a.alias) FROM surtidor_alias a JOIN surtidor s ON s.id = a.surtidor_id)
                     """)) {
            assertTrue(filas.next());
            return filas.getString(1);
        }
    }
}
