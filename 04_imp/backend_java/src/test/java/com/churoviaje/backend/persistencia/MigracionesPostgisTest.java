package com.churoviaje.backend.persistencia;

import com.churoviaje.backend.config.LectorCoberturaGeoJson;
import com.churoviaje.backend.config.PruebaIntegracionPostgis;
import com.churoviaje.backend.dominio.catalogo.ClaveAlias;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;

class MigracionesPostgisTest extends PruebaIntegracionPostgis {
    @Autowired PostgreSQLContainer<?> postgres;

    // CP-23: 20 denominaciones aceptadas tras retirar pimentel; una fila por establecimiento real.
    private static final Map<String, String> SURTIDORES_POR_DENOMINACION = Map.ofEntries(
        Map.entry("las vegas", "Las Vegas"),
        Map.entry("don daniel", "Don Daniel"),
        Map.entry("tacuarandi", "Tacuarandi"),
        Map.entry("campesino", "Campesino"),
        Map.entry("ypfb morros blancos", "YPFB Parada Chaco"),
        Map.entry("agrupa", "Agrupa"),
        Map.entry("el portillo", "El Portillo"),
        Map.entry("moto mendez", "Moto Mendez"),
        Map.entry("ex terminal", "Ex Terminal"),
        Map.entry("sointa", "Sointa"),
        Map.entry("panamericano", "Panamericano"),
        Map.entry("san jorge", "San Jorge"),
        Map.entry("san geronimo", "Panamericano"),
        Map.entry("el molle", "El Molle"),
        Map.entry("surtidor tarija", "Surtidor Tarija"),
        Map.entry("san martin", "Surtidor Tarija"),
        Map.entry("villanueva", "Panamericano"),
        Map.entry("ypfb parada chaco", "YPFB Parada Chaco"),
        Map.entry("domingo savio", "Surtidor Tarija"),
        Map.entry("coliseo universitario", "Panamericano")
    );

    @Test void cp22Cp23DosBasesVaciasReproduciblesConCatalogosAliasYCobertura() throws Exception {
        String sufijo = UUID.randomUUID().toString().replace("-", "");
        String a = crearBase("t089_a_" + sufijo);
        String b = crearBase("t089_b_" + sufijo);
        // Ambas bases se migran antes de comprobar catalogos, alias y cobertura.
        // assertAll ejecuta tambien la segunda aunque falle la primera.
        assertAll("V1 y V2 desde dos bases vacias",
                () -> migrar(a), () -> migrar(b));
        assertAll("CP-22 / CP-23: ambas bases y su reproducibilidad",
                () -> verificar(a),
                () -> verificar(b),
                () -> assertEquals(firma(a), firma(b), "Los catalogos deben ser reproducibles"));
    }

    private Connection conectar(String base) throws SQLException {
        return DriverManager.getConnection(base, postgres.getUsername(), postgres.getPassword());
    }

    private String crearBase(String nombre) throws SQLException {
        try (var conexion = conectar(postgres.getJdbcUrl()); var sentencia = conexion.createStatement()) {
            // nombre solo contiene un prefijo constante y un UUID hexadecimal generado aqui.
            sentencia.execute("CREATE DATABASE " + nombre + " TEMPLATE template0");
        }
        return "jdbc:postgresql://" + postgres.getHost() + ":"
                + postgres.getMappedPort(5432) + "/" + nombre;
    }

