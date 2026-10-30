package com.churoviaje.backend.motor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class MotorDeRutas {
    private static final Logger log = LoggerFactory.getLogger(MotorDeRutas.class);
    private static final int TIMEOUT_SEGUNDOS = 3;

    @Value("${motor.ruta-ejecutable}")
    private String rutaEjecutable;

    @Value("${motor.ruta-red-vial}")
    private String rutaRedVial;

    private Process procesoMotor;
    private BufferedWriter escritor;
    private BufferedReader lector;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Cola concurrente para procesar de a una petición
    private final BlockingQueue<PeticionMotor> filaPeticiones = new LinkedBlockingQueue<>();
    private final ExecutorService hiloProcesador = Executors.newSingleThreadExecutor();

    @PostConstruct
    public void iniciar() {
        arrancarProceso();
        hiloProcesador.submit(this::procesarFilaContinua);
    }

    private synchronized void arrancarProceso() {
        try {
            if (procesoMotor != null && procesoMotor.isAlive()) {
                procesoMotor.destroyForcibly();
            }

            log.info("Iniciando motor: {}", rutaEjecutable);
            
            // Adaptación para correr el simulador en Java y evitar errores de Python
            ProcessBuilder pb;
            if (rutaEjecutable.endsWith(".py")) {
                pb = new ProcessBuilder("py", rutaEjecutable, rutaRedVial);
            } else if (rutaEjecutable.endsWith(".java")) {
                // Obtiene la ruta absoluta del JDK actual para ejecutar el archivo .java
                String javaBin = System.getProperty("java.home") + java.io.File.separator + "bin" + java.io.File.separator + "java";
                pb = new ProcessBuilder(javaBin, rutaEjecutable, rutaRedVial);
            } else {
                pb = new ProcessBuilder(rutaEjecutable, rutaRedVial);
            }

            pb.redirectErrorStream(false);
            procesoMotor = pb.start();

            escritor = new BufferedWriter(new OutputStreamWriter(procesoMotor.getOutputStream()));
            lector = new BufferedReader(new InputStreamReader(procesoMotor.getInputStream()));

            new Thread(() -> {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(procesoMotor.getErrorStream()))) {
                    String linea;
                    while ((linea = errorReader.readLine()) != null) {
                        log.error("[Motor C++] {}", linea);
                    }
                } catch (IOException ignored) {}
            }).start();

            verificarEstadoInicial();

        } catch (Exception e) {
            log.error("Fallo crítico al arrancar el motor", e);
        }
    }

    private void verificarEstadoInicial() {
        try {
            JsonNode respuesta = enviarAlMotorInmediato("consultarEstado", objectMapper.createObjectNode());
            if (respuesta != null && "cargada".equals(respuesta.path("estado").asText())) {
                log.info("Red vial cargada correctamente en el motor.");
            }
        } catch (Exception e) {
            log.error("El motor no reportó estado válido al iniciar.");
        }
    }

    public JsonNode consultarEstado() {
        return encolarPeticion("consultarEstado", objectMapper.createObjectNode());
    }
    
    public JsonNode calcularRuta(ObjectNode datos) {
        return encolarPeticion("calcularRuta", datos);
    }

    public JsonNode revisarRuta(ObjectNode datos) {
        return encolarPeticion("revisarRuta", datos);
    }

    public JsonNode actualizarTrafico(ObjectNode datos) {
        return encolarPeticion("actualizarTrafico", datos);
    }

    public JsonNode analizarTelemetria(ObjectNode datos) {
        return encolarPeticion("analizarTelemetria", datos);
    }

    private JsonNode encolarPeticion(String operacion, ObjectNode cuerpo) {
        String id = UUID.randomUUID().toString();
        cuerpo.put("id", id);
        
        // Modificación clave: Respetar las operaciones de simulación de los tests
        if (!cuerpo.has("operacion") || !cuerpo.get("operacion").asText().startsWith("simular_")) {
            cuerpo.put("operacion", operacion);
        }

        CompletableFuture<JsonNode> futuro = new CompletableFuture<>();
        PeticionMotor peticion = new PeticionMotor(id, cuerpo, futuro);

        filaPeticiones.offer(peticion);

        try {
            return futuro.get(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            if (filaPeticiones.remove(peticion)) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "motor no disponible");
            }
            log.error("El motor excedió los 3s procesando la petición {}. Reiniciando...", id);
            arrancarProceso();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "motor no disponible");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del motor");
        }
    }

    private void procesarFilaContinua() {
        while (!Thread.currentThread().isInterrupted()) {
            PeticionMotor peticion = null;
            try {
                peticion = filaPeticiones.take();
                
                if (peticion.estaVencida()) {
                    peticion.futuro.completeExceptionally(new TimeoutException());
                    continue;
                }

                if (procesoMotor == null || !procesoMotor.isAlive()) {
                    arrancarProceso();
                }

                JsonNode respuesta = enviarAlMotorInmediato(peticion.cuerpo.path("operacion").asText(), peticion.cuerpo);
                peticion.futuro.complete(respuesta);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error al procesar petición encolada", e);
                if (peticion != null) {
                    peticion.futuro.completeExceptionally(e);
                }
            }
        }
    }

    private synchronized JsonNode enviarAlMotorInmediato(String operacion, ObjectNode cuerpo) throws IOException {
        String jsonEnvio = objectMapper.writeValueAsString(cuerpo);
        escritor.write(jsonEnvio);
        escritor.newLine();
        escritor.flush();

        String respuestaMotor = lector.readLine();
        if (respuestaMotor == null) {
            throw new IOException("El motor cerró la conexión inesperadamente");
        }
        
        return objectMapper.readTree(respuestaMotor);
    }

    @PreDestroy
    public void detener() {
        if (procesoMotor != null && procesoMotor.isAlive()) {
            procesoMotor.destroy();
        }
        hiloProcesador.shutdownNow();
    }

    private static class PeticionMotor {
        final String id;
        final ObjectNode cuerpo;
        final CompletableFuture<JsonNode> futuro;
        final long instanteCreacion;

        PeticionMotor(String id, ObjectNode cuerpo, CompletableFuture<JsonNode> futuro) {
            this.id = id;
            this.cuerpo = cuerpo;
            this.futuro = futuro;
            this.instanteCreacion = Instant.now().toEpochMilli();
        }

        boolean estaVencida() {
            return (Instant.now().toEpochMilli() - instanteCreacion) >= (TIMEOUT_SEGUNDOS * 1000);
        }
    }
}