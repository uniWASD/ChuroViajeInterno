package com.churoviaje.backend.servicios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.churoviaje.backend.config.ConfiguracionRelojDePrueba;
import com.churoviaje.backend.config.PruebaIntegracionPostgis;
import com.churoviaje.backend.config.RelojDePrueba;
import com.churoviaje.backend.seguridad.JwtServicio;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * HU-12 (#72): {@code POST /surtidores/{id}/confirmaciones}. CP-111 a CP-113 y las demás reglas de la confirmación de
 * recarga (RN-05.9 a RN-05.15). El surtidor de las pruebas es «Las Vegas» del catálogo (V2); las paradas se corren
 * hacia el norte: en Tarija un grado de latitud mide unos 110 724 m.
 */
@Import(ConfiguracionRelojDePrueba.class)
class ConfirmacionRecargaIntegracionTest extends PruebaIntegracionPostgis {

    /** 17:00 en Tarija. */
    private static final Instant AHORA = Instant.parse("2026-11-22T21:00:00Z");
    private static final double SURTIDOR_LAT = -21.5234844;
    private static final double SURTIDOR_LON = -64.724772;
    private static final double METROS_POR_GRADO = 110_724;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtServicio jwtServicio;
    @Autowired RelojDePrueba reloj;
    @Autowired ObjectMapper json;

    private long conductor;
    private long surtidor;

    @BeforeEach
    void preparar() {
        jdbc.update("delete from confirmacion_recarga");
        jdbc.update("delete from estado_combustible");
        jdbc.update("delete from voto");
        jdbc.update("delete from evento_vial");
        jdbc.update("delete from sesion");
        jdbc.update("delete from conductor");
        reloj.fijar(AHORA);
        conductor = crearConductor("google-recarga", false);
        surtidor = jdbc.queryForObject("select id from surtidor where nombre = 'Las Vegas'", Long.class);
    }

    private long crearConductor(String googleId, boolean simulado) {
        return jdbc.queryForObject(
                "insert into conductor (google_id, fecha_alta, simulado) values (?, now(), ?) returning id",
                Long.class, googleId, simulado);
    }

    private String jwt(long quien) {
        return "Bearer " + jwtServicio.emitir(quien, reloj.instant());
    }

    /** Confirma desde {@code metros} al norte del surtidor. */
    private ResultActions confirmar(long quien, long id, String tipo, double metros) throws Exception {
        double lat = SURTIDOR_LAT + metros / METROS_POR_GRADO;
        String cuerpo = "{\"tipo\":\"" + tipo + "\",\"ubicacion\":{\"latitud\":" + lat + ",\"longitud\":"
                + SURTIDOR_LON + "}}";
        return mvc.perform(post("/surtidores/" + id + "/confirmaciones").header("Authorization", jwt(quien))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private JsonNode combustible(String tipo) throws Exception {
        String cuerpo = mvc.perform(get("/surtidores").param("latitud", "-21.5355").param("longitud", "-64.7296")
                        .header("Authorization", jwt(crearConductor("google-otro-" + System.nanoTime(), false))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode s : json.readTree(cuerpo).path("surtidores")) {
            if (s.path("id").asLong() == surtidor) {
                for (JsonNode c : s.path("combustibles")) {
                    if (c.path("tipo").asText().equals(tipo)) {
                        return c;
                    }
                }
                return null;
            }
        }
        throw new AssertionError("No vino el surtidor");
    }

    private void datoWhatsapp(String tipo, boolean disponible, Instant fecha) {
        jdbc.update("""
                insert into estado_combustible (surtidor_id, tipo_codigo, origen, disponible, fecha_dato, actualizado_en)
                values (?, ?, 'WHATSAPP', ?, ?, ?)
                on conflict (surtidor_id, tipo_codigo, origen) do update
                set disponible = excluded.disponible, fecha_dato = excluded.fecha_dato
                """, surtidor, tipo, disponible, Timestamp.from(fecha), Timestamp.from(fecha));
    }

    @Test
    @DisplayName("CP-111: un conductor a 20 m del surtidor confirma Diesel y el surtidor queda con Diesel disponible, origen Conductores")
    void cp111ConfirmacionAceptada() throws Exception {
        confirmar(conductor, surtidor, "DIESEL", 20).andExpect(status().isCreated())
                .andExpect(jsonPath("$.surtidorId").value(surtidor))
                .andExpect(jsonPath("$.tipo").value("DIESEL"))
                .andExpect(jsonPath("$.disponible").value(true))
                .andExpect(jsonPath("$.origen").value("CONDUCTORES"))
                .andExpect(jsonPath("$.fecha").value("2026-11-22T17:00:00-04:00"));

        JsonNode diesel = combustible("DIESEL");
        assertThat(diesel.path("disponible").asBoolean()).isTrue();
        assertThat(diesel.path("origen").asText()).isEqualTo("CONDUCTORES");
        assertThat(diesel.path("fecha").asText()).isEqualTo("2026-11-22T17:00:00-04:00");
        assertThat(jdbc.queryForObject("select count(*) from confirmacion_recarga", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("CP-112: la misma confirmación 10 minutos después responde 409; pasados 60 minutos vuelve a valer")
    void cp112ConfirmacionRepetida() throws Exception {
        confirmar(conductor, surtidor, "DIESEL", 20).andExpect(status().isCreated());
        reloj.avanzar(Duration.ofMinutes(10));
        confirmar(conductor, surtidor, "DIESEL", 20).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFIRMACION_REPETIDA"));
        assertThat(jdbc.queryForObject("select count(*) from confirmacion_recarga", Integer.class))
                .as("la repetida no se registra").isEqualTo(1);

        confirmar(conductor, surtidor, "GASOLINA", 20).andExpect(status().isCreated()); // otro tipo sí
        confirmar(crearConductor("google-otro", false), surtidor, "DIESEL", 20)
                .andExpect(status().isCreated()); // otro conductor sí

        reloj.avanzar(Duration.ofMinutes(51));
        confirmar(conductor, surtidor, "DIESEL", 20).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("CP-113: una confirmación desde 500 m responde 422; a 55 m también, y a 45 m se acepta")
    void cp113ConfirmacionLejos() throws Exception {
        confirmar(conductor, surtidor, "DIESEL", 500).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("LEJOS_DEL_SURTIDOR"));
        confirmar(conductor, surtidor, "DIESEL", 55).andExpect(status().isUnprocessableEntity());
        assertThat(combustible("DIESEL")).as("nada cambió").isNull();
        confirmar(conductor, surtidor, "DIESEL", 45).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("RN-05.9: con el tipo Desconocido responde 422; un tipo que no existe, 400")
    void tipoDesconocido() throws Exception {
        confirmar(conductor, surtidor, "DESCONOCIDO", 20).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TIPO_NO_PERMITIDO"));
        confirmar(conductor, surtidor, "KEROSENE", 20).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PETICION_INVALIDA"));
        assertThat(jdbc.queryForObject("select count(*) from confirmacion_recarga", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Rechazos: surtidor inexistente 404, cuerpo incompleto 400 y sin JWT 401")
    void rechazos() throws Exception {
        confirmar(conductor, 999_999, "DIESEL", 20).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SURTIDOR_NO_ENCONTRADO"));
        mvc.perform(post("/surtidores/" + surtidor + "/confirmaciones").header("Authorization", jwt(conductor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"DIESEL\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/surtidores/" + surtidor + "/confirmaciones").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"DIESEL\",\"ubicacion\":{\"latitud\":-21.52,\"longitud\":-64.72}}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("RN-05.12 y RN-05.15: un agotado de WhatsApp más nuevo tapa a la confirmación, y con la misma hora gana WhatsApp")
    void confirmacionFrenteAWhatsapp() throws Exception {
        datoWhatsapp("GASOLINA", false, AHORA.minus(Duration.ofHours(1)));
        confirmar(conductor, surtidor, "GASOLINA", 10).andExpect(status().isCreated());
        assertThat(combustible("GASOLINA").path("origen").asText()).isEqualTo("CONDUCTORES");
        assertThat(combustible("GASOLINA").path("disponible").asBoolean()).isTrue();

        datoWhatsapp("GASOLINA", false, AHORA.plus(Duration.ofMinutes(10)));
        reloj.avanzar(Duration.ofMinutes(15));
        assertThat(combustible("GASOLINA").path("origen").asText()).isEqualTo("WHATSAPP");
        assertThat(combustible("GASOLINA").path("disponible").asBoolean()).isFalse();

        datoWhatsapp("DIESEL", false, reloj.instant());
        confirmar(conductor, surtidor, "DIESEL", 10).andExpect(status().isCreated());
        assertThat(combustible("DIESEL").path("origen").asText())
                .as("con la misma fecha, la confirmación no tapa a WhatsApp").isEqualTo("WHATSAPP");
        assertThat(jdbc.queryForObject("select bool_and(disponible) from estado_combustible where origen = 'CONDUCTORES'",
                Boolean.class)).as("una confirmación nunca marca agotado").isTrue();
    }

    @Test
    @DisplayName("D-48: un usuario fantasma no confirma recargas y el dato real queda igual")
    void usuarioFantasma() throws Exception {
        long fantasma = crearConductor("prueba:carga-1:1", true);
        confirmar(fantasma, surtidor, "DIESEL", 20).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        assertThat(jdbc.queryForObject("select count(*) from estado_combustible", Integer.class)).isZero();
    }
}
