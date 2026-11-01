package com.churoviaje.backend.motor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.churoviaje.backend.errores.ExcepcionRechazo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Pruebas del wrapper con un nucleo falso en Java (CP-41 a CP-44). No necesitan Docker. */
class MotorDeRutasTest {

    private static final Path NUCLEO_FALSO = Path.of("src/test/resources/nucleo_falso/NucleoFalso.java");
    private static final ObjectMapper JSON = new ObjectMapper();

    private MotorDeRutas motor;

    @AfterEach
    void apagar() {
        if (motor != null) {
            motor.detener();
        }
    }

    private static LanzadorNucleo nucleoFalso(String... opciones) {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return () -> {
            List<String> comando = new ArrayList<>(List.of(java, NUCLEO_FALSO.toString()));
            comando.addAll(List.of(opciones));
            return new ProcessBuilder(comando).start();
        };
    }

    private static PropiedadesMotor propiedades(Duration tiempoMaximo) {
        return new PropiedadesMotor(true, "nucleo", "red.json", tiempoMaximo, Duration.ofSeconds(30),
                Duration.ofMillis(200));
    }

    private MotorDeRutas iniciar(Duration tiempoMaximo, EventosVigentesParaMotor eventos, String... opciones)
            throws InterruptedException {
        motor = new MotorDeRutas(propiedades(tiempoMaximo), nucleoFalso(opciones), eventos, JSON);
        motor.iniciar();
        esperarListo();
        return motor;
    }

    private void esperarListo() throws InterruptedException {
        long limite = System.currentTimeMillis() + 30_000;
        while (!motor.estaListo() && System.currentTimeMillis() < limite) {
            Thread.sleep(50);
        }
        assertThat(motor.estaListo()).as("el nucleo falso debia quedar listo").isTrue();
    }

    private static ObjectNode cuerpo(String campo, Object valor) {
        ObjectNode nodo = JSON.createObjectNode();
        if (valor instanceof Boolean b) {
            nodo.put(campo, b);
        } else if (valor instanceof Integer i) {
            nodo.put(campo, i);
        }
        return nodo;
    }

    private static void assertMotorNoDisponible(Runnable accion) {
        assertThatThrownBy(accion::run)
                .isInstanceOfSatisfying(ExcepcionRechazo.class, e -> {
                    assertThat(e.getEstadoHttp()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getCodigo()).isEqualTo("MOTOR_NO_DISPONIBLE");
                });
    }

    @Test
    void cp41_consultarEstadoDevuelveQueLaRedEstaCargada() throws Exception {
        iniciar(Duration.ofSeconds(3), List::of);

        JsonNode estado = motor.consultarEstado();

        assertThat(estado.path("ok").asBoolean()).isTrue();
        assertThat(estado.path("red_cargada").asBoolean()).isTrue();
        assertThat(estado.path("fecha_extracto").asText()).isEqualTo("2026-10-01");
    }

    @Test
    void esperaAQueElNucleoCarguePlaRedAntesDeAtender() throws Exception {
        iniciar(Duration.ofSeconds(3), List::of, "--consultas-sin-red=3");

        assertThat(motor.consultarEstado().path("red_cargada").asBoolean()).isTrue();
        assertThat(motor.vecesArrancado()).isEqualTo(1);
    }

    @Test
    void conElNucleoSinArrancarLasOperacionesResponden503() {
        motor = new MotorDeRutas(propiedades(Duration.ofSeconds(3)), nucleoFalso(), List::of, JSON);

        assertMotorNoDisponible(() -> motor.consultarEstado());
        assertMotorNoDisponible(() -> motor.calcularRuta(JSON.createObjectNode()));
    }

    @Test
    void deshabilitadoNoArrancaNingunProceso() {
        PropiedadesMotor apagado = new PropiedadesMotor(false, null, null, Duration.ofSeconds(3),
                Duration.ofSeconds(30), Duration.ofSeconds(2));
        motor = new MotorDeRutas(apagado, null, List::of, JSON);
        motor.iniciar();

        assertThat(motor.estaListo()).isFalse();
        assertMotorNoDisponible(() -> motor.consultarEstado());
        assertThat(motor.vecesArrancado()).isZero();
    }

    @Test
    void lasCincoOperacionesLlegaranAlNucleoConSuNombre() throws Exception {
        iniciar(Duration.ofSeconds(3), List::of);
        ObjectNode vacio = JSON.createObjectNode();

        assertThat(motor.calcularRuta(vacio).path("operacion").asText()).isEqualTo("calcular_ruta");
        assertThat(motor.revisarRuta(vacio).path("operacion").asText()).isEqualTo("revisar_ruta");
        assertThat(motor.analizarTelemetria(vacio).path("operacion").asText()).isEqualTo("analizar_telemetria");
        assertThat(motor.actualizarTrafico(vacio).path("ok").asBoolean()).isTrue();
    }

