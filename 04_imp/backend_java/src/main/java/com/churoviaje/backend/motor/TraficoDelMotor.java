package com.churoviaje.backend.motor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Mantiene al dia el trafico del motor (T-099, D-14). Cada vez que un evento nace, cambia su vencimiento, se
 * retira o vence, le pasa el aviso al nucleo con {@code actualizar_trafico}.
 *
 * <p>El aviso sale despues de que el cambio quedo guardado en la base, y en un hilo propio, asi que la peticion
 * del conductor no espera al nucleo. Si el nucleo no lo recibe (esta arrancando o no responde), el aviso queda
 * pendiente y se reintenta: nunca se pierde. Si llegan dos avisos del mismo evento antes de enviarse, vale el
 * ultimo. Cuando el nucleo se reinicia, el wrapper le envia todos los eventos vigentes
 * ({@link EventosVigentesDelMotor}).
 */
@Component
public class TraficoDelMotor {

    private static final Logger LOG = LoggerFactory.getLogger(TraficoDelMotor.class);

    private final MotorDeRutas motor;
    private final ObjectMapper json;
    private final boolean habilitado;
    private final Map<Long, AvisoTrafico> pendientes = new LinkedHashMap<>();
    private final ReentrantLock enviando = new ReentrantLock();
    private final ExecutorService hilo = Executors.newSingleThreadExecutor(tarea -> {
        Thread t = new Thread(tarea, "trafico-del-motor");
        t.setDaemon(true);
        return t;
    });

    public TraficoDelMotor(MotorDeRutas motor, ObjectMapper json, PropiedadesMotor propiedades) {
        this.motor = motor;
        this.json = json;
        this.habilitado = propiedades.habilitado();
    }

    /** Recibe el aviso cuando la transaccion que cambio el evento ya se confirmo. */
    @TransactionalEventListener(fallbackExecution = true)
    public void alCambiarUnEvento(AvisoTrafico aviso) {
        if (!habilitado) {
            return; // sin nucleo no hay a quien avisar; al habilitarlo, recibe los vigentes al arrancar
        }
        synchronized (pendientes) {
            pendientes.remove(aviso.eventoId()); // el aviso nuevo va al final de la fila
            pendientes.put(aviso.eventoId(), aviso);
        }
        hilo.submit(this::enviarPendientes);
    }

    /** Envia los avisos pendientes en orden; los que el nucleo no recibio quedan para el siguiente intento. */
    @Scheduled(fixedDelayString = "${churoviaje.motor.reintento-trafico-ms:5000}")
    public void enviarPendientes() {
        enviando.lock();
        try {
            for (AvisoTrafico aviso : copiaDePendientes()) {
                if (!enviar(aviso)) {
                    return; // el nucleo no esta disponible: los demas esperan al siguiente intento
                }
                synchronized (pendientes) {
                    pendientes.remove(aviso.eventoId(), aviso);
                }
            }
        } finally {
            enviando.unlock();
        }
    }

    /** Cuantos avisos esperan al nucleo (para pruebas y diagnostico). */
    public int cantidadPendiente() {
        synchronized (pendientes) {
            return pendientes.size();
        }
    }

    /** Devuelve falso si hay que reintentar; un aviso que el nucleo rechaza por mal formado no se reintenta. */
    private boolean enviar(AvisoTrafico aviso) {
        try {
            JsonNode respuesta = motor.actualizarTrafico(aviso.aMensaje(json));
            if (respuesta.path("ok").asBoolean(false)) {
                return true;
            }
            String codigo = respuesta.path("error").path("codigo").asText("");
            if ("PETICION_INVALIDA".equals(codigo)) {
                LOG.error("El nucleo rechazo el aviso de trafico del evento {}: {}", aviso.eventoId(), respuesta);
                return true;
            }
            LOG.warn("El nucleo no tomo el aviso de trafico del evento {} ({}); se reintenta", aviso.eventoId(), codigo);
            return false;
        } catch (MotorNoDisponibleException e) {
            LOG.debug("El nucleo no esta disponible; el aviso del evento {} queda pendiente", aviso.eventoId());
            return false;
        } catch (RuntimeException e) {
            LOG.warn("No se pudo avisar al nucleo del evento {}: {}; se reintenta", aviso.eventoId(), e.getMessage());
            return false;
        }
    }

    private List<AvisoTrafico> copiaDePendientes() {
        synchronized (pendientes) {
            return new ArrayList<>(pendientes.values());
        }
    }

    @PreDestroy
    public void detener() {
        hilo.shutdownNow();
    }
}
