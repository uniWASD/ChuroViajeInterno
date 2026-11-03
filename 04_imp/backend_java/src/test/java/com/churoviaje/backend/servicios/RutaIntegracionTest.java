package com.churoviaje.backend.servicios;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.churoviaje.backend.config.PruebaIntegracionPostgis;
import com.churoviaje.backend.motor.MotorDeRutas;
import com.churoviaje.backend.motor.MotorNoDisponibleException;
import com.churoviaje.backend.seguridad.JwtServicio;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** HU-05 (#65): {@code POST /rutas} de punta a punta en el backend, con el motor reemplazado por un doble. */
class RutaIntegracionTest extends PruebaIntegracionPostgis {

    private static final String PLAZA_A_UNIVERSIDAD = """
            {"origen":{"latitud":-21.5355,"longitud":-64.7296},"destino":{"latitud":-21.5300,"longitud":-64.7260}}""";
    private static final String RESPUESTA_MOTOR = """
            {"id":1,"ok":true,"estado":"ENCONTRADA","distancia_m":755,"tiempo_s":136,
             "trazado":[[-21.5355,-64.7296],[-21.5348,-64.728],[-21.53,-64.726]],
             "tramos":[{"id":"osm:1:1:ida","calle":"Calle Comercio"},{"id":"osm:2:1:ida","calle":"Av. Las Américas"}]}""";

    @Autowired MockMvc mvc;
    @Autowired JwtServicio jwt;
    @Autowired ObjectMapper json;
    @MockitoBean MotorDeRutas motor;

    private String autorizacion() {
        return "Bearer " + jwt.emitir(1L, Instant.now());
    }

    @Test
    @DisplayName("CP-92: de la plaza a la universidad responde la ruta con distancia, tiempo, trazado y calles")
    void cp92RutaEncontrada() throws Exception {
        when(motor.calcularRuta(any())).thenReturn(json.readTree(RESPUESTA_MOTOR));

        mvc.perform(post("/rutas").header("Authorization", autorizacion())
                        .contentType(MediaType.APPLICATION_JSON).content(PLAZA_A_UNIVERSIDAD))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.estado").value("ENCONTRADA"))
                .andExpect(jsonPath("$.distanciaM").value(755))
                .andExpect(jsonPath("$.tiempoS").value(136))
                .andExpect(jsonPath("$.trazado[0].latitud").value(-21.5355))
                .andExpect(jsonPath("$.trazado[2].longitud").value(-64.726))
                .andExpect(jsonPath("$.tramos[1].calle").value("Av. Las Américas"))
                .andExpect(jsonPath("$.tramos[0].id").value("osm:1:1:ida"))
                .andExpect(jsonPath("$.mensaje").doesNotExist());
    }

    @Test
    @DisplayName("CP-93: un destino en San Lorenzo responde 200 con «Origen o destino inválido»")
    void cp93DestinoFueraDeTarija() throws Exception {
        mvc.perform(post("/rutas").header("Authorization", autorizacion()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"origen":{"latitud":-21.5355,"longitud":-64.7296},
                                 "destino":{"latitud":-21.4181,"longitud":-64.7494},"paradas":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PUNTO_INVALIDO"))
                .andExpect(jsonPath("$.mensaje").value("Origen o destino inválido"))
                .andExpect(jsonPath("$.trazado").doesNotExist());
        verify(motor, never()).calcularRuta(any());
    }

    @Test
    @DisplayName("CP-94: con el motor caído responde 503, y el siguiente intento, con el motor de vuelta, funciona")
    void cp94MotorCaidoYLuegoDeVuelta() throws Exception {
        when(motor.calcularRuta(any())).thenThrow(new MotorNoDisponibleException())
                .thenReturn(json.readTree(RESPUESTA_MOTOR));

        mvc.perform(post("/rutas").header("Authorization", autorizacion())
                        .contentType(MediaType.APPLICATION_JSON).content(PLAZA_A_UNIVERSIDAD))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("MOTOR_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
        mvc.perform(post("/rutas").header("Authorization", autorizacion())
                        .contentType(MediaType.APPLICATION_JSON).content(PLAZA_A_UNIVERSIDAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENCONTRADA"));
    }

    @Test
    @DisplayName("RN-03.12: «sin ruta» es 200 con su mensaje, no un error")
    void sinRutaEs200() throws Exception {
        when(motor.calcularRuta(any())).thenReturn(json.readTree("{\"ok\":true,\"estado\":\"SIN_RUTA\"}"));

        mvc.perform(post("/rutas").header("Authorization", autorizacion())
                        .contentType(MediaType.APPLICATION_JSON).content(PLAZA_A_UNIVERSIDAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SIN_RUTA"))
                .andExpect(jsonPath("$.mensaje").value("No se encontró una ruta válida"));
    }

    @Test
    @DisplayName("RN-03.2: sin origen o sin destino responde 400")
    void faltaOrigenODestino() throws Exception {
        mvc.perform(post("/rutas").header("Authorization", autorizacion()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origen\":{\"latitud\":-21.5355,\"longitud\":-64.7296}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PETICION_INVALIDA"));
        mvc.perform(post("/rutas").header("Authorization", autorizacion()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destino\":{\"latitud\":-21.5355,\"longitud\":-64.7296}}"))
                .andExpect(status().isBadRequest());
        verify(motor, never()).calcularRuta(any());
    }

    @Test
    @DisplayName("Más de 10 paradas responde 400")
    void demasiadasParadas() throws Exception {
        String parada = "{\"latitud\":-21.5348,\"longitud\":-64.7280}";
        String paradas = String.join(",", java.util.Collections.nCopies(11, parada));
        mvc.perform(post("/rutas").header("Authorization", autorizacion()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origen\":{\"latitud\":-21.5355,\"longitud\":-64.7296},"
                                + "\"destino\":{\"latitud\":-21.53,\"longitud\":-64.726},\"paradas\":[" + paradas + "]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("RN-03.1: sin JWT responde 401")
    void exigeJwt() throws Exception {
        mvc.perform(post("/rutas").contentType(MediaType.APPLICATION_JSON).content(PLAZA_A_UNIVERSIDAD))
                .andExpect(status().isUnauthorized());
        verify(motor, never()).calcularRuta(any());
    }
}
