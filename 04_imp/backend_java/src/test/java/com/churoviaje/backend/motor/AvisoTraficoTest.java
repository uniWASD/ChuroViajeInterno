package com.churoviaje.backend.motor;

import static org.assertj.core.api.Assertions.assertThat;

import com.churoviaje.backend.dominio.EstadoEvento;
import com.churoviaje.backend.dominio.EventoVial;
import com.churoviaje.backend.dominio.OrigenEvento;
import com.churoviaje.backend.dominio.Ubicacion;
import com.churoviaje.backend.dominio.catalogo.TipoEvento;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T-099 (#67): el cuerpo de {@code actualizar_trafico} como lo define 05_contratos.md. */
class AvisoTraficoTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Ubicacion PUNTO = new Ubicacion(new BigDecimal("-21.5352"), new BigDecimal("-64.7288"));
    private static final Instant OCURRIO = Instant.parse("2026-10-06T19:02:00Z");

    private static EventoVial evento(String tipo, int vigencia, OrigenEvento origen, String nivel) {
        return EventoVial.desdeRegistro(153, new TipoEvento(tipo, tipo, "#000000", vigencia, true), PUNTO, OCURRIO,
                OCURRIO, origen == OrigenEvento.CONDUCTOR ? 1L : null, origen, nivel, EstadoEvento.ACTIVO, 0, 0, false);
    }

    @Test
    @DisplayName("«nace» lleva el evento, el tipo, la ubicacion como [latitud, longitud] y el vencimiento con zona horaria")
    void naceComoEnElContrato() {
        String mensaje = AvisoTrafico.nace(evento("ACCIDENTE", 60, OrigenEvento.CONDUCTOR, null)).aMensaje(JSON).toString();
        assertThat(mensaje).isEqualTo("{\"evento\":\"153\",\"accion\":\"nace\",\"tipo\":\"ACCIDENTE\","
                + "\"ubicacion\":[-21.5352,-64.7288],\"vence\":\"2026-10-06T16:02:00-04:00\"}");
    }

    @Test
    @DisplayName("La congestion lleva tambien su nivel, y «cambia_vencimiento» el vencimiento nuevo")
    void congestionConNivel() {
        EventoVial congestion = evento("CONGESTION", 30, OrigenEvento.TELEMETRIA, "MUY_CONGESTIONADO");
        var mensaje = AvisoTrafico.cambiaVencimiento(congestion).aMensaje(JSON);
        assertThat(mensaje.get("accion").asText()).isEqualTo("cambia_vencimiento");
        assertThat(mensaje.get("nivel").asText()).isEqualTo("MUY_CONGESTIONADO");
        assertThat(mensaje.get("vence").asText()).isEqualTo("2026-10-06T15:32:00-04:00");
    }

    @Test
    @DisplayName("«retira» y «vence» solo necesitan el evento")
    void retiraYVenceSoloLlevanElEvento() {
        assertThat(AvisoTrafico.retira(7).aMensaje(JSON).toString()).isEqualTo("{\"evento\":\"7\",\"accion\":\"retira\"}");
        assertThat(AvisoTrafico.vence(8).aMensaje(JSON).toString()).isEqualTo("{\"evento\":\"8\",\"accion\":\"vence\"}");
    }
}
