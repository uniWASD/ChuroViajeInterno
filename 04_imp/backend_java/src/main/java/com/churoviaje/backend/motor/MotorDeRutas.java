package com.churoviaje.backend.motor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Unica via del backend hacia el nucleo C++ (T-095, 05_contratos.md «Backend con el motor core»).
 *
 * <p>El nucleo corre como proceso hijo y habla por la entrada y salida estandar, una linea de JSON por mensaje.
 * Atiende una peticion a la vez, asi que el wrapper las pone en fila. Cada peticion tiene como maximo
 * {@code motor.tiempo-maximo} (RN-03.15, 3 s):
 * <ul>
 *   <li>Si vence esperando en la fila, responde 503 sin tocar el nucleo.</li>
 *   <li>Si el nucleo no contesta dentro de ese plazo, contado desde que se le escribio, se lo detiene, se lo
 *       vuelve a arrancar y la peticion responde 503.</li>
 *   <li>Mientras el nucleo arranca (o si esta apagado), las operaciones responden 503 de inmediato.</li>
 * </ul>
 * Cada metodo devuelve la respuesta completa del nucleo, con su {@code ok}; un {@code ok:false} es una respuesta
 * valida del nucleo y la interpreta quien llama (por ejemplo, «punto invalido»).
 */
@Component
public class MotorDeRutas {

    private static final Logger LOG = LoggerFactory.getLogger(MotorDeRutas.class);
    private static final Logger LOG_NUCLEO = LoggerFactory.getLogger("com.churoviaje.backend.motor.nucleo");
    private static final long PAUSA_ENTRE_CONSULTAS_MS = 100;
    private static final long ESPERA_OCIOSA_MS = 200;
    private static final String FIN_DE_SALIDA = "\u0000FIN";

    private final PropiedadesMotor propiedades;
    private final LanzadorNucleo lanzador;
    private final EventosVigentesParaMotor eventosVigentes;
    private final ObjectMapper json;

    private final BlockingQueue<Peticion> fila = new LinkedBlockingQueue<>();
    private final AtomicLong ultimoId = new AtomicLong();
    private final AtomicInteger arranques = new AtomicInteger();
    private volatile Nucleo nucleo;
    private volatile boolean listo;
    private volatile boolean detenido;
    private Thread hilo;

    @Autowired
    public MotorDeRutas(PropiedadesMotor propiedades, ObjectProvider<EventosVigentesParaMotor> eventosVigentes,
            ObjectMapper json) {
        this(propiedades, propiedades.habilitado() ? LanzadorNucleo.desde(propiedades) : null,
                eventosVigentes.getIfAvailable(() -> List::of), json);
    }

    /** Para pruebas: permite poner otro lanzador (un nucleo falso). */
    public MotorDeRutas(PropiedadesMotor propiedades, LanzadorNucleo lanzador,
            EventosVigentesParaMotor eventosVigentes, ObjectMapper json) {
        this.propiedades = propiedades;
        this.lanzador = lanzador;
        this.eventosVigentes = eventosVigentes;
        this.json = json;
    }

    // ------------------------------------------------------------------ operaciones

    public JsonNode calcularRuta(ObjectNode peticion) {
        return enviar("calcular_ruta", peticion);
    }

    public JsonNode revisarRuta(ObjectNode peticion) {
        return enviar("revisar_ruta", peticion);
    }

    public JsonNode actualizarTrafico(ObjectNode peticion) {
        return enviar("actualizar_trafico", peticion);
    }

    public JsonNode analizarTelemetria(ObjectNode peticion) {
        return enviar("analizar_telemetria", peticion);
    }

    public JsonNode consultarEstado() {
        return enviar("consultar_estado", json.createObjectNode());
    }

    /** Verdadero cuando el nucleo ya cargo la red vial y recibio los eventos vigentes. */
    public boolean estaListo() {
        return listo;
    }

    /** Cuantas veces se arranco el nucleo desde que inicio el backend (para pruebas y diagnostico). */
    public int vecesArrancado() {
        return arranques.get();
    }

    // ------------------------------------------------------------------ ciclo de vida

    @PostConstruct
    public void iniciar() {
        if (lanzador == null) {
            LOG.warn("El motor esta deshabilitado (motor.habilitado=false): las operaciones responderan 503");
            return;
        }
        detenido = false;
        hilo = new Thread(this::trabajar, "motor-de-rutas");
        hilo.setDaemon(true);
        hilo.start();
    }

    @PreDestroy
    public void detener() {
        detenido = true;
        if (hilo != null) {
            hilo.interrupt();
        }
        apagarNucleo();
        Peticion pendiente;
        while ((pendiente = fila.poll()) != null) {
            pendiente.respuesta.completeExceptionally(new MotorNoDisponibleException());
        }
    }

    // ------------------------------------------------------------------ lado de quien llama

    private JsonNode enviar(String operacion, ObjectNode cuerpo) {
        if (!listo) {
            throw new MotorNoDisponibleException();
        }
        long limite = System.nanoTime() + propiedades.tiempoMaximo().toNanos();
        Peticion peticion = new Peticion(operacion, cuerpo, limite);
        fila.add(peticion);
        try {
            return esperar(peticion);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            peticion.estado.set(Peticion.CANCELADA);
            throw new MotorNoDisponibleException();
        }
    }

