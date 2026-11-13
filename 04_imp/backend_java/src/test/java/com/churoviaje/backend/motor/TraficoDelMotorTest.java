package com.churoviaje.backend.motor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** T-099 (#67): los avisos pendientes y sus reintentos, con el motor reemplazado por un doble. */
class TraficoDelMotorTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final MotorDeRutas motor = mock(MotorDeRutas.class);
    private TraficoDelMotor trafico;

    @AfterEach
    void apagar() {
        if (trafico != null) {
            trafico.detener();
        }
    }

    private TraficoDelMotor trafico(boolean habilitado) {
        PropiedadesMotor propiedades = new PropiedadesMotor(habilitado, "nucleo", "red.json", Duration.ofSeconds(3),
                Duration.ofSeconds(60), Duration.ofSeconds(2));
        trafico = new TraficoDelMotor(motor, JSON, propiedades);
        return trafico;
    }

    private static JsonNode ok() throws Exception {
        return JSON.readTree("{\"ok\":true,\"tramos\":[]}");
    }

    @Test
    @DisplayName("El aviso llega al motor en su propio hilo y no queda pendiente")
    void elAvisoLlegaAlMotor() throws Exception {
        when(motor.actualizarTrafico(any())).thenReturn(ok());
        trafico(true).alCambiarUnEvento(AvisoTrafico.retira(5));
        ArgumentCaptor<ObjectNode> mensaje = ArgumentCaptor.forClass(ObjectNode.class);
        verify(motor, timeout(2000)).actualizarTrafico(mensaje.capture());
        assertThat(mensaje.getValue().toString()).isEqualTo("{\"evento\":\"5\",\"accion\":\"retira\"}");
        esperarQueNoQuedenPendientes();
    }

    @Test
    @DisplayName("Si el motor no esta disponible, el aviso queda pendiente y se envia en el siguiente intento")
    void conElMotorCaidoElAvisoSeReintenta() throws Exception {
        when(motor.actualizarTrafico(any())).thenThrow(new MotorNoDisponibleException()).thenReturn(ok());
        trafico(true).alCambiarUnEvento(AvisoTrafico.vence(9));
        verify(motor, timeout(2000)).actualizarTrafico(any());
        assertThat(trafico.cantidadPendiente()).isEqualTo(1);

        trafico.enviarPendientes();
        verify(motor, times(2)).actualizarTrafico(any());
        assertThat(trafico.cantidadPendiente()).isZero();
    }

    @Test
    @DisplayName("Un nucleo que todavia no tiene la red (ok:false) tambien se reintenta; un aviso mal formado no")
    void respuestasNegativas() throws Exception {
        when(motor.actualizarTrafico(any()))
                .thenReturn(JSON.readTree("{\"ok\":false,\"error\":{\"codigo\":\"RED_NO_CARGADA\"}}"));
        trafico(true).alCambiarUnEvento(AvisoTrafico.vence(1));
        verify(motor, timeout(2000)).actualizarTrafico(any());
        Thread.sleep(100);
        assertThat(trafico.cantidadPendiente()).isEqualTo(1);

        when(motor.actualizarTrafico(any()))
                .thenReturn(JSON.readTree("{\"ok\":false,\"error\":{\"codigo\":\"PETICION_INVALIDA\"}}"));
        trafico.enviarPendientes();
        assertThat(trafico.cantidadPendiente()).isZero();
    }

    @Test
    @DisplayName("De dos avisos pendientes del mismo evento se envia solo el ultimo, en orden de llegada")
    void valeElUltimoAvisoDeCadaEvento() throws Exception {
        when(motor.actualizarTrafico(any())).thenThrow(new MotorNoDisponibleException());
        trafico(true);
        trafico.alCambiarUnEvento(AvisoTrafico.retira(1));
        trafico.alCambiarUnEvento(AvisoTrafico.vence(2));
        trafico.alCambiarUnEvento(AvisoTrafico.vence(1));
        Thread.sleep(300);
        assertThat(trafico.cantidadPendiente()).isEqualTo(2);

        List<String> enviados = new ArrayList<>();
        doAnswer(invocacion -> {
            enviados.add(invocacion.getArgument(0).toString());
            return ok();
        }).when(motor).actualizarTrafico(any());
        trafico.enviarPendientes();
        assertThat(enviados).containsExactly("{\"evento\":\"2\",\"accion\":\"vence\"}",
                "{\"evento\":\"1\",\"accion\":\"vence\"}");
    }

    @Test
    @DisplayName("Con el motor deshabilitado no se guarda ningun aviso")
    void sinMotorNoHayAvisos() throws Exception {
        trafico(false).alCambiarUnEvento(AvisoTrafico.retira(3));
        Thread.sleep(100);
        verify(motor, never()).actualizarTrafico(any());
        assertThat(trafico.cantidadPendiente()).isZero();
    }

    private void esperarQueNoQuedenPendientes() throws InterruptedException {
        long limite = System.currentTimeMillis() + 2000;
        while (trafico.cantidadPendiente() > 0 && System.currentTimeMillis() < limite) {
            Thread.sleep(20);
        }
        assertThat(trafico.cantidadPendiente()).isZero();
    }
}