    private void migrar(String base) throws SQLException {
        assertEquals(0, contar(base, "SELECT count(*) FROM pg_extension WHERE extname='postgis'"));
        assertEquals(0, contar(base, "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'"));
        var flyway = Flyway.configure().dataSource(base, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").cleanDisabled(true).load();
        assertEquals(2, flyway.migrate().migrationsExecuted);
        flyway.validate();
        assertEquals(0, flyway.migrate().migrationsExecuted, "Reejecutar Flyway no debe duplicar datos");
    }

    private int contar(String base, String sql) throws SQLException {
        try (var conexion = conectar(base); var sentencia = conexion.createStatement();
                var resultado = sentencia.executeQuery(sql)) {
            assertTrue(resultado.next());
            return resultado.getInt(1);
        }
    }

    private void verificar(String base) {
        // Todas las comprobaciones se ejecutan para informar los fallos de cada base.
        assertAll("Verificacion de " + base,
                () -> verificarEstructura(base),
                () -> verificarCatalogos(base),
                () -> verificarAlias(base),
                () -> verificarRestricciones(base),
                () -> verificarCoberturaReal(base),
                () -> verificarAceptacionSurtidores(base));
    }

    private void verificarEstructura(String base) throws SQLException {
        assertEquals(1, contar(base, "SELECT count(*) FROM pg_extension WHERE extname='postgis'"));
        for (String tabla : List.of("tipo_evento", "tipo_combustible", "surtidor", "surtidor_alias")) {
            assertEquals(1, contar(base, "SELECT count(*) FROM information_schema.tables "
                    + "WHERE table_schema='public' AND table_name='" + tabla + "'"), tabla);
        }
        assertEquals(1, contar(base, "SELECT count(*) FROM pg_indexes WHERE schemaname='public' "
                + "AND indexname='idx_surtidor_ubicacion' AND indexdef ILIKE '%USING gist%'"));
        assertEquals(1, contar(base, "SELECT count(*) FROM pg_constraint "
                + "WHERE conrelid='surtidor_alias'::regclass AND conname='ck_surtidor_alias_normalizado'"));
        assertEquals(1, contar(base, "SELECT count(*) FROM pg_constraint "
                + "WHERE conrelid='surtidor_alias'::regclass AND conname='fk_surtidor_alias_surtidor' "
                + "AND confdeltype='c'"));
        assertEquals(0, contar(base, "SELECT count(*) FROM surtidor WHERE ST_SRID(ubicacion::geometry)<>4326 "
                + "OR ST_GeometryType(ubicacion::geometry)<>'ST_Point'"));
    }

    private void verificarCatalogos(String base) throws SQLException {
        assertAll("Catalogos base",
            () -> assertEquals(5, contar(base, "SELECT count(*) FROM tipo_evento")),
            () -> assertEquals(5, contar(base, "SELECT count(*) FROM tipo_evento WHERE color ~ '^#[0-9A-Fa-f]{6}$'")),
            () -> assertEquals(4, contar(base, "SELECT count(*) FROM tipo_evento WHERE reportable AND vigencia_minutos=60")),
            () -> assertEquals(1, contar(base, "SELECT count(*) FROM tipo_evento WHERE codigo='CONGESTION' AND NOT reportable AND vigencia_minutos=30")),
            () -> assertEquals(1, contar(base, "SELECT count(*) FROM tipo_evento WHERE codigo='ACCIDENTE' AND color='#E53935'")),
            () -> assertEquals(1, contar(base, "SELECT count(*) FROM tipo_evento WHERE codigo='CONTROL_POLICIAL' AND color='#1E88E5'")),
            () -> assertEquals(1, contar(base, "SELECT count(*) FROM tipo_evento WHERE codigo='BLOQUEO' AND color='#FB8C00'")),
            () -> assertEquals(1, contar(base, "SELECT count(*) FROM tipo_evento WHERE codigo='OTRO' AND color='#757575'")),
            () -> assertEquals(1, contar(base, "SELECT count(*) FROM tipo_evento WHERE codigo='CONGESTION' AND color='#FDD835'")),
            () -> assertEquals(5, contar(base, "SELECT count(*) FROM tipo_combustible")),
            () -> assertEquals(5, contar(base, "SELECT count(*) FROM tipo_combustible WHERE codigo IN "
                    + "('GASOLINA','DIESEL','GASOLINA_PREMIUM','DIESEL_ULS','DESCONOCIDO')"))
        );
    }

    private void verificarAlias(String base) throws SQLException {
        assertEquals(36, contar(base, "SELECT count(*) FROM surtidor_alias"), "Alias del catalogo revisado");
        assertEquals(4, contar(base, "SELECT count(*) FROM surtidor_alias a JOIN surtidor s ON s.id=a.surtidor_id "
                + "WHERE (a.alias='vegas' AND s.nombre='Las Vegas') "
                + "OR (a.alias='daniel' AND s.nombre='Don Daniel') "
                + "OR (a.alias='molle' AND s.nombre='El Molle') "
                + "OR (a.alias='campesino' AND s.nombre='Campesino')"), "Alias cortos univocos");
        assertEquals(0, contar(base, "SELECT count(*) FROM surtidor s LEFT JOIN surtidor_alias a ON a.surtidor_id=s.id WHERE a.id IS NULL"));
        assertEquals(0, contar(base, "SELECT count(*) FROM surtidor_alias WHERE alias IN ('ypfb', 'pimentel')"),
                "No asociar denominaciones ambiguas o sin respaldo suficiente");
        assertEquals(0, contar(base, "SELECT count(*) FROM (SELECT alias FROM surtidor_alias GROUP BY alias HAVING count(*)>1) duplicados"));
        try (var conexion = conectar(base); var sentencia = conexion.createStatement();
                var resultado = sentencia.executeQuery("SELECT id, nombre FROM surtidor")) {
            while (resultado.next()) {
                long id = resultado.getLong("id");
                String nombre = resultado.getString("nombre");
                try (var consulta = conexion.prepareStatement("SELECT surtidor_id FROM surtidor_alias WHERE alias=?")) {
                    consulta.setString(1, ClaveAlias.normalizar(nombre));
                    try (var alias = consulta.executeQuery()) {
                        assertTrue(alias.next(), "Falta alias oficial: " + nombre);
                        assertEquals(id, alias.getLong(1), "El nombre oficial apunta a otro surtidor: " + nombre);
                        assertFalse(alias.next(), "Nombre oficial duplicado: " + nombre);
                    }
                }
            }
        }
        try (var conexion = conectar(base); var sentencia = conexion.createStatement();
                var resultado = sentencia.executeQuery("SELECT alias FROM surtidor_alias")) {
            while (resultado.next()) {
                String alias = resultado.getString(1);
                assertEquals(alias, ClaveAlias.normalizar(alias));
            }
        }
    }

    private void verificarAceptacionSurtidores(String base) throws SQLException {
        List<String> errores = new ArrayList<>();
        try (var conexion = conectar(base);
                var consulta = conexion.prepareStatement("SELECT s.nombre FROM surtidor_alias a "
                        + "JOIN surtidor s ON s.id=a.surtidor_id WHERE a.alias=?")) {
            for (var entrada : SURTIDORES_POR_DENOMINACION.entrySet()) {
                consulta.setString(1, entrada.getKey());
                try (var resultado = consulta.executeQuery()) {
                    if (!resultado.next()) {
                        errores.add("Falta denominacion: " + entrada.getKey());
                    } else {
                        String nombre = resultado.getString(1);
                        if (!entrada.getValue().equals(nombre)) {
                            errores.add(entrada.getKey() + " apunta a " + nombre
                                    + "; corresponde a " + entrada.getValue());
                        }
                        if (resultado.next()) errores.add("Alias repetido: " + entrada.getKey());
                    }
                }
            }
        }
        assertAll("CP-23: denominaciones aceptadas sin predios duplicados",
            () -> assertEquals(20, SURTIDORES_POR_DENOMINACION.size(), "Deben comprobarse las 20 denominaciones aceptadas"),
            () -> assertEquals(new HashSet<>(SURTIDORES_POR_DENOMINACION.values()).size(),
                    contar(base, "SELECT count(*) FROM surtidor"), "Una fila por estacion identificada"),
            () -> assertTrue(errores.isEmpty(), "Correspondencias incorrectas: " + errores),
            () -> assertEquals(1, contar(base, "SELECT count(*) FROM surtidor WHERE nombre='Las Vegas'")),
            () -> assertEquals(0, contar(base, "SELECT count(*) FROM (SELECT ST_AsEWKB(ubicacion::geometry) "
                    + "FROM surtidor GROUP BY ST_AsEWKB(ubicacion::geometry) HAVING count(*)>1) repetidos"),
                    "No duplicar coordenadas para completar el catalogo")
        );
    }

    private void verificarCoberturaReal(String base) throws Exception {
        // CP-22 carga el GeoJSON ENTREGADO y las filas realmente migradas.
        // No analiza INSERT mediante regex ni depende del formato del SQL.
        var doc = new ObjectMapper().readTree(Path.of("../datos/mancha_urbana_tarija.geojson").toFile());
        var cobertura = new LectorCoberturaGeoJson().leer(doc);
        List<String> fuera = new ArrayList<>();
        int examinados = 0;
        try (var conexion = conectar(base); var sentencia = conexion.createStatement();
                var resultado = sentencia.executeQuery("SELECT nombre, ST_Y(ubicacion::geometry) AS latitud, "
                        + "ST_X(ubicacion::geometry) AS longitud FROM surtidor ORDER BY nombre")) {
            while (resultado.next()) {
                double latitud = resultado.getDouble("latitud");
                double longitud = resultado.getDouble("longitud");
                if (!cobertura.contiene(latitud, longitud)) {
                    fuera.add(resultado.getString("nombre") + " (" + latitud + ", " + longitud + ")");
                }
                examinados++;
            }
        }
        assertAll("CP-22: cobertura del archivo entregado",
            () -> assertTrue(cobertura.contiene(-21.5355, -64.7296), "Centro del ejemplo del contrato"),
            () -> assertFalse(cobertura.contiene(-21.418100, -64.749400), "San Lorenzo debe quedar fuera"),
            () -> assertTrue(fuera.isEmpty(), "Surtidores fuera de la cobertura: " + fuera)
        );
        assertEquals(14, examinados, "CP-22 debe comprobar todos los surtidores reales de V2");
        verificarMargenesSurtidores(base, doc.path("features").get(0).path("geometry").toString());
    }

    private void verificarMargenesSurtidores(String base, String geometria) throws SQLException {
        // Umbrales de regresion conservadores, inferiores a los margenes medidos
        // el 2026-10-29. No son radios de servicio ni precisiones del levantamiento.
        Map<String, Double> minimos = Map.of(
                "El Molle", 300.0, "Moto Mendez", 340.0, "El Portillo", 360.0);
        var examinados = new HashSet<String>();
        try (var conexion = conectar(base);
                var consulta = conexion.prepareStatement("""
                SELECT nombre, ST_Distance(ubicacion,
                    ST_Boundary(ST_SetSRID(ST_GeomFromGeoJSON(?),4326))::geography) AS margen
                FROM surtidor WHERE nombre IN ('El Molle','Moto Mendez','El Portillo')
                """)) {
            consulta.setString(1, geometria);
            try (var resultado = consulta.executeQuery()) {
                while (resultado.next()) {
                    String nombre = resultado.getString("nombre");
                    double margen = resultado.getDouble("margen");
                    assertTrue(margen >= minimos.get(nombre),
                            nombre + ": margen al borde reducido a " + margen + " m");
                    examinados.add(nombre);
                }
            }
        }
        assertEquals(minimos.keySet(), examinados, "Deben protegerse los tres surtidores proximos al borde");
    }

    private void comprobarRechazo(String base, String sql, String estadoSql) throws SQLException {
        try (var conexion = conectar(base); var sentencia = conexion.createStatement()) {
            conexion.setAutoCommit(false);
            try {
                SQLException rechazo = assertThrows(SQLException.class, () -> sentencia.executeUpdate(sql));
                assertEquals(estadoSql, rechazo.getSQLState());
            } finally {
                conexion.rollback();
            }
        }
    }

    private void verificarRestricciones(String base) throws SQLException {
        assertAll("Restricciones efectivas, no solo nombres de constraints",
            () -> comprobarRechazo(base, "UPDATE tipo_evento SET color=NULL WHERE codigo='ACCIDENTE'", "23502"),
            () -> comprobarRechazo(base, "UPDATE tipo_evento SET color='rojo' WHERE codigo='ACCIDENTE'", "23514"),
            () -> comprobarRechazo(base, "INSERT INTO surtidor_alias(surtidor_id,alias) SELECT min(id),'Alias Invalido' FROM surtidor", "23514"),
            () -> comprobarRechazo(base, "INSERT INTO surtidor_alias(surtidor_id,alias) SELECT min(id),'las vegas' FROM surtidor", "23505"),
            () -> comprobarRechazo(base, "INSERT INTO surtidor_alias(surtidor_id,alias) VALUES (-1,'huerfano de prueba')", "23503"),
            () -> verificarBorradoEnCascada(base)
        );
    }

    private void verificarBorradoEnCascada(String base) throws SQLException {
        try (var conexion = conectar(base); var sentencia = conexion.createStatement()) {
            conexion.setAutoCommit(false);
            try {
                long id;
                try (var insertado = sentencia.executeQuery("INSERT INTO surtidor(nombre,ubicacion) VALUES "
                        + "('prueba transaccional',ST_SetSRID(ST_MakePoint(-64.73,-21.53),4326)::geography) RETURNING id")) {
                    assertTrue(insertado.next());
                    id = insertado.getLong(1);
                }
                sentencia.executeUpdate("INSERT INTO surtidor_alias(surtidor_id,alias) VALUES (" + id + ",'alias transaccional')");
                sentencia.executeUpdate("DELETE FROM surtidor WHERE id=" + id);
                try (var resultado = sentencia.executeQuery("SELECT count(*) FROM surtidor_alias WHERE surtidor_id=" + id)) {
                    assertTrue(resultado.next());
                    assertEquals(0, resultado.getInt(1));
                }
            } finally {
                conexion.rollback();
            }
        }
    }

    private String firma(String base) throws SQLException {
        try (var conexion = conectar(base); var sentencia = conexion.createStatement();
                var resultado = sentencia.executeQuery("""
                SELECT jsonb_build_array(
                  (SELECT jsonb_agg(to_jsonb(t) ORDER BY codigo) FROM tipo_evento t),
                  (SELECT jsonb_agg(to_jsonb(t) ORDER BY codigo) FROM tipo_combustible t),
                  (SELECT jsonb_agg(jsonb_build_array(nombre,ST_AsEWKT(ubicacion::geometry)) ORDER BY nombre) FROM surtidor),
                  (SELECT jsonb_agg(jsonb_build_array(s.nombre,a.alias) ORDER BY s.nombre,a.alias)
                   FROM surtidor_alias a JOIN surtidor s ON s.id=a.surtidor_id)
                )::text
                """)) {
            assertTrue(resultado.next());
            return resultado.getString(1);
        }
    }
}
