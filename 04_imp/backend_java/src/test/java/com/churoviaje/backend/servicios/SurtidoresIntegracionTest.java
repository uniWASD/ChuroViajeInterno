package com.churoviaje.backend.servicios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.churoviaje.backend.config.ConfiguracionRelojDePrueba;
import com.churoviaje.backend.config.PruebaIntegracionPostgis;
import com.churoviaje.backend.config.RelojDePrueba;
import com.churoviaje.backend.seguridad.JwtServicio;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** HU-07 (#69): {@code GET /surtidores} con la base real y el catálogo de surtidores de V2. */
@Import(ConfiguracionRelojDePrueba.class)
class SurtidoresIntegracionTest extends PruebaIntegracionPostgis {

    /** 17:00 en Tarija. */
    private static final Instant AHORA = Instant.parse("2026-11-14T21:00:00Z");
    private static final String PLAZA_LAT = "-21.5355";
    private static final String PLAZA_LON = "-64.7296";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtServicio jwtServicio;
    @Autowired RelojDePrueba reloj;
    @Autowired ObjectMapper json;

    private String jwt;

    @BeforeEach
    void preparar() {
        jdbc.update("delete from estado_combustible");
        jdbc.update("delete from voto");
        jdbc.update("delete from evento_vial");
        jdbc.update("delete from sesion");
        jdbc.update("delete from conductor");
        reloj.fijar(AHORA);
        long conductor = jdbc.queryForObject(
                "insert into conductor (google_id, fecha_alta) values ('google-surtidores', now()) returning id", Long.class);
        jwt = "Bearer " + jwtServicio.emitir(conductor, AHORA);
    }

    /** Hora de Tarija del 14/11/2026, por ejemplo "10:30". */
    private static Timestamp a(String hora) {
        return Timestamp.from(java.time.OffsetDateTime.parse("2026-11-14T" + hora + ":00-04:00").toInstant());
    }

    private void dato(String surtidor, String tipo, String origen, boolean disponible, String fila, String hora) {
        jdbc.update("""
                insert into estado_combustible (surtidor_id, tipo_codigo, origen, disponible, fila, fecha_dato, actualizado_en)
                select id, ?, ?, ?, ?, ?, ? from surtidor where nombre = ?""",
                tipo, origen, disponible, fila, a(hora), a(hora), surtidor);
    }

    private void datoAnh(String surtidor, String tipo, double litros, String estadoAnh, String fecha, String leido) {
        jdbc.update("""
                insert into estado_combustible (surtidor_id, tipo_codigo, origen, disponible, fecha_dato, litros,
                                                estado_anh, consultado_en, actualizado_en)
                select id, ?, 'ANH', ?, ?, ?, ?, ?, ? from surtidor where nombre = ?""",
                tipo, litros > 0, a(fecha), litros, estadoAnh, a(leido), a(leido), surtidor);
    }

    private ResultActions pedir(String lat, String lon) throws Exception {
        return mvc.perform(get("/surtidores").param("latitud", lat).param("longitud", lon).header("Authorization", jwt));
    }

    private JsonNode consultar() throws Exception {
        String cuerpo = pedir(PLAZA_LAT, PLAZA_LON).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).path("surtidores");
    }

    private static JsonNode surtidor(JsonNode lista, String nombre) {
        for (JsonNode s : lista) {
            if (s.path("nombre").asText().equals(nombre)) {
                return s;
            }
        }
        throw new AssertionError("No vino " + nombre);
    }

    @Test
    @DisplayName("CP-102: Gasolina «agotado» por WhatsApp a las 10:00 y «disponible» por Conductores a las 10:30 muestra disponible, Conductores, 10:30")
    void cp102GanaElMasReciente() throws Exception {
        reloj.fijar(Instant.parse("2026-11-14T15:00:00Z")); // 11:00 en Tarija
        dato("Las Vegas", "GASOLINA", "WHATSAPP", false, "LARGA", "10:00");
        dato("Las Vegas", "GASOLINA", "CONDUCTORES", true, null, "10:30");

        JsonNode combustibles = surtidor(consultar(), "Las Vegas").path("combustibles");
        assertThat(combustibles).hasSize(1);
        JsonNode g = combustibles.get(0);
        assertThat(g.path("tipo").asText()).isEqualTo("GASOLINA");
        assertThat(g.path("nombre").asText()).isEqualTo("Gasolina");
        assertThat(g.path("disponible").asBoolean()).isTrue();
        assertThat(g.path("origen").asText()).isEqualTo("CONDUCTORES");
        assertThat(g.path("fecha").asText()).isEqualTo("2026-11-14T10:30:00-04:00");
        assertThat(g.has("fila")).as("la fila solo si se conoce (RN-05.4)").isFalse();
        assertThat(g.has("litros")).isFalse();
    }

    @Test
    @DisplayName("CP-103: un surtidor cuyo último dato es de hace 7 horas sale como «sin información reciente»")
    void cp103DatoVencido() throws Exception {
        dato("Don Daniel", "DIESEL", "WHATSAPP", true, "CORTA", "10:00"); // 7 horas antes de las 17:00
        dato("Agrupa", "DIESEL", "WHATSAPP", true, null, "11:01");        // 5 h 59 min: todavía vale

        JsonNode lista = consultar();
        JsonNode daniel = surtidor(lista, "Don Daniel");
        assertThat(daniel.path("informacionReciente").asBoolean()).isFalse();
        assertThat(daniel.path("combustibles")).isEmpty();
        assertThat(surtidor(lista, "Agrupa").path("informacionReciente").asBoolean()).isTrue();
        assertThat(surtidor(lista, "Tacuarandi").path("informacionReciente").asBoolean()).as("sin ningún dato").isFalse();
    }

    @Test
    @DisplayName("RN-05.5: un dato vencido no tapa a uno vigente más viejo de otro origen")
    void vencidoNoTapa() throws Exception {
        dato("Sointa", "GASOLINA", "CONDUCTORES", true, null, "14:00");
        datoAnh("Sointa", "GASOLINA", 0, "BAJO", "15:00", "10:30"); // fecha más nueva, pero leído hace 6,5 h
        JsonNode g = surtidor(consultar(), "Sointa").path("combustibles").get(0);
        assertThat(g.path("origen").asText()).isEqualTo("CONDUCTORES");
    }

    @Test
    @DisplayName("CP-104: desde la Plaza el primer surtidor es el más cercano, y la lista va de más cerca a más lejos")
    void cp104OrdenPorCercania() throws Exception {
        JsonNode lista = consultar();
        long total = jdbc.queryForObject("select count(*) from surtidor", Long.class);
        assertThat(lista).hasSize((int) total);

        // Distancia calculada aparte (haversine) con las coordenadas de V2.
        List<Map<String, Object>> filas = jdbc.queryForList(
                "select nombre, ST_Y(ubicacion::geometry) as lat, ST_X(ubicacion::geometry) as lon from surtidor");
        String masCercano = null;
        double menor = Double.MAX_VALUE;
        for (Map<String, Object> f : filas) {
            double d = haversine(Double.parseDouble(PLAZA_LAT), Double.parseDouble(PLAZA_LON),
                    ((Number) f.get("lat")).doubleValue(), ((Number) f.get("lon")).doubleValue());
            if (d < menor) {
                menor = d;
                masCercano = (String) f.get("nombre");
            }
        }
        assertThat(lista.get(0).path("nombre").asText()).isEqualTo(masCercano);
        assertThat(lista.get(0).path("distanciaM").asDouble()).isCloseTo(menor, org.assertj.core.data.Offset.offset(5.0));
        List<Long> distancias = new ArrayList<>();
        lista.forEach(s -> distancias.add(s.path("distanciaM").asLong()));
        assertThat(distancias).isSorted();
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double r = 6_371_008.8;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * r * Math.asin(Math.sqrt(h));
    }

    @Test
    @DisplayName("RN-05.4, RN-09.17: un dato de ANH trae los litros, el estado de ANH y cuándo se leyó, y vale 6 horas desde la lectura")
    void datoDeAnh() throws Exception {
        datoAnh("Ex Terminal", "DIESEL", 1250.5, "ALTO", "08:00", "16:30"); // updated_at viejo, lectura reciente
        dato("Ex Terminal", "DIESEL", "WHATSAPP", false, null, "07:30");     // vencido
        JsonNode d = surtidor(consultar(), "Ex Terminal").path("combustibles").get(0);
        assertThat(d.path("origen").asText()).isEqualTo("ANH");
        assertThat(d.path("disponible").asBoolean()).isTrue();
        assertThat(d.path("litros").asDouble()).isEqualTo(1250.5);
        assertThat(d.path("estadoAnh").asText()).isEqualTo("ALTO");
        assertThat(d.path("fecha").asText()).isEqualTo("2026-11-14T08:00:00-04:00");
        assertThat(d.path("consultadoEn").asText()).isEqualTo("2026-11-14T16:30:00-04:00");
    }

    @Test
    @DisplayName("RN-05.6: un dato de WhatsApp sin tipo sale como Desconocido; cada tipo sale por separado")
    void tipoDesconocido() throws Exception {
        dato("El Portillo", "DESCONOCIDO", "WHATSAPP", false, "SIN_FILA", "16:00");
        dato("El Portillo", "GASOLINA", "WHATSAPP", true, null, "15:00");
        JsonNode c = surtidor(consultar(), "El Portillo").path("combustibles");
        assertThat(c).extracting(n -> n.path("tipo").asText()).containsExactly("DESCONOCIDO", "GASOLINA");
        assertThat(c.get(0).path("nombre").asText()).isEqualTo("Desconocido");
        assertThat(c.get(0).path("fila").asText()).isEqualTo("SIN_FILA");
    }

    @Test
    @DisplayName("RN-05.15: con la misma fecha, la confirmación de un conductor no tapa a WhatsApp")
    void empateNoGanaConductores() throws Exception {
        dato("Moto Mendez", "GASOLINA", "WHATSAPP", false, null, "16:00");
        dato("Moto Mendez", "GASOLINA", "CONDUCTORES", true, null, "16:00");
        assertThat(surtidor(consultar(), "Moto Mendez").path("combustibles").get(0).path("origen").asText())
                .isEqualTo("WHATSAPP");
    }

    @Test
    @DisplayName("RN-05.7: la respuesta no tiene nada de quién aportó el dato")
    void sinAutor() throws Exception {
        dato("Las Vegas", "GASOLINA", "CONDUCTORES", true, null, "16:00");
        String cuerpo = pedir(PLAZA_LAT, PLAZA_LON).andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain("conductor").doesNotContain("google").doesNotContain("reportante");
    }

    @Test
    @DisplayName("RNF-03 y RN-05.1: responde en menos de 3 segundos; sin JWT, 401; sin ubicación o fuera de rango, 400")
    void tiempoYRechazos() throws Exception {
        pedir(PLAZA_LAT, PLAZA_LON); // calienta el controlador
        long inicio = System.nanoTime();
        pedir(PLAZA_LAT, PLAZA_LON).andExpect(status().isOk());
        assertThat((System.nanoTime() - inicio) / 1_000_000).isLessThan(3000);

        mvc.perform(get("/surtidores").param("latitud", PLAZA_LAT).param("longitud", PLAZA_LON))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/surtidores").param("latitud", PLAZA_LAT).header("Authorization", jwt))
                .andExpect(status().isBadRequest());
        pedir("-91", PLAZA_LON).andExpect(status().isBadRequest());
        pedir(PLAZA_LAT, "norte").andExpect(status().isBadRequest());
    }
}