    @Test
    void soloAceptaLaRespuestaConElMismoId() throws Exception {
        iniciar(Duration.ofSeconds(3), List::of);

        JsonNode respuesta = motor.calcularRuta(cuerpo("id_equivocado", true));

        assertThat(respuesta.path("intruso").isMissingNode()).isTrue();
        assertThat(respuesta.path("operacion").asText()).isEqualTo("calcular_ruta");
    }

    @Test
    void alArrancarEnviaLosEventosVigentes() throws Exception {
        ObjectNode evento = JSON.createObjectNode().put("evento", "uno");
        ObjectNode otro = JSON.createObjectNode().put("evento", "dos");
        iniciar(Duration.ofSeconds(3), () -> List.of(evento, otro));

        // el nucleo falso cuenta cuantos actualizar_trafico recibio: los dos de arranque mas este
        JsonNode respuesta = motor.actualizarTrafico(JSON.createObjectNode());

        assertThat(respuesta.path("eventos_recibidos").asInt()).isEqualTo(3);
    }

    @Test
    void cp42_siElNucleoNoRespondeCortaAlPlazoLoReiniciaYLaSiguienteFunciona() throws Exception {
        iniciar(Duration.ofSeconds(1), List::of);
        assertThat(motor.vecesArrancado()).isEqualTo(1);

        long inicio = System.nanoTime();
        assertMotorNoDisponible(() -> motor.calcularRuta(cuerpo("colgar", true)));
        long ms = Duration.ofNanos(System.nanoTime() - inicio).toMillis();

        assertThat(ms).as("corta cerca del plazo de 1 s").isBetween(900L, 1900L);
        esperarListo();
        assertThat(motor.vecesArrancado()).isEqualTo(2);
        assertThat(motor.calcularRuta(JSON.createObjectNode()).path("ok").asBoolean()).isTrue();
    }

    @Test
    void cp43_lasPeticionesQueVencenEnLaFilaRespondenSinReiniciarElNucleo() throws Exception {
        iniciar(Duration.ofSeconds(2), List::of);
        ExecutorService hilos = Executors.newFixedThreadPool(50);
        try {
            List<Callable<Boolean>> tareas = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                // cada una tarda 150 ms en el nucleo: 50 juntas necesitan 7,5 s y el plazo es de 2 s
                tareas.add(() -> {
                    try {
                        motor.calcularRuta(cuerpo("lento", 150));
                        return true;
                    } catch (ExcepcionRechazo e) {
                        assertThat(e.getCodigo()).isEqualTo("MOTOR_NO_DISPONIBLE");
                        return false;
                    }
                });
            }
            int atendidas = 0;
            int rechazadas = 0;
            for (Future<Boolean> resultado : hilos.invokeAll(tareas)) {
                if (resultado.get()) {
                    atendidas++;
                } else {
                    rechazadas++;
                }
            }

            assertThat(atendidas).isBetween(5, 20);
            assertThat(rechazadas).isGreaterThan(25);
            assertThat(motor.vecesArrancado()).as("no se reinicio").isEqualTo(1);
            assertThat(motor.calcularRuta(JSON.createObjectNode()).path("ok").asBoolean()).isTrue();
        } finally {
            hilos.shutdownNow();
        }
    }

    @Test
    void cp44_siElProcesoMuereElWrapperLoVuelveAArrancarSolo() throws Exception {
        iniciar(Duration.ofSeconds(2), List::of);

        assertMotorNoDisponible(() -> motor.calcularRuta(cuerpo("morir", true)));
        esperarListo();

        assertThat(motor.vecesArrancado()).isEqualTo(2);
        assertThat(motor.consultarEstado().path("red_cargada").asBoolean()).isTrue();
    }

    @Test
    void elComandoDelNucleoRealLlevaLaRutaDeLaRed() {
        PropiedadesMotor p = new PropiedadesMotor(true, "/opt/churoviaje/motor_nucleo", "/datos/red_vial_tarija.json",
                Duration.ofSeconds(3), Duration.ofSeconds(60), Duration.ofSeconds(2));

        assertThat(LanzadorNucleo.comando(p))
                .containsExactly("/opt/churoviaje/motor_nucleo", "--red", "/datos/red_vial_tarija.json");
    }

    @Test
    void habilitadoSinRutasNoArranca() {
        assertThatThrownBy(() -> new PropiedadesMotor(true, "", null, Duration.ofSeconds(3), Duration.ofSeconds(60),
                Duration.ofSeconds(2))).isInstanceOf(IllegalStateException.class);
    }
}
