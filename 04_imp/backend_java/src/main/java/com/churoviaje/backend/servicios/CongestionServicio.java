package com.churoviaje.backend.servicios;

import com.churoviaje.backend.dominio.EventoRepository;
import com.churoviaje.backend.dominio.EventoVial;
import com.churoviaje.backend.dominio.Ubicacion;
import com.churoviaje.backend.dominio.catalogo.CatalogoRepository;
import com.churoviaje.backend.dominio.catalogo.TipoEvento;
import com.churoviaje.backend.motor.AvisoTrafico;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra las congestiones que el motor detecta con la telemetría (CU-04, HU-13): crea el {@code EventoVial} de
 * tipo Congestión con origen Telemetría (RN-04.10, D-04) o extiende el que ya tiene ese tramo (RN-04.11), y se lo
 * avisa al motor para que pese en las rutas con su nivel (D-14, P-12). La congestión nueva guarda el trazado de su
 * tramo, que la app dibuja a lo largo de la calle (RN-04.25, D-45).
 */
@Service
public class CongestionServicio {

    private static final Logger LOG = LoggerFactory.getLogger(CongestionServicio.class);

    private final EventoRepository eventos;
    private final CatalogoRepository catalogo;
    private final Clock reloj;
    private final ApplicationEventPublisher avisos;

    public CongestionServicio(EventoRepository eventos, CatalogoRepository catalogo, Clock reloj,
            ApplicationEventPublisher avisos) {
        this.eventos = eventos;
        this.catalogo = catalogo;
        this.reloj = reloj;
        this.avisos = avisos;
    }

    /**
     * Procesa la lista {@code congestiones} de una respuesta de {@code analizar_telemetria}, en una sola transacción.
     * Los avisos al motor salen recién cuando quedó guardada (TraficoDelMotor escucha al confirmar).
     *
     * @return cuántas congestiones nacieron o se extendieron
     */
    @Transactional
    public int registrar(JsonNode congestiones) {
        if (!congestiones.isArray() || congestiones.isEmpty()) {
            return 0;
        }
        Instant ahora = reloj.instant();
        TipoEvento tipo = catalogo.buscarTipoDeEvento(EventoVial.TIPO_CONGESTION)
                .orElseThrow(() -> new IllegalStateException("El catálogo no tiene el tipo CONGESTION (V1)"));
        int registradas = 0;
        for (JsonNode c : congestiones) {
            String tramo = c.path("tramo").asText("");
            String nivel = c.path("nivel").asText("");
            boolean simulado = c.path("simulado").asBoolean(false);
            JsonNode punto = c.path("punto");
            if (tramo.isEmpty() || !punto.isArray() || punto.size() != 2) {
                LOG.warn("El motor informó una congestión sin tramo o sin punto; se ignora: {}", c);
                continue;
            }
            Ubicacion ubicacion = new Ubicacion(BigDecimal.valueOf(punto.path(0).asDouble()),
                    BigDecimal.valueOf(punto.path(1).asDouble()));
            registrar(tipo, tramo, ubicacion, trazado(c.path("trazado")), nivel, simulado, ahora);
            registradas++;
        }
        return registradas;
    }

    /** RN-04.25: los puntos {@code [lat, lon]} del trazado; si falta o algún punto no sirve, la congestión va sin él. */
    static List<Ubicacion> trazado(JsonNode trazado) {
        if (!trazado.isArray() || trazado.size() < 2) {
            return List.of();
        }
        List<Ubicacion> puntos = new ArrayList<>();
        for (JsonNode p : trazado) {
            if (!p.isArray() || p.size() != 2 || !p.path(0).isNumber() || !p.path(1).isNumber()) {
                LOG.warn("El motor informó un trazado con un punto inválido; la congestión va sin trazado: {}", trazado);
                return List.of();
            }
            puntos.add(new Ubicacion(p.path(0).decimalValue(), p.path(1).decimalValue()));
        }
        return List.copyOf(puntos);
    }

    private void registrar(TipoEvento tipo, String tramo, Ubicacion ubicacion, List<Ubicacion> trazado, String nivel,
            boolean simulado, Instant ahora) {
        Optional<EventoVial> activa = eventos.buscarCongestionActivaEnTramo(tramo, simulado);
        if (activa.isPresent() && activa.get().estaVigente(ahora)) {
            // RN-04.11: no se crea otra; se mueve el momento de la que ya está, y su nivel si cambió.
            EventoVial extendida = activa.get().extenderCongestion(nivel, ahora);
            eventos.actualizarCongestion(extendida, ahora);
            avisos.publishEvent(AvisoTrafico.cambiaVencimiento(extendida));
            return;
        }
        if (activa.isPresent()) {
            // Ya venció, pero la tarea periódica todavía no la marcó (RN-02.4): se cierra y nace una nueva.
            eventos.marcarVencido(activa.get().getId(), ahora);
            avisos.publishEvent(AvisoTrafico.vence(activa.get().getId()));
        }
        EventoVial nueva = EventoVial.detectarCongestion(tipo, ubicacion, tramo, trazado, nivel, simulado, ahora);
        EventoVial guardada = nueva.conId(eventos.guardarNuevo(nueva));
        avisos.publishEvent(AvisoTrafico.nace(guardada));
    }
}
