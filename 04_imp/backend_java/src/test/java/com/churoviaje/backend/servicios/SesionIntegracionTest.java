package com.churoviaje.backend.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.churoviaje.backend.config.ConfiguracionRelojDePrueba;
import com.churoviaje.backend.config.PruebaIntegracionPostgis;
import com.churoviaje.backend.config.RelojDePrueba;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** HU-10 / CU-08: CP-31 a CP-35 y las reglas de acceso de RN-08.11 y D-13, contra una base PostGIS real. */
@Import(ConfiguracionRelojDePrueba.class)
class SesionIntegracionTest extends PruebaIntegracionPostgis {

    private static final String CREDENCIAL_SERVICIO = "credencial-de-servicio-solo-para-pruebas-0123456789";
    private static final String EVENTO = """
            {"tipo":"ACCIDENTE","ubicacion":{"latitud":-21.53,"longitud":-64.73},
             "horaConfirmacion":"2026-10-06T15:02:00-04:00","minutosElegidos":0}
            """;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired RelojDePrueba reloj;
    @MockitoBean ValidadorTokenGoogle google;

    @BeforeEach
    void limpiar() {
        jdbc.update("delete from sesion");
        jdbc.update("delete from conductor");
        reloj.fijar(java.time.Instant.parse("2026-11-01T12:00:00Z"));
    }

    private ResultActions iniciar(String googleId) throws Exception {
        when(google.identificarCuenta("token-de-" + googleId)).thenReturn(Optional.of(googleId));
        return mvc.perform(post("/sesion/google").contentType(MediaType.APPLICATION_JSON)
                .content("{\"tokenGoogle\":\"token-de-" + googleId + "\"}"));
    }

