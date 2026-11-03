package com.churoviaje.backend.servicios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.churoviaje.backend.config.LectorCoberturaGeoJson;
import com.churoviaje.backend.dominio.Ubicacion;
import com.churoviaje.backend.dominio.cobertura.CoberturaManchaUrbana;
import com.churoviaje.backend.dto.RutaPeticion;
import com.churoviaje.backend.dto.RutaRespuesta;
import com.churoviaje.backend.motor.MotorDeRutas;
import com.churoviaje.backend.motor.MotorNoDisponibleException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** HU-05 (#65): el servicio valida los puntos, arma el mensaje para el motor y traduce su respuesta. */
class RutaServicioTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Ubicacion PLAZA = punto("-21.5355", "-64.7296");
    private static final Ubicacion UNIVERSIDAD = punto("-21.5300", "-64.7260");
    private static final Ubicacion MERCADO = punto("-21.5348", "-64.7280");
    private static final Ubicacion SAN_LORENZO = punto("-21.418100", "-64.749400");

    private MotorDeRutas motor;
    private RutaServicio servicio;

    private static Ubicacion punto(String latitud, String longitud) {
        return new Ubicacion(new BigDecimal(latitud), new BigDecimal(longitud));
    }

    private static JsonNode leer(String texto) throws Exception {
        return JSON.readTree(texto);
    }

    @BeforeEach
    void preparar() throws Exception {
        CoberturaManchaUrbana cobertura = new LectorCoberturaGeoJson()
                .leer(JSON.readTree(Path.of("../datos/mancha_urbana_tarija.geojson").toFile()));
        motor = mock(MotorDeRutas.class);
        servicio = new RutaServicio(cobertura, motor, JSON);
    }

    @Test
    @DisplayName("Una ruta encontrada trae distancia, tiempo, trazado y tramos con su calle (RN-03.13)")
    void rutaEncontrada() throws Exception {
        when(motor.calcularRuta(any())).thenReturn(leer("""
                {"id":17,"ok":true,"estado":"ENCONTRADA","distancia_m":755,"tiempo_s":136,
                 "trazado":[[-21.5355,-64.7296],[-21.5348,-64.728],[-21.53,-64.726]],
                 "tramos":[{"id":"osm:1:1:ida","calle":"Calle Comercio"},{"id":"osm:2:1:ida","calle":"Av. Las Américas"}]}"""));

        RutaRespuesta r = servicio.calcular(new RutaPeticion(PLAZA, UNIVERSIDAD, null));

        assertThat(r.estado()).isEqualTo("ENCONTRADA");
        assertThat(r.mensaje()).isNull();
        assertThat(r.distanciaM()).isEqualTo(755);
        assertThat(r.tiempoS()).isEqualTo(136);
        assertThat(r.trazado()).hasSize(3);
        assertThat(r.trazado().get(1)).isEqualTo(new RutaRespuesta.Punto(-21.5348, -64.728));
        assertThat(r.tramos()).containsExactly(new RutaRespuesta.TramoRuta("osm:1:1:ida", "Calle Comercio"),
                new RutaRespuesta.TramoRuta("osm:2:1:ida", "Av. Las Américas"));
    }

    @Test
    @DisplayName("Al motor le llegan origen, destino y paradas como [latitud, longitud], en orden")
    void mensajeAlMotor() throws Exception {
        when(motor.calcularRuta(any())).thenReturn(leer("{\"ok\":true,\"estado\":\"SIN_RUTA\"}"));

        servicio.calcular(new RutaPeticion(PLAZA, UNIVERSIDAD, List.of(MERCADO)));

        ArgumentCaptor<ObjectNode> enviado = ArgumentCaptor.forClass(ObjectNode.class);
        verify(motor).calcularRuta(enviado.capture());
        ObjectNode m = enviado.getValue();
        assertThat(m.path("origen").path(0).asDouble()).isEqualTo(-21.5355);
        assertThat(m.path("origen").path(1).asDouble()).isEqualTo(-64.7296);
        assertThat(m.path("destino").path(0).asDouble()).isEqualTo(-21.53);
        assertThat(m.path("destino").path(1).asDouble()).isEqualTo(-64.726);
        assertThat(m.path("paradas")).hasSize(1);
        assertThat(m.path("paradas").path(0).path(0).asDouble()).isEqualTo(-21.5348);
        assertThat(m.path("paradas").path(0).path(1).asDouble()).isEqualTo(-64.728);
    }

    @Test
    @DisplayName("RN-03.12: sin camino responde «No se encontró una ruta válida», sin error")
    void sinRuta() throws Exception {
        when(motor.calcularRuta(any())).thenReturn(leer("{\"ok\":true,\"estado\":\"SIN_RUTA\"}"));

        RutaRespuesta r = servicio.calcular(new RutaPeticion(PLAZA, UNIVERSIDAD, List.of()));

        assertThat(r).isEqualTo(RutaRespuesta.sinRuta());
        assertThat(r.mensaje()).isEqualTo("No se encontró una ruta válida");
    }

    @Test
    @DisplayName("RN-03.4: un punto que el motor no puede ajustar a la red es «Origen o destino inválido»")
    void puntoInvalidoDelMotor() throws Exception {
        when(motor.calcularRuta(any())).thenReturn(leer("{\"ok\":true,\"estado\":\"PUNTO_INVALIDO\"}"));

        assertThat(servicio.calcular(new RutaPeticion(PLAZA, UNIVERSIDAD, null))).isEqualTo(RutaRespuesta.puntoInvalido());
    }

    @Test
    @DisplayName("CP-93, RN-03.3: un destino fuera de la mancha urbana responde «punto inválido» sin llamar al motor")
    void fueraDeLaManchaNoLlamaAlMotor() {
        assertThat(servicio.calcular(new RutaPeticion(PLAZA, SAN_LORENZO, null)).estado()).isEqualTo("PUNTO_INVALIDO");
        assertThat(servicio.calcular(new RutaPeticion(PLAZA, UNIVERSIDAD, List.of(SAN_LORENZO))).mensaje())
                .isEqualTo("Origen o destino inválido");
        verify(motor, never()).calcularRuta(any());
    }

    @Test
    @DisplayName("RN-03.15: si el núcleo no tiene la red vial, es como si el motor no estuviera disponible (503)")
    void redNoCargadaEs503() throws Exception {
        when(motor.calcularRuta(any())).thenReturn(leer(
                "{\"ok\":false,\"error\":{\"codigo\":\"RED_NO_CARGADA\",\"descripcion\":\"sin red\"}}"));

        assertThatThrownBy(() -> servicio.calcular(new RutaPeticion(PLAZA, UNIVERSIDAD, null)))
                .isInstanceOf(MotorNoDisponibleException.class);
    }

    @Test
    @DisplayName("Un rechazo inesperado del núcleo es un error del servidor, no una ruta")
    void otroRechazoEsErrorInterno() throws Exception {
        when(motor.calcularRuta(any())).thenReturn(leer(
                "{\"ok\":false,\"error\":{\"codigo\":\"PETICION_INVALIDA\",\"descripcion\":\"x\"}}"));
        assertThatThrownBy(() -> servicio.calcular(new RutaPeticion(PLAZA, UNIVERSIDAD, null)))
                .isInstanceOf(IllegalStateException.class);

        when(motor.calcularRuta(any())).thenReturn(leer("{\"ok\":true,\"estado\":\"QUIZAS\"}"));
        assertThatThrownBy(() -> servicio.calcular(new RutaPeticion(PLAZA, UNIVERSIDAD, null)))
                .isInstanceOf(IllegalStateException.class);
    }
}
