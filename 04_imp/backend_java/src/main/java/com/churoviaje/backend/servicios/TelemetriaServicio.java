package com.churoviaje.backend.servicios;

import com.churoviaje.backend.dominio.Conductor;
import com.churoviaje.backend.dominio.ConductorRepository;
import com.churoviaje.backend.dto.TelemetriaPeticion;
import com.churoviaje.backend.errores.ExcepcionRechazo;
import com.churoviaje.backend.motor.MotorDeRutas;
import com.churoviaje.backend.motor.MotorNoDisponibleException;
import com.churoviaje.backend.seguridad.IdentificadorRecorrido;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Caso de uso de la telemetría (CU-04, HU-13). Recibe las lecturas de un conductor, les pone un identificador
 * temporal de recorrido y se las pasa a {@link MotorDeRutas} sin guardarlas (RN-04.12, D-07). El análisis corre en
 * un hilo aparte: la app recibe «recibido» de inmediato y no espera al motor (#74). Las congestiones que informa el
 * motor las registra {@link CongestionServicio}.
 */
@Service
public class TelemetriaServicio {

    private static final Logger LOG = LoggerFactory.getLogger(TelemetriaServicio.class);
    /** Las fechas del pipe van siempre con segundos, como en el contrato. */
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final ZoneId ZONA = ZoneId.of("America/La_Paz");
    /** Envíos que pueden esperar al motor; si se llena, los más nuevos se descartan (la telemetría no se repite). */
    static final int ENVIOS_EN_ESPERA = 500;

    private final ConductorRepository conductores;
    private final IdentificadorRecorrido recorridos;
    private final MotorDeRutas motor;
    private final CongestionServicio congestiones;
    private final ObjectMapper json;
    private final ThreadPoolExecutor hilo;
    /** Envíos aceptados que todavía no terminaron de analizarse (para las pruebas). */
    private final AtomicInteger enCamino = new AtomicInteger();

    public TelemetriaServicio(ConductorRepository conductores, IdentificadorRecorrido recorridos, MotorDeRutas motor,
            CongestionServicio congestiones, ObjectMapper json) {
        this.conductores = conductores;
        this.recorridos = recorridos;
        this.motor = motor;
        this.congestiones = congestiones;
        this.json = json;
        this.hilo = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(ENVIOS_EN_ESPERA),
                tarea -> {
                    Thread t = new Thread(tarea, "telemetria");
                    t.setDaemon(true);
                    return t;
                },
                (tarea, ejecutor) -> {
                    enCamino.decrementAndGet();
                    LOG.warn("Telemetría descartada: hay {} envíos esperando al motor", ENVIOS_EN_ESPERA);
                });
    }

    /**
     * RN-04.4: solo de un conductor con sesión vigente (el JWT ya se comprobó) que sigue existiendo. Devuelve cuántas
     * lecturas recibió; el análisis queda en camino.
     */
    public int recibir(long conductorId, TelemetriaPeticion peticion) {
        Conductor conductor = conductores.buscarPorId(conductorId)
                .orElseThrow(() -> new ExcepcionRechazo(HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO",
                        "Inicia sesión para continuar."));
        ObjectNode mensaje = json.createObjectNode();
        ObjectNode grupo = mensaje.putArray("recorridos").addObject();
        grupo.put("recorrido", recorridos.de(conductorId)); // RNF-10: nada que identifique al conductor
        grupo.put("simulado", conductor.isSimulado());      // RN-04.20
        ArrayNode lecturas = grupo.putArray("lecturas");
        for (TelemetriaPeticion.Lectura l : peticion.lecturas()) {
            ObjectNode lectura = lecturas.addObject();
            lectura.set("posicion", RespuestasDelMotor.punto(json, l.ubicacion()));
            lectura.put("precision_m", l.precisionM());
            lectura.put("momento", FORMATO.format(l.momento().atZoneSameInstant(ZONA)));
        }
        enCamino.incrementAndGet();
        hilo.execute(() -> {
            try {
                analizar(mensaje);
            } finally {
                enCamino.decrementAndGet();
            }
        });
        return peticion.lecturas().size();
    }

    /** En el hilo de la telemetría: si el motor no está, las lecturas se pierden; nunca se guardan (RN-04.12). */
    void analizar(ObjectNode mensaje) {
        try {
            JsonNode respuesta = motor.analizarTelemetria(mensaje);
            RespuestasDelMotor.exigirOk(respuesta, "analizar_telemetria");
            congestiones.registrar(respuesta.path("congestiones"));
        } catch (MotorNoDisponibleException e) {
            LOG.debug("El motor no está disponible; se descartan las lecturas de este envío");
        } catch (RuntimeException e) {
            LOG.error("No se pudo analizar la telemetría: {}", e.getMessage(), e);
        }
    }

    /** Para pruebas: espera a que el hilo de la telemetría termine lo que tiene en fila. */
    public boolean esperarPendientes(long milisegundos) throws InterruptedException {
        long limite = System.currentTimeMillis() + milisegundos;
        while (System.currentTimeMillis() < limite) {
            if (enCamino.get() == 0) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    @PreDestroy
    public void detener() {
        hilo.shutdownNow();
    }
}