    private JsonNode cuerpo(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    private ResultActions con(String jwt, String ruta) throws Exception {
        return mvc.perform(post(ruta).header("Authorization", "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON).content(EVENTO));
    }

    private ResultActions renovar(String token) throws Exception {
        return mvc.perform(post("/sesion/renovar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"tokenRenovacion\":\"" + token + "\"}"));
    }

    private ResultActions cerrar(String token) throws Exception {
        return mvc.perform(post("/sesion/cerrar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"tokenRenovacion\":\"" + token + "\"}"));
    }

    private long contar(String tabla) {
        return jdbc.queryForObject("select count(*) from " + tabla, Long.class);
    }

    @Test
    @DisplayName("CP-31: el primer ingreso crea al conductor y devuelve el JWT; el segundo no crea otro")
    void cp31PrimerIngresoCreaAlConductorYElSegundoNo() throws Exception {
        JsonNode primero = cuerpo(iniciar("google-1").andExpect(status().isOk())
                .andExpect(jsonPath("$.jwt").isNotEmpty())
                .andExpect(jsonPath("$.tokenRenovacion").isNotEmpty())
                .andExpect(jsonPath("$.expiraEn").isNotEmpty()));
        assertEquals(1, contar("conductor"));

        JsonNode segundo = cuerpo(iniciar("google-1").andExpect(status().isOk()));
        assertEquals(1, contar("conductor"), "la misma cuenta de Google es el mismo conductor (RN-08.4)");
        assertEquals(2, contar("sesion"));
        assertNotEquals(primero.get("tokenRenovacion").asText(), segundo.get("tokenRenovacion").asText());

        iniciar("google-2").andExpect(status().isOk());
        assertEquals(2, contar("conductor"));
    }

    @Test
    @DisplayName("El JWT vence a la hora exacta y el token de renovacion se entrega aparte (RN-08.5, RN-08.12)")
    void elJwtDuraUnaHora() throws Exception {
        JsonNode sesion = cuerpo(iniciar("google-1").andExpect(status().isOk()));
        assertEquals(OffsetDateTime.parse("2026-11-01T13:00:00Z"), OffsetDateTime.parse(sesion.get("expiraEn").asText()));
    }

    @Test
    @DisplayName("RN-08.6: no se guarda el token de renovacion ni datos de la cuenta de Google, solo su identificador")
    void soloSeGuardaElIdentificadorDeGoogleYLaHuellaDelToken() throws Exception {
        JsonNode sesion = cuerpo(iniciar("google-1").andExpect(status().isOk()));
        String token = sesion.get("tokenRenovacion").asText();

        var columnasConductor = jdbc.queryForList("""
                select column_name from information_schema.columns
                where table_name = 'conductor' order by column_name""", String.class);
        assertEquals(java.util.List.of("fecha_alta", "google_id", "id", "simulado"), columnasConductor);

        String huella = jdbc.queryForObject("select token_renovacion_hash from sesion", String.class);
        assertEquals(64, huella.length());
        assertNotEquals(token, huella);
        assertFalse(huella.contains(token));
    }

    @Test
    @DisplayName("CP-32: un token de Google vencido o falso responde 401 y no emite JWT ni crea nada")
    void cp32TokenDeGoogleInvalidoResponde401() throws Exception {
        when(google.identificarCuenta("falso")).thenReturn(Optional.empty());
        mvc.perform(post("/sesion/google").contentType(MediaType.APPLICATION_JSON).content("{\"tokenGoogle\":\"falso\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("TOKEN_GOOGLE_INVALIDO"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty())
                .andExpect(jsonPath("$.jwt").doesNotExist());
        assertEquals(0, contar("conductor"));
        assertEquals(0, contar("sesion"));
    }

    @Test
    @DisplayName("RN-08.9: si no se puede consultar a Google responde 503 y no emite nada")
    void sinConexionConGoogleResponde503() throws Exception {
        when(google.identificarCuenta("lento"))
                .thenThrow(new ValidadorTokenGoogle.GoogleNoDisponibleException(new java.io.IOException("sin red")));
        mvc.perform(post("/sesion/google").contentType(MediaType.APPLICATION_JSON).content("{\"tokenGoogle\":\"lento\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("GOOGLE_NO_DISPONIBLE"));
        assertEquals(0, contar("conductor"));
    }

    @Test
    @DisplayName("Una peticion de sesion sin el token responde 400 en el formato de rechazo")
    void peticionSinTokenResponde400() throws Exception {
        mvc.perform(post("/sesion/google").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PETICION_INVALIDA"));
        mvc.perform(post("/sesion/renovar").contentType(MediaType.APPLICATION_JSON).content("{\"tokenRenovacion\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/sesion/cerrar").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CP-33: sin JWT, con uno vencido o con uno falso, las rutas de conductor responden 401")
    void cp33RutasDeConductorExigenJwtVigente() throws Exception {
        mvc.perform(post("/eventos").contentType(MediaType.APPLICATION_JSON).content(EVENTO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        mvc.perform(get("/eventos")).andExpect(status().isUnauthorized());
        con("esto-no-es-un-jwt", "/eventos").andExpect(status().isUnauthorized());

        String jwt = cuerpo(iniciar("google-1")).get("jwt").asText();
        con(jwt, "/eventos").andExpect(status().isCreated());

        reloj.avanzar(Duration.ofMinutes(59));
        con(jwt, "/eventos").andExpect(status().isCreated());
        reloj.avanzar(Duration.ofMinutes(2));
        con(jwt, "/eventos").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    @DisplayName("CP-34: con un token de renovacion vigente se obtiene un JWT nuevo; con uno de mas de 30 dias, 401")
    void cp34RenovacionHastaLos30Dias() throws Exception {
        JsonNode sesion = cuerpo(iniciar("google-1"));
        String jwtViejo = sesion.get("jwt").asText();
        String token = sesion.get("tokenRenovacion").asText();

        reloj.avanzar(Duration.ofHours(2));
        con(jwtViejo, "/eventos").andExpect(status().isUnauthorized());

        JsonNode renovado = cuerpo(renovar(token).andExpect(status().isOk())
                .andExpect(jsonPath("$.jwt").isNotEmpty())
                .andExpect(jsonPath("$.tokenRenovacion").doesNotExist()));
        con(renovado.get("jwt").asText(), "/eventos").andExpect(status().isCreated());
        assertEquals(1, contar("conductor"), "renovar no pasa por Google ni crea conductores");
        assertEquals(1, contar("sesion"));

        reloj.avanzar(Duration.ofDays(29));
        renovar(token).andExpect(status().isOk());
        reloj.avanzar(Duration.ofDays(2));
        renovar(token).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_VENCIDA"))
                .andExpect(jsonPath("$.jwt").doesNotExist());
    }

    @Test
    @DisplayName("Un token de renovacion que no existe responde 401")
    void tokenDeRenovacionDesconocidoResponde401() throws Exception {
        renovar("no-existe").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_VENCIDA"));
    }

    @Test
    @DisplayName("CP-35: despues de cerrar sesion, el token de renovacion ya no sirve; cerrar de nuevo no es un error")
    void cp35CerrarSesionInvalidaElTokenDeRenovacion() throws Exception {
        String token = cuerpo(iniciar("google-1")).get("tokenRenovacion").asText();
        String otroToken = cuerpo(iniciar("google-1")).get("tokenRenovacion").asText();

        cerrar(token).andExpect(status().isNoContent());
        renovar(token).andExpect(status().isUnauthorized());
        cerrar(token).andExpect(status().isNoContent());
        renovar(otroToken).andExpect(status().isOk());
        assertTrue(contar("sesion") == 1, "solo se cierra la sesion indicada");
        assertEquals(1, contar("conductor"), "cerrar sesion no borra al conductor");
    }

    @Test
    @DisplayName("D-13: /monitoreo/* solo acepta la credencial de servicio; un conductor recibe 403; sin credencial, 401")
    void monitoreoSoloAceptaLaCredencialDeServicio() throws Exception {
        String jwt = cuerpo(iniciar("google-1")).get("jwt").asText();

        mvc.perform(get("/monitoreo/surtidores")).andExpect(status().isUnauthorized());
        mvc.perform(get("/monitoreo/surtidores").header("Authorization", "Bearer incorrecta"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/monitoreo/surtidores").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        // La ruta todavia no existe (otro issue): con la credencial correcta se pasa la seguridad y llega al 404.
        mvc.perform(get("/monitoreo/surtidores").header("Authorization", "Bearer " + CREDENCIAL_SERVICIO))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("D-13: la credencial de servicio no sirve en las rutas de conductor (403)")
    void laCredencialDeServicioNoSirveParaLasRutasDeConductor() throws Exception {
        con(CREDENCIAL_SERVICIO, "/eventos").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Las rutas /sesion/* no piden JWT, aunque la app mande uno vencido")
    void lasRutasDeSesionNoPidenJwt() throws Exception {
        String token = cuerpo(iniciar("google-1")).get("tokenRenovacion").asText();
        reloj.avanzar(Duration.ofHours(3));
        mvc.perform(post("/sesion/renovar").header("Authorization", "Bearer jwt-vencido")
                .contentType(MediaType.APPLICATION_JSON).content("{\"tokenRenovacion\":\"" + token + "\"}"))
                .andExpect(status().isOk());
    }
}
