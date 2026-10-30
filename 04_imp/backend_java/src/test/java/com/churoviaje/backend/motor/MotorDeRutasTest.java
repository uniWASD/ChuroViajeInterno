package com.churoviaje.backend.motor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@SpringBootTest
class MotorDeRutasTest {

    @Autowired
    private MotorDeRutas motorDeRutas;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void cp41_consultarEstado_respondeCargada() {
        // Ejecución normal: el simulador debe devolver "estado": "cargada"
        JsonNode respuesta = motorDeRutas.consultarEstado();
        
        assertNotNull(respuesta);
        assertEquals("cargada", respuesta.path("estado").asText());
    }

    @Test
    void cp42_demoraMasDe3Segundos_reiniciaElMotor() {
        ObjectNode datos = objectMapper.createObjectNode();
        datos.put("operacion", "simular_demora");
        
        // El script Python se detiene 4 segundos, pero Java debe cortar a los 3s
        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class, () -> {
            motorDeRutas.calcularRuta(datos);
        });
        
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, excepcion.getStatusCode());
        assertEquals("motor no disponible", excepcion.getReason());
        
        // Inmediatamente después, el motor debe haberse reiniciado y estar listo
        JsonNode respuesta = motorDeRutas.consultarEstado();
        assertEquals("cargada", respuesta.path("estado").asText());
    }

    @Test
    void cp43_saturacionDeCola_respondeNoDisponibleSinReiniciar() {
        int cantidadPeticiones = 50;
        List<CompletableFuture<Void>> futuros = new ArrayList<>();
        
        // Mandamos 50 peticiones simultáneas que tardan 4 segundos cada una
        for (int i = 0; i < cantidadPeticiones; i++) {
            CompletableFuture<Void> futuro = CompletableFuture.runAsync(() -> {
                ObjectNode datos = objectMapper.createObjectNode();
                datos.put("operacion", "simular_demora");
                motorDeRutas.calcularRuta(datos);
            });
            futuros.add(futuro);
        }
        
        // Contamos cuántas rechazaron con 503 "motor no disponible" por caducar en la fila
        long errores503 = futuros.stream().map(futuro -> {
            try {
                futuro.join();
                return 0;
            } catch (Exception e) {
                if (e.getCause() instanceof ResponseStatusException rse) {
                    if (rse.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
                        return 1;
                    }
                }
                return 0;
            }
        }).reduce(0, Integer::sum);
        
        // La primera demorará al motor, las otras 49 vencerán en la fila esperando
        assertTrue(errores503 >= 49, "Las peticiones encoladas deben vencer a los 3 segundos");
    }

    @Test
    void cp44_caidaDelProceso_loReiniciaAutomaticamente() {
        ObjectNode datos = objectMapper.createObjectNode();
        datos.put("operacion", "simular_caida");
        
        // El script Python hará sys.exit(1), simulando una muerte súbita
        assertThrows(ResponseStatusException.class, () -> {
            motorDeRutas.calcularRuta(datos);
        });

        // La siguiente petición debe detectar que murió, revivirlo y responder bien
        JsonNode respuesta = motorDeRutas.consultarEstado();
        assertEquals("cargada", respuesta.path("estado").asText());
    }
}