    private JsonNode esperar(Peticion peticion) throws InterruptedException {
        try {
            // Mientras espera en la fila, solo se espera hasta el limite.
            return peticion.respuesta.get(Math.max(0, peticion.limiteEnFila - System.nanoTime()),
                    TimeUnit.NANOSECONDS);
        } catch (TimeoutException vencio) {
            if (peticion.estado.compareAndSet(Peticion.EN_FILA, Peticion.CANCELADA)) {
                throw new MotorNoDisponibleException();
            }
            // Ya la tomo el hilo del nucleo: el mismo hilo garantiza una respuesta dentro del plazo.
            try {
                return peticion.respuesta.get(propiedades.tiempoMaximo().toNanos() + TimeUnit.SECONDS.toNanos(1),
                        TimeUnit.NANOSECONDS);
            } catch (TimeoutException | ExecutionException otro) {
                throw new MotorNoDisponibleException();
            }
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new MotorNoDisponibleException();
        }
    }

    // ------------------------------------------------------------------ hilo que habla con el nucleo

    private void trabajar() {
        while (!detenido) {
            try {
                if (nucleo == null || !nucleo.vivo()) {
                    if (nucleo != null) {
                        LOG.warn("El nucleo termino por su cuenta (codigo {}); se vuelve a arrancar",
                                nucleo.proceso.exitValue());
                    }
                    reiniciar(false);
                    continue;
                }
                Peticion peticion = fila.poll(ESPERA_OCIOSA_MS, TimeUnit.MILLISECONDS);
                if (peticion != null) {
                    atender(peticion);
                }
            } catch (InterruptedException e) {
                return;
            } catch (RuntimeException e) {
                LOG.error("Error inesperado en el hilo del motor; se reinicia el nucleo", e);
                reiniciar(true);
            }
        }
    }

    private void atender(Peticion peticion) {
        if (!peticion.estado.compareAndSet(Peticion.EN_FILA, Peticion.TOMADA)) {
            return; // quien la hizo ya se rindio
        }
        if (System.nanoTime() > peticion.limiteEnFila) {
            // Vencio esperando: no es culpa del nucleo, no se reinicia.
            peticion.respuesta.completeExceptionally(new MotorNoDisponibleException());
            return;
        }
        if (!listo) {
            peticion.respuesta.completeExceptionally(new MotorNoDisponibleException());
            return;
        }
        try {
            JsonNode respuesta = intercambiar(peticion.operacion, peticion.cuerpo, propiedades.tiempoMaximo());
            peticion.respuesta.complete(respuesta);
        } catch (FalloDelNucleo e) {
            LOG.warn("El nucleo fallo en «{}»: {}. Se reinicia.", peticion.operacion, e.getMessage());
            // Antes de avisar a quien espera: desde ese momento nadie debe ver al nucleo como listo.
            listo = false;
            peticion.respuesta.completeExceptionally(new MotorNoDisponibleException());
            reiniciar(true);
        }
    }

