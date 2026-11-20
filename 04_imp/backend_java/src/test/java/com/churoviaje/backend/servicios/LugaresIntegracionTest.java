package com.churoviaje.backend.servicios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.churoviaje.backend.config.PruebaIntegracionPostgis;
import com.churoviaje.backend.seguridad.JwtServicio;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** D-47 (T-123): carga de lugares (V10) y {@code GET /lugares} con la base real y la cobertura de Tarija. */
class LugaresIntegracionTest extends PruebaIntegracionPostgis {

    private static final String PLAZA_LAT = "-21.5355";
    private static final String PLAZA_LON = "-64.7296";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtServicio jwtServicio;
    @Autowired ObjectMapper json;
    @Autowired LugarServicio servicio;

    @TempDir Path carpeta;

    private String jwt;

    @BeforeEach
    void preparar() throws Exception {
        jdbc.update("delete from sesion");
        jdbc.update("delete from voto");
        jdbc.update("delete from evento_vial");
        jdbc.update("delete from estado_combustible");
        jdbc.update("delete from conductor");
        jdbc.update("delete from lugar");
        jdbc.update("delete from lugar_extracto");
        long conductor = jdbc.queryForObject(
                "insert into conductor (google_id, fecha_alta) values ('google-lugares', now()) returning id", Long.class);
        jwt = "Bearer " + jwtServicio.emitir(conductor, Instant.now());
        servicio.cargar(archivo("2026-11-19", lugaresDePrueba()));
    }

    private static Map<String, Object> lugar(String clave, String nombre, String categoria, double lat, double lon) {
        return Map.of("clave", clave, "nombre", nombre, "categoria", categoria, "latitud", lat, "longitud", lon);
    }

    private static List<Map<String, Object>> lugaresDePrueba() {
        return new ArrayList<>(List.of(
                lugar("calle:w1", "Calle Colón", "CALLE", -21.5340, -64.7310),
                lugar("calle:w2", "Avenida Las Américas", "CALLE", -21.5290, -64.7300),
                lugar("w3", "Hospital Regional San Juan de Dios", "SALUD", -21.5290, -64.7350),
                lugar("n4", "Capilla San Juan de Dios", "RELIGION", -21.5420, -64.7230),
                lugar("n5", "Mercado Campesino", "MERCADO", -21.5250, -64.7330),
                lugar("n6", "Plaza Luis de Fuentes", "PLAZA", -21.5355, -64.7296),
                lugar("n7", "Juan XXIII", "EDUCACION", -21.5200, -64.7400)));
    }

    private Path archivo(String fecha, List<Map<String, Object>> lugares) throws Exception {
        Path ruta = carpeta.resolve("lugares-" + fecha + "-" + lugares.size() + ".json");
        Files.writeString(ruta, json.writeValueAsString(Map.of("fecha_extracto", fecha, "lugares", lugares)));
        return ruta;
    }

    private ResultActions buscar(String texto, String... ubicacion) throws Exception {
        MockHttpServletRequestBuilder pedido = get("/lugares").param("texto", texto).header("Authorization", jwt);
        if (ubicacion.length == 2) {
            pedido = pedido.param("latitud", ubicacion[0]).param("longitud", ubicacion[1]);
        }
        return mvc.perform(pedido);
    }

    private JsonNode resultados(String texto, String... ubicacion) throws Exception {
        String cuerpo = buscar(texto, ubicacion).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return json.readTree(cuerpo).path("lugares");
    }

    private static List<String> nombres(JsonNode lugares) {
        List<String> nombres = new ArrayList<>();
        lugares.forEach(l -> nombres.add(l.path("nombre").asText()));
        return nombres;
    }

