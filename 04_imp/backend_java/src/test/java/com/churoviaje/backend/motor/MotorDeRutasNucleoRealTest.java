package com.churoviaje.backend.motor;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Prueba con el núcleo C++ de verdad. Solo corre si se definen {@code MOTOR_PATH} (el ejecutable compilado) y
 * {@code RED_VIAL_PATH} (un archivo de red vial, por ejemplo {@code 04_imp/motor_cpp/tests/datos/red_prueba_plaza.json}).
 * Sin esas variables se omite, así que no obliga a nadie a compilar el C++.
 */
@EnabledIfEnvironmentVariable(named = "MOTOR_PATH", matches = ".+")
@EnabledIfEnvironmentVariable(named = "RED_VIAL_PATH", matches = ".+")
class MotorDeRutasNucleoRealTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void elWrapperArrancaElNucleoRealYConsultaElEstado() throws Exception {
        PropiedadesMotor propiedades = new PropiedadesMotor(true, System.getenv("MOTOR_PATH"),
                System.getenv("RED_VIAL_PATH"), Duration.ofSeconds(3), Duration.ofSeconds(60), Duration.ofSeconds(2));
        MotorDeRutas motor = new MotorDeRutas(propiedades, LanzadorNucleo.desde(propiedades), List::of, JSON);
        try {
            motor.iniciar();
            long limite = System.currentTimeMillis() + 30_000;
            while (!motor.estaListo() && System.currentTimeMillis() < limite) {
                Thread.sleep(50);
            }
            assertThat(motor.estaListo()).as("el núcleo real debía cargar la red y quedar listo").isTrue();

            JsonNode estado = motor.consultarEstado();
            assertThat(estado.path("ok").asBoolean()).isTrue();
            assertThat(estado.path("red_cargada").asBoolean()).isTrue();
            assertThat(estado.path("fecha_extracto").asText()).matches("\\d{4}-\\d{2}-\\d{2}");
            assertThat(estado.path("nodos").asInt()).isPositive();
            assertThat(estado.path("tramos").asInt()).isPositive();

            // Las operaciones que aún no existen en el núcleo responden «ok:false», no 503.
            ObjectNode ruta = JSON.createObjectNode();
            JsonNode respuesta = motor.calcularRuta(ruta);
            assertThat(respuesta.path("ok").asBoolean()).isFalse();
            assertThat(respuesta.path("error").path("codigo").asText()).isEqualTo("NO_IMPLEMENTADA");
            assertThat(motor.vecesArrancado()).isEqualTo(1);
        } finally {
            motor.detener();
        }
    }
}
