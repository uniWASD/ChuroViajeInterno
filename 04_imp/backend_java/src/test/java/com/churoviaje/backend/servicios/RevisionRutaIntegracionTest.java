package com.churoviaje.backend.servicios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.churoviaje.backend.config.PruebaIntegracionPostgis;
import com.churoviaje.backend.motor.MotorDeRutas;
import com.churoviaje.backend.motor.MotorNoDisponibleException;
import com.churoviaje.backend.seguridad.JwtServicio;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** HU-06 (#82): {@code POST /rutas/revision} de punta a punta en el backend, con el motor reemplazado por un doble. */
class RevisionRutaIntegracionTest extends PruebaIntegracionPostgis {

    /** De la plaza al mirador por el mercado, con un evento ya avisado en esta ruta. */
    private static final String PETICION = """
            {"posicion":{"latitud":-21.5355,"longitud":-64.7296},
             "tramos":["Plaza-Mercado","Mercado-Universidad","Universidad-Mirador"],
             "destino":{"latitud":-21.529,"longitud":-64.722},
             "paradas":[{"latitud":-21.53,"longitud":-64.726}],
             "eventosAvisados":[150]}""";

    @Autowired MockMvc mvc;
    @Autowired JwtServicio jwt;
    @Autowired ObjectMapper json;
    @MockitoBean MotorDeRutas motor;

    private ResultActions revisar(String cuerpo) throws Exception {
        return mvc.perform(post("/rutas/revision").header("Authorization", "Bearer " + jwt.emitir(1L, Instant.now()))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private void motorResponde(String respuesta) throws Exception {
        when(motor.revisarRuta(any())).thenReturn(json.readTree(respuesta));
    }

    @Test
    @DisplayName("CP-134: con un accidente nuevo sobre un tramo que falta, la respuesta trae una alternativa más barata")
    void cp134Alternativa() throws Exception {
        motorResponde("""
                {"id":1,"ok":true,"estado":"ALTERNATIVA","evento":"153","costo":1700,"costo_actual":3244,
                 "distancia_m":1700,"tiempo_s":306,
                 "trazado":[[-21.5355,-64.7296],[-21.533,-64.731],[-21.53,-64.726],[-21.529,-64.722]],
                 "tramos":[{"id":"Plaza-Hospital","calle":"Av. Circunvalación"},
                           {"id":"Hospital-Universidad","calle":"Calle La Cuesta (bajada)"},
                           {"id":"Universidad-Mirador","calle":"Camino al Mirador"}]}""");

        revisar(PETICION).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ALTERNATIVA"))
                .andExpect(jsonPath("$.eventoId").value(153))
                .andExpect(jsonPath("$.costo").value(1700))
                .andExpect(jsonPath("$.costoActual").value(3244))
                .andExpect(jsonPath("$.distanciaM").value(1700))
                .andExpect(jsonPath("$.tiempoS").value(306))
                .andExpect(jsonPath("$.trazado[1].latitud").value(-21.533))
                .andExpect(jsonPath("$.tramos[0].id").value("Plaza-Hospital"))
                .andExpect(jsonPath("$.tramos[1].calle").value("Calle La Cuesta (bajada)"));

        // RN-04.15: al motor le llegan la posición actual, lo que falta, el destino y las paradas que faltan.
        ArgumentCaptor<ObjectNode> mensaje = ArgumentCaptor.forClass(ObjectNode.class);
        verify(motor).revisarRuta(mensaje.capture());
        JsonNode m = mensaje.getValue();
        assertThat(m.path("posicion").path(0).asDouble()).isEqualTo(-21.5355);
        assertThat(m.path("tramos").size()).isEqualTo(3);
        assertThat(m.path("tramos").path(1).asText()).isEqualTo("Mercado-Universidad");
        assertThat(m.path("destino").path(1).asDouble()).isEqualTo(-64.722);
        assertThat(m.path("paradas").size()).isEqualTo(1);
        assertThat(m.path("eventos_avisados").path(0).asText()).isEqualTo("150");
    }

    @Test
    @DisplayName("Si un bloqueo corta lo que falta, la alternativa llega sin costo de seguir")
    void alternativaConLaRutaCortada() throws Exception {
        motorResponde("""
                {"id":1,"ok":true,"estado":"ALTERNATIVA","evento":"156","costo":1700,"distancia_m":1700,"tiempo_s":306,
                 "trazado":[[-21.5355,-64.7296],[-21.529,-64.722]],"tramos":[{"id":"Plaza-Hospital","calle":"Av. Circunvalación"}]}""");
        revisar(PETICION).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ALTERNATIVA"))
                .andExpect(jsonPath("$.costoActual").doesNotExist());
    }

    @Test
    @DisplayName("CP-135: si no hay camino mejor, la respuesta es «sin alternativa», con el evento que la provocó")
    void cp135SinAlternativa() throws Exception {
        motorResponde("""
                {"id":1,"ok":true,"estado":"SIN_ALTERNATIVA","evento":"154"}""");
        revisar(PETICION).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SIN_ALTERNATIVA"))
                .andExpect(jsonPath("$.eventoId").value(154))
                .andExpect(jsonPath("$.tramos").doesNotExist())
                .andExpect(jsonPath("$.costo").doesNotExist());
    }

    @Test
    @DisplayName("CP-136: el mismo evento, enviado como ya avisado, devuelve «sin cambios»")
    void cp136SinCambios() throws Exception {
        motorResponde("""
                {"id":1,"ok":true,"estado":"SIN_CAMBIOS"}""");
        revisar(PETICION.replace("[150]", "[150,153]")).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SIN_CAMBIOS"))
                .andExpect(jsonPath("$.eventoId").doesNotExist());

        ArgumentCaptor<ObjectNode> mensaje = ArgumentCaptor.forClass(ObjectNode.class);
        verify(motor).revisarRuta(mensaje.capture());
        assertThat(mensaje.getValue().path("eventos_avisados").toString()).isEqualTo("[\"150\",\"153\"]");
    }

    @Test
    @DisplayName("CP-137: con tramos que no existen, la respuesta es «ruta desactualizada»")
    void cp137RutaDesactualizada() throws Exception {
        motorResponde("""
                {"id":1,"ok":true,"estado":"RUTA_DESACTUALIZADA"}""");
        revisar(PETICION).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RUTA_DESACTUALIZADA"))
                .andExpect(jsonPath("$.eventoId").doesNotExist());
    }

    @Test
    @DisplayName("Sin paradas ni eventos avisados también se puede revisar")
    void sinParadasNiAvisados() throws Exception {
        motorResponde("""
                {"id":1,"ok":true,"estado":"SIN_CAMBIOS"}""");
        revisar("""
                {"posicion":{"latitud":-21.5355,"longitud":-64.7296},"tramos":["Plaza-Mercado"],
                 "destino":{"latitud":-21.5348,"longitud":-64.728}}""").andExpect(status().isOk());

        ArgumentCaptor<ObjectNode> mensaje = ArgumentCaptor.forClass(ObjectNode.class);
        verify(motor).revisarRuta(mensaje.capture());
        assertThat(mensaje.getValue().path("paradas").isArray()).isTrue();
        assertThat(mensaje.getValue().path("eventos_avisados").size()).isZero();
    }

    @Test
    @DisplayName("Con el motor caído o sin la red vial responde 503 y la app sigue con su ruta")
    void motorNoDisponible() throws Exception {
        when(motor.revisarRuta(any())).thenThrow(new MotorNoDisponibleException());
        revisar(PETICION).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("MOTOR_NO_DISPONIBLE"));

        // doReturn: con when() se llamaría al doble, que todavía lanza la excepción de arriba.
        doReturn(json.readTree("""
                {"id":1,"ok":false,"error":{"codigo":"RED_NO_CARGADA","descripcion":"sin red"}}"""))
                .when(motor).revisarRuta(any());
        revisar(PETICION).andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("Sin tramos, sin posición o sin destino responde 400 sin llamar al motor")
    void peticionInvalida() throws Exception {
        revisar("""
                {"posicion":{"latitud":-21.5355,"longitud":-64.7296},"destino":{"latitud":-21.529,"longitud":-64.722}}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PETICION_INVALIDA"));
        revisar("""
                {"tramos":["Plaza-Mercado"],"destino":{"latitud":-21.529,"longitud":-64.722}}""")
                .andExpect(status().isBadRequest());
        revisar("""
                {"posicion":{"latitud":-21.5355,"longitud":-64.7296},"tramos":["Plaza-Mercado"]}""")
                .andExpect(status().isBadRequest());
        verify(motor, never()).revisarRuta(any());
    }

    @Test
    @DisplayName("Sin JWT responde 401")
    void pideJwt() throws Exception {
        mvc.perform(post("/rutas/revision").contentType(MediaType.APPLICATION_JSON).content(PETICION))
                .andExpect(status().isUnauthorized());
        verify(motor, never()).revisarRuta(any());
    }
}
