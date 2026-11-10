package com.churoviaje.backend.motor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.churoviaje.backend.config.LectorCoberturaGeoJson;
import com.churoviaje.backend.dominio.Ubicacion;
import com.churoviaje.backend.dto.RutaPeticion;
import com.churoviaje.backend.dto.RutaRespuesta;
import com.churoviaje.backend.servicios.RutaServicio;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * HU-05 (#65): {@code RutaServicio} con el núcleo C++ de verdad. Como {@link MotorDeRutasNucleoRealTest}, solo
 * corre con {@code MOTOR_PATH} y {@code RED_VIAL_PATH}. Pide la ruta a lo largo del tramo que empieza más cerca de
 * la plaza principal, así que sirve con cualquier red vial de Tarija (la de prueba o la real).
 */
@EnabledIfEnvironmentVariable(named = "MOTOR_PATH", matches = ".+")
@EnabledIfEnvironmentVariable(named = "RED_VIAL_PATH", matches = ".+")
class RutaConNucleoRealTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void calculaUnaRutaConElNucleoReal() throws Exception {
        JsonNode red = JSON.readTree(Path.of(System.getenv("RED_VIAL_PATH")).toFile());
        JsonNode tramo = null;
        double mejor = Double.MAX_VALUE;
        for (JsonNode t : red.path("tramos")) {
            JsonNode inicio = t.path("geometria").path(0);
            double d = Math.pow(inicio.path(0).asDouble() + 21.5355, 2) + Math.pow(inicio.path(1).asDouble() + 64.7296, 2);
            if (d < mejor) {
                mejor = d;
                tramo = t;
            }
        }
        assertThat(tramo).as("la red vial no tiene tramos").isNotNull();
        Ubicacion desde = ubicacion(tramo.path("geometria").path(0));
        Ubicacion hasta = ubicacion(tramo.path("geometria").path(tramo.path("geometria").size() - 1));

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
            RutaServicio servicio = new RutaServicio(new LectorCoberturaGeoJson()
                    .leer(JSON.readTree(Path.of("../datos/mancha_urbana_tarija.geojson").toFile())), motor, JSON);

            long inicio = System.nanoTime();
            RutaRespuesta r = servicio.calcular(new RutaPeticion(desde, hasta, null));
            long ms = (System.nanoTime() - inicio) / 1_000_000;

            assertThat(r.estado()).isEqualTo("ENCONTRADA");
            assertThat(r.distanciaM()).isPositive();
            assertThat(r.tramos()).isNotEmpty();
            assertThat(r.trazado().get(0).latitud()).isCloseTo(desde.latitud().doubleValue(), within(1e-5));
            assertThat(ms).as("RNF-02: menos de 3 segundos").isLessThan(3000);
        } finally {
            motor.detener();
        }
    }

    private static Ubicacion ubicacion(JsonNode par) {
        return new Ubicacion(BigDecimal.valueOf(par.path(0).asDouble()), BigDecimal.valueOf(par.path(1).asDouble()));
    }
}
