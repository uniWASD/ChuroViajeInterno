package com.churoviaje.backend.servicios;

import com.churoviaje.backend.config.PropiedadesSimulacion;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Registro de cada prueba de carga, por su identificador (RN-07.10, RN-07.11): cuándo entró el primer usuario
 * fantasma, cuántos entraron, cuántas peticiones hicieron, cuántas fallaron o fueron lentas, si el backend se saturó y
 * cuándo llegó la última petición. Cada línea va al registro del backend con el prefijo {@code [prueba-carga]} y, si
 * está configurado, también a un archivo propio que no se borra a los 14 días como los demás registros (T-103).
 * Ninguna línea dice nada de un conductor: solo números de la prueba.
 */
@Component
public class RegistroPruebasCarga {

    private static final Logger LOG = LoggerFactory.getLogger("churoviaje.pruebas-carga");

    /** Una prueba sin peticiones durante este tiempo se da por terminada y se escribe su resumen. */
    static final Duration INACTIVIDAD = Duration.ofMinutes(2);
    /** RN-03.15: más de 3 segundos ya es una respuesta lenta. */
    static final Duration LENTA = Duration.ofSeconds(3);

    /** Lo que se sabe de una prueba de carga hasta ahora. */
    public record Resumen(String prueba, Instant primeraEntrada, Instant ultimaPeticion, int usuarios,
            long peticiones, long errores, long saturadas, long lentas, long maximaMs) {}

    private static final class Prueba {
        final String id;
        final Instant primeraEntrada;
        final AtomicReference<Instant> ultimaPeticion;
        final Set<Integer> usuarios = ConcurrentHashMap.newKeySet();
        final AtomicLong peticiones = new AtomicLong();
        final AtomicLong errores = new AtomicLong();
        final AtomicLong saturadas = new AtomicLong();
        final AtomicLong lentas = new AtomicLong();
        final AtomicLong maximaMs = new AtomicLong();
        final AtomicBoolean avisoSaturacion = new AtomicBoolean();

        Prueba(String id, Instant ahora) {
            this.id = id;
            this.primeraEntrada = ahora;
            this.ultimaPeticion = new AtomicReference<>(ahora);
        }

        Resumen resumen() {
            return new Resumen(id, primeraEntrada, ultimaPeticion.get(), usuarios.size(), peticiones.get(),
                    errores.get(), saturadas.get(), lentas.get(), maximaMs.get());
        }
    }

    private final Map<String, Prueba> pruebas = new ConcurrentHashMap<>();
    private final Clock reloj;
    private final Path archivo;
    private final AtomicBoolean avisoArchivo = new AtomicBoolean();

    public RegistroPruebasCarga(Clock reloj, PropiedadesSimulacion propiedades) {
        this.reloj = reloj;
        this.archivo = propiedades.registro().isEmpty() ? null : Path.of(propiedades.registro());
    }

    /** Un usuario fantasma obtuvo su sesión de prueba (RN-07.4). */
    public void usuarioEntro(String prueba, int usuario) {
        Instant ahora = reloj.instant();
        Prueba datos = pruebas.computeIfAbsent(prueba, id -> {
            anotar("Prueba %s: entró el primer usuario fantasma a las %s".formatted(id, ahora));
            return new Prueba(id, ahora);
        });
        datos.usuarios.add(usuario);
        datos.ultimaPeticion.set(ahora);
    }

    /**
     * Una petición de un usuario fantasma terminó con {@code estadoHttp} después de {@code duracionMs}. Un 503 cuenta
     * como saturación (RN-07.10): el backend baja su rendimiento y lo deja escrito, sin que sea un fallo del proceso.
     */
    public void peticion(String prueba, int estadoHttp, long duracionMs) {
        Instant ahora = reloj.instant();
        Prueba datos = pruebas.computeIfAbsent(prueba, id -> new Prueba(id, ahora));
        datos.peticiones.incrementAndGet();
        datos.ultimaPeticion.set(ahora);
        datos.maximaMs.accumulateAndGet(duracionMs, Math::max);
        if (duracionMs > LENTA.toMillis()) {
            datos.lentas.incrementAndGet();
        }
        if (estadoHttp >= 500) {
            datos.errores.incrementAndGet();
        }
        if (estadoHttp == 503) {
            datos.saturadas.incrementAndGet();
            if (datos.avisoSaturacion.compareAndSet(false, true)) {
                anotar("Prueba %s: el backend empezó a responder 503 a las %s (saturación)".formatted(prueba, ahora));
            }
        }
    }

    /** Para las pruebas y el resumen: lo que se sabe de una prueba de carga que sigue activa. */
    public Optional<Resumen> resumen(String prueba) {
        return Optional.ofNullable(pruebas.get(prueba)).map(Prueba::resumen);
    }

    /**
     * Cada minuto escribe el avance de las pruebas activas y el resumen final de las que llevan
     * {@link #INACTIVIDAD} sin peticiones. Las pruebas automáticas la llaman a mano.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void informar() {
        Instant ahora = reloj.instant();
        for (Prueba datos : pruebas.values()) {
            Resumen r = datos.resumen();
            boolean termino = Duration.between(r.ultimaPeticion(), ahora).compareTo(INACTIVIDAD) >= 0;
            anotar(String.format("Prueba %s: %s usuarios=%d peticiones=%d errores=%d saturadas=%d lentas=%d "
                    + "maxima_ms=%d primera_entrada=%s ultima_peticion=%s", r.prueba(),
                    termino ? "terminó" : "en curso", r.usuarios(), r.peticiones(), r.errores(), r.saturadas(),
                    r.lentas(), r.maximaMs(), r.primeraEntrada(), r.ultimaPeticion()));
            if (termino) {
                pruebas.remove(datos.id, datos);
            }
        }
    }

    /** Escribe una línea en el registro del backend y, si hay archivo, también allí. */
    void anotar(String linea) {
        LOG.info("[prueba-carga] {}", linea);
        if (archivo == null) {
            return;
        }
        synchronized (this) {
            try {
                Files.writeString(archivo, reloj.instant() + " " + linea + System.lineSeparator(),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                if (avisoArchivo.compareAndSet(false, true)) {
                    LOG.warn("No se pudo escribir el registro de pruebas de carga en {}: {}", archivo, e.getMessage());
                }
            }
        }
    }
}
