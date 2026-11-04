package com.churoviaje.backend.motor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * T-120: los viajes de referencia de Tarija ({@code 04_imp/datos/rutas_referencia.json}) calculados a través de
 * {@link MotorDeRutas} con el núcleo C++ de verdad y la red real. La red real no está en el repositorio (D-33), así
 * que la prueba solo corre con {@code MOTOR_PATH} y {@code RED_VIAL_REAL} (la misma variable que usan las pruebas
 * del núcleo); si falta alguna, se salta.
 */
@EnabledIfEnvironmentVariable(named = "MOTOR_PATH", matches = ".+")
@EnabledIfEnvironmentVariable(named = "RED_VIAL_REAL", matches = ".+")
class RutasReferenciaNucleoRealTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static MotorDeRutas motor;
    private static JsonNode referencia;
    private static final Map<String, double[]> NODOS = new HashMap<>();

    @BeforeAll
    static void arrancar() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(Path.of("../datos/rutas_referencia.json")),
                "No existe el archivo de viajes de referencia");
        Assumptions.assumeTrue(Files.isRegularFile(Path.of(System.getenv("RED_VIAL_REAL"))),
                "No existe el archivo de red vial real");
        Assumptions.assumeTrue(Files.isRegularFile(Path.of(System.getenv("MOTOR_PATH"))),
                "No existe el ejecutable del motor C++");
        referencia = JSON.readTree(Path.of("../datos/rutas_referencia.json").toFile());
        for (JsonNode n : JSON.readTree(Path.of(System.getenv("RED_VIAL_REAL")).toFile()).path("nodos")) {
            NODOS.put(n.path("id").asText(), new double[] {n.path("lat").asDouble(), n.path("lon").asDouble()});
        }
        PropiedadesMotor propiedades = new PropiedadesMotor(true, System.getenv("MOTOR_PATH"),
                System.getenv("RED_VIAL_REAL"), Duration.ofSeconds(3), Duration.ofSeconds(60), Duration.ofSeconds(2));
        motor = new MotorDeRutas(propiedades, LanzadorNucleo.desde(propiedades), List::of, JSON);
        motor.iniciar();
        long limite = System.currentTimeMillis() + 30_000;
        while (!motor.estaListo() && System.currentTimeMillis() < limite) {
            Thread.sleep(50);
        }
        assertThat(motor.estaListo()).as("el núcleo no quedó listo").isTrue();
    }

    @AfterAll
    static void detener() {
        if (motor != null) {
            motor.detener();
        }
    }

    @Test
    void losViajesDeReferenciaPasanPorLasCallesEsperadas() {
        double tolerancia = referencia.path("tolerancia_distancia").asDouble();
        SoftAssertions verificar = new SoftAssertions();
        for (JsonNode viaje : referencia.path("viajes")) {
            String nombre = viaje.path("nombre").asText();
            JsonNode r = motor.calcularRuta(mensaje(viaje));
            assertThat(r.path("estado").asText()).as(nombre).isEqualTo("ENCONTRADA");

            JsonNode trazado = r.path("trazado");
            double[] inicio = NODOS.get(viaje.path("nodo_origen").asText());
            double[] fin = NODOS.get(viaje.path("nodo_destino").asText());
            verificar.assertThat(trazado.path(0).path(0).asDouble()).as(nombre + ": inicio").isCloseTo(inicio[0], within(1e-5));
            verificar.assertThat(trazado.path(0).path(1).asDouble()).as(nombre + ": inicio").isCloseTo(inicio[1], within(1e-5));
            JsonNode ultimo = trazado.path(trazado.size() - 1);
            verificar.assertThat(ultimo.path(0).asDouble()).as(nombre + ": fin").isCloseTo(fin[0], within(1e-5));
            verificar.assertThat(ultimo.path(1).asDouble()).as(nombre + ": fin").isCloseTo(fin[1], within(1e-5));

            List<String> calles = callesEnOrden(r.path("tramos"));
            List<String> esperadas = new ArrayList<>();
            viaje.path("calles_esperadas").forEach(c -> esperadas.add(c.asText()));
            verificar.assertThat(aparecenEnOrden(esperadas, calles))
                    .as("%s: se esperaba %s y la ruta pasa por %s", nombre, esperadas, calles).isTrue();
            viaje.path("no_debe_pasar_por").forEach(c -> verificar.assertThat(calles).as(nombre).doesNotContain(c.asText()));

            double aprox = viaje.path("distancia_aprox_m").asDouble();
            verificar.assertThat(r.path("distancia_m").asDouble()).as(nombre + ": distancia")
                    .isCloseTo(aprox, within(tolerancia * aprox));
        }
        verificar.assertAll();
    }

    private static ObjectNode mensaje(JsonNode viaje) {
        ObjectNode m = JSON.createObjectNode();
        m.set("origen", viaje.path("origen"));
        m.set("destino", viaje.path("destino"));
        m.set("paradas", viaje.has("paradas") ? viaje.path("paradas") : JSON.createArrayNode());
        return m;
    }

    /** Las calles de la ruta en orden, sin repetir la misma calle cuando sigue en el tramo siguiente. */
    private static List<String> callesEnOrden(JsonNode tramos) {
        List<String> calles = new ArrayList<>();
        for (JsonNode t : tramos) {
            String calle = t.path("calle").asText();
            if (calles.isEmpty() || !calles.get(calles.size() - 1).equals(calle)) {
                calles.add(calle);
            }
        }
        return calles;
    }

    private static boolean aparecenEnOrden(List<String> esperadas, List<String> calles) {
        int i = 0;
        for (String calle : calles) {
            if (i < esperadas.size() && calle.equals(esperadas.get(i))) {
                i++;
            }
        }
        return i == esperadas.size();
    }
}