    /**
     * Escribe una peticion y espera la respuesta con el mismo id. El plazo se cuenta desde la escritura. Las lineas
     * con otro id (de una peticion anterior que ya se dio por perdida) se descartan.
     */
    private JsonNode intercambiar(String operacion, ObjectNode cuerpo, Duration plazo) {
        Nucleo actual = nucleo;
        long id = ultimoId.incrementAndGet();
        ObjectNode mensaje = json.createObjectNode();
        mensaje.put("id", id);
        mensaje.put("operacion", operacion);
        cuerpo.fields().forEachRemaining(campo -> {
            if (!campo.getKey().equals("id") && !campo.getKey().equals("operacion")) {
                mensaje.set(campo.getKey(), campo.getValue());
            }
        });
        long limite;
        try {
            actual.escribir(json.writeValueAsString(mensaje));
            limite = System.nanoTime() + plazo.toNanos();
        } catch (IOException e) {
            throw new FalloDelNucleo("no se pudo escribir: " + e.getMessage());
        }
        while (true) {
            String linea;
            try {
                linea = actual.lineas.poll(Math.max(0, limite - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FalloDelNucleo("interrumpido");
            }
            if (linea == null) {
                throw new FalloDelNucleo("no respondio en " + plazo.toMillis() + " ms");
            }
            if (linea.equals(FIN_DE_SALIDA)) {
                throw new FalloDelNucleo("el nucleo cerro su salida");
            }
            JsonNode respuesta;
            try {
                respuesta = json.readTree(linea);
            } catch (IOException e) {
                LOG.warn("Linea del nucleo que no es JSON, se descarta: {}", linea);
                continue;
            }
            if (respuesta != null && respuesta.path("id").asLong(-1) == id) {
                return respuesta;
            }
            LOG.warn("Respuesta con id distinto de {}, se descarta: {}", id, linea);
        }
    }

    // ------------------------------------------------------------------ arranque y reinicio

    private void reiniciar(boolean esperarAntes) {
        listo = false;
        apagarNucleo();
        if (esperarAntes) {
            pausa(propiedades.esperaReinicio().toMillis());
        }
        while (!detenido) {
            if (arrancar()) {
                return;
            }
            apagarNucleo();
            pausa(propiedades.esperaReinicio().toMillis());
        }
    }

    /** Arranca el proceso, espera la red vial y envia los eventos vigentes. */
    private boolean arrancar() {
        try {
            Process proceso = lanzador.lanzar();
            arranques.incrementAndGet();
            nucleo = new Nucleo(proceso);
            LOG.info("Nucleo arrancado, esperando que cargue la red vial");
        } catch (IOException e) {
            LOG.error("No se pudo arrancar el nucleo: {}", e.getMessage());
            return false;
        }
        long limite = System.nanoTime() + propiedades.esperaArranque().toNanos();
        try {
            while (!detenido && System.nanoTime() < limite) {
                if (!nucleo.vivo()) {
                    LOG.error("El nucleo termino mientras arrancaba (codigo {})", nucleo.proceso.exitValue());
                    return false;
                }
                // El nucleo carga la red antes de leer su entrada, asi que la primera respuesta puede tardar
                // mucho mas que 3 s: al arrancar el plazo es el de espera-arranque, no el de una peticion.
                Duration restante = Duration.ofNanos(Math.max(limite - System.nanoTime(), 1));
                JsonNode estado = intercambiar("consultar_estado", json.createObjectNode(), restante);
                if (estado.path("ok").asBoolean(false) && estado.path("red_cargada").asBoolean(false)) {
                    enviarEventosVigentes();
                    listo = true;
                    LOG.info("Nucleo listo (red vial de {})", estado.path("fecha_extracto").asText("fecha desconocida"));
                    return true;
                }
                pausa(PAUSA_ENTRE_CONSULTAS_MS);
            }
        } catch (FalloDelNucleo e) {
            LOG.error("El nucleo no respondio al arrancar: {}", e.getMessage());
            return false;
        }
        LOG.error("El nucleo no cargo la red vial en {} s", propiedades.esperaArranque().toSeconds());
        return false;
    }

    private void enviarEventosVigentes() {
        for (ObjectNode evento : eventosVigentes.eventosVigentes()) {
            JsonNode respuesta = intercambiar("actualizar_trafico", evento, propiedades.esperaArranque());
            if (!respuesta.path("ok").asBoolean(false)) {
                LOG.warn("El nucleo rechazo un evento vigente al arrancar: {}", respuesta);
            }
        }
    }

    private void apagarNucleo() {
        Nucleo anterior = nucleo;
        nucleo = null;
        listo = false;
        if (anterior != null) {
            anterior.proceso.destroyForcibly();
        }
    }

    private void pausa(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            detenido = true;
        }
    }

    // ------------------------------------------------------------------ piezas internas

    private static final class FalloDelNucleo extends RuntimeException {
        FalloDelNucleo(String mensaje) {
            super(mensaje);
        }
    }

    private static final class Peticion {
        static final int EN_FILA = 0;
        static final int TOMADA = 1;
        static final int CANCELADA = 2;

        final String operacion;
        final ObjectNode cuerpo;
        final long limiteEnFila;
        final AtomicInteger estado = new AtomicInteger(EN_FILA);
        final CompletableFuture<JsonNode> respuesta = new CompletableFuture<>();

        Peticion(String operacion, ObjectNode cuerpo, long limiteEnFila) {
            this.operacion = operacion;
            this.cuerpo = cuerpo;
            this.limiteEnFila = limiteEnFila;
        }
    }

    /** Un proceso del nucleo con los hilos que leen su salida y sus errores. */
    private static final class Nucleo {
        final Process proceso;
        final BufferedWriter entrada;
        final BlockingQueue<String> lineas = new LinkedBlockingQueue<>();

        Nucleo(Process proceso) {
            this.proceso = proceso;
            this.entrada = new BufferedWriter(new OutputStreamWriter(proceso.getOutputStream(), StandardCharsets.UTF_8));
            leerEnHilo("motor-salida", proceso, true);
            leerEnHilo("motor-errores", proceso, false);
        }

        boolean vivo() {
            return proceso.isAlive();
        }

        void escribir(String linea) throws IOException {
            entrada.write(linea);
            entrada.write('\n');
            entrada.flush();
        }

        private void leerEnHilo(String nombre, Process p, boolean salida) {
            Thread hilo = new Thread(() -> {
                try (BufferedReader lector = new BufferedReader(new InputStreamReader(
                        salida ? p.getInputStream() : p.getErrorStream(), StandardCharsets.UTF_8))) {
                    String linea;
                    while ((linea = lector.readLine()) != null) {
                        if (salida) {
                            lineas.add(linea);
                        } else {
                            LOG_NUCLEO.warn("{}", linea);
                        }
                    }
                } catch (IOException ignorada) {
                    // el proceso se cerro
                } finally {
                    if (salida) {
                        lineas.add(FIN_DE_SALIDA);
                    }
                }
            }, nombre);
            hilo.setDaemon(true);
            hilo.start();
        }
    }
}