    @Test
    @DisplayName("CP-187: el archivo se carga una vez por extracto y reemplaza al anterior")
    void cargaPorExtracto() throws Exception {
        assertThat(jdbc.queryForObject("select count(*) from lugar", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("select nombre_busqueda from lugar where clave = 'calle:w2'", String.class))
                .isEqualTo("avenida las americas");

        LugarServicio.Carga igual = servicio.cargar(archivo("2026-11-19", List.of(
                lugar("n9", "Otro lugar", "MERCADO", -21.53, -64.73))));
        assertThat(igual.cargado()).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from lugar", Integer.class)).isEqualTo(7);

        List<Map<String, Object>> nuevos = new ArrayList<>(List.of(
                lugar("n9", "Terminal de Buses", "TRANSPORTE", -21.5390, -64.7240),
                lugar("n9", "Repetido", "TRANSPORTE", -21.5390, -64.7240),
                lugar("n10", "Fuera de Tarija", "MERCADO", -16.50, -68.15),
                lugar("n11", "Sin categoría", "NAVE", -21.53, -64.73),
                lugar("n12", "Surtidor de OSM", "SURTIDOR", -21.53, -64.73),
                lugar("n13", "¿?", "MERCADO", -21.53, -64.73)));
        LugarServicio.Carga nueva = servicio.cargar(archivo("2026-12-01", nuevos));
        assertThat(nueva.cargado()).isTrue();
        assertThat(nueva.guardados()).isEqualTo(1);
        assertThat(nueva.descartados()).isEqualTo(5);
        assertThat(jdbc.queryForList("select nombre from lugar", String.class)).containsExactly("Terminal de Buses");
        assertThat(jdbc.queryForObject("select fecha_extracto::text from lugar_extracto", String.class))
                .isEqualTo("2026-12-01");
    }

    @Test
    @DisplayName("CP-187: un archivo sin lugares válidos no borra los que había")
    void archivoInvalido() throws Exception {
        Path vacio = archivo("2026-12-02", List.of(lugar("n1", "Fuera", "MERCADO", -16.5, -68.15)));
        assertThatThrownBy(() -> servicio.cargar(vacio)).isInstanceOf(IllegalStateException.class);
        Path sinFecha = carpeta.resolve("sin-fecha.json");
        Files.writeString(sinFecha, "{\"lugares\":[]}");
        assertThatThrownBy(() -> servicio.cargar(sinFecha)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from lugar", Integer.class)).isEqualTo(7);
    }

    @Test
    @DisplayName("CP-188: encuentra sin tildes, por partes de palabras y con abreviaturas")
    void encuentra() throws Exception {
        assertThat(nombres(resultados("colon"))).containsExactly("Calle Colón");
        assertThat(nombres(resultados("Av. las amer"))).containsExactly("Avenida Las Américas");
        assertThat(nombres(resultados("san juan"))).containsExactlyInAnyOrder(
                "Hospital Regional San Juan de Dios", "Capilla San Juan de Dios");
        assertThat(nombres(resultados("hosp dios"))).containsExactly("Hospital Regional San Juan de Dios");
        assertThat(resultados("zzzz")).isEmpty();
    }

    @Test
    @DisplayName("CP-188: también encuentra surtidores del catálogo, por nombre y por alias")
    void encuentraSurtidores() throws Exception {
        JsonNode vegas = resultados("las vegas");
        assertThat(nombres(vegas)).contains("Las Vegas");
        JsonNode primero = vegas.get(0);
        assertThat(primero.path("categoria").asText()).isEqualTo("SURTIDOR");
        String alias = jdbc.queryForObject("select alias from surtidor_alias limit 1", String.class);
        String surtidor = jdbc.queryForObject("""
                select s.nombre from surtidor s join surtidor_alias a on a.surtidor_id = s.id where a.alias = ?""",
                String.class, alias);
        assertThat(nombres(resultados(alias))).contains(surtidor);
    }

    @Test
    @DisplayName("CP-188: con la ubicación, primero lo que empieza con lo escrito y luego lo más cercano")
    void ordenaPorCercania() throws Exception {
        JsonNode sanJuan = resultados("san juan", "-21.5425", "-64.7225");
        assertThat(nombres(sanJuan)).containsExactly("Capilla San Juan de Dios", "Hospital Regional San Juan de Dios");
        assertThat(sanJuan.get(0).path("distanciaM").asLong()).isLessThan(100);
        assertThat(sanJuan.get(1).path("distanciaM").asLong()).isGreaterThan(sanJuan.get(0).path("distanciaM").asLong());

        // "Juan XXIII" empieza con lo escrito: va primero aunque la capilla esté más cerca.
        assertThat(nombres(resultados("juan", "-21.5425", "-64.7225"))).containsExactly(
                "Juan XXIII", "Capilla San Juan de Dios", "Hospital Regional San Juan de Dios");
    }

    @Test
    @DisplayName("CP-188: cada resultado trae solo nombre, categoría, ubicación y, si hay ubicación, la distancia")
    void campos() throws Exception {
        JsonNode sin = resultados("mercado").get(0);
        List<String> campos = new ArrayList<>();
        sin.fieldNames().forEachRemaining(campos::add);
        assertThat(campos).containsExactlyInAnyOrder("nombre", "categoria", "ubicacion");
        JsonNode con = resultados("mercado", PLAZA_LAT, PLAZA_LON).get(0);
        List<String> camposConUbicacion = new ArrayList<>();
        con.fieldNames().forEachRemaining(camposConUbicacion::add);
        assertThat(camposConUbicacion).containsExactlyInAnyOrder("nombre", "categoria", "ubicacion", "distanciaM");
        assertThat(con.path("ubicacion").path("latitud").asDouble()).isEqualTo(-21.525);
    }

    @Test
    @DisplayName("CP-189: pide sesión, al menos 2 letras y la ubicación completa")
    void rechazos() throws Exception {
        mvc.perform(get("/lugares").param("texto", "colon")).andExpect(status().isUnauthorized());
        buscar("c").andExpect(status().isBadRequest());
        buscar(" ¿? ").andExpect(status().isBadRequest());
        buscar("a".repeat(101)).andExpect(status().isBadRequest());
        mvc.perform(get("/lugares").header("Authorization", jwt)).andExpect(status().isBadRequest());
        mvc.perform(get("/lugares").param("texto", "colon").param("latitud", PLAZA_LAT).header("Authorization", jwt))
                .andExpect(status().isBadRequest());
        buscar("colon", "95", PLAZA_LON).andExpect(status().isBadRequest());
        String cuerpo = buscar("c").andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(json.readTree(cuerpo).path("codigo").asText()).isEqualTo("PETICION_INVALIDA");
    }
}
