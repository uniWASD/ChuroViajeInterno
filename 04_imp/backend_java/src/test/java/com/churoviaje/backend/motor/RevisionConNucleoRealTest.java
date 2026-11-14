package com.churoviaje.backend.motor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.churoviaje.backend.dominio.Ubicacion;
import com.churoviaje.backend.dto.RevisionRutaPeticion;
import com.churoviaje.backend.dto.RevisionRutaRespuesta;
import com.churoviaje.backend.dto.RutaRespuesta.TramoRuta;
import com.churoviaje.backend.servicios.RevisionRutaServicio;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * HU-06 (#82): {@code RevisionRutaServicio} con el núcleo C++ de verdad y la red irregular de prueba
 * ({@code 04_imp/motor_cpp/tests/datos/red_irregular.json}, la que usa la CI). Se salta con otra red, o con un
 * núcleo anterior a #76 que todavía responde «no implementada».
 */
@EnabledIfEnvironmentVariable(named = "MOTOR_PATH", matches = ".+")
@EnabledIfEnvironmentVariable(named = "RED_VIAL_PATH", matches = ".+")
class RevisionConNucleoRealTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void unAccidenteEnLoQueFaltaDaUnaAlternativaYYaAvisadoNoVuelve() throws Exception {
        assumeTrue(Files.readString(Path.of(System.getenv("RED_VIAL_PATH"))).contains("\"Plaza-Mercado\""),
                "la prueba usa la red irregular de prueba");
        PropiedadesMotor propiedades = new PropiedadesMotor(true, System.getenv("MOTOR_PATH"),
                System.getenv("RED_VIAL_PATH"), Duration.ofSeconds(3), Duration.ofSeconds(60), Duration.ofSeconds(2));
        MotorDeRutas motor = new MotorDeRutas(propiedades, LanzadorNucleo.desde(propiedades), List::of, JSON);
        try {
            motor.iniciar();
            long limite = System.currentTimeMillis() + 30_000;
            while (!motor.estaListo() && System.currentTimeMillis() < limite) {
                Thread.sleep(50);
            }
            assertThat(motor.estaListo()).isTrue();
            JsonNode prueba = motor.revisarRuta(JSON.createObjectNode());
            assumeFalse("NO_IMPLEMENTADA".equals(prueba.path("error").path("codigo").asText()),
                    "el núcleo todavía no tiene revisar_ruta (#76)");

            // Un accidente en la Av. Las Américas, entre el Mercado y la Universidad.
            ObjectNode accidente = JSON.createObjectNode();
            accidente.put("evento", "153");
            accidente.put("accion", "nace");
            accidente.put("tipo", "ACCIDENTE");
            accidente.putArray("ubicacion").add(-21.5324).add(-64.727);
            accidente.put("vence", OffsetDateTime.now().plusHours(1).toString());
            assertThat(motor.actualizarTrafico(accidente).path("ok").asBoolean()).isTrue();

            RevisionRutaServicio servicio = new RevisionRutaServicio(motor, JSON);
            List<String> faltan = List.of("Plaza-Mercado", "Mercado-Universidad", "Universidad-Mirador");
            RevisionRutaRespuesta r = servicio.revisar(new RevisionRutaPeticion(
                    ubicacion(-21.5355, -64.7296), faltan, ubicacion(-21.529, -64.722), null, null));
            assertThat(r.estado()).isEqualTo("ALTERNATIVA");
            assertThat(r.eventoId()).isEqualTo(153L);
            assertThat(r.costo()).isLessThan(r.costoActual());
            assertThat(r.tramos()).extracting(TramoRuta::id).first().isEqualTo("Plaza-Hospital");

            RevisionRutaRespuesta otraVez = servicio.revisar(new RevisionRutaPeticion(
                    ubicacion(-21.5355, -64.7296), faltan, ubicacion(-21.529, -64.722), null, List.of(153L)));
            assertThat(otraVez.estado()).isEqualTo("SIN_CAMBIOS");
        } finally {
            motor.detener();
        }
    }

    private static Ubicacion ubicacion(double lat, double lon) {
        return new Ubicacion(BigDecimal.valueOf(lat), BigDecimal.valueOf(lon));
    }
}
