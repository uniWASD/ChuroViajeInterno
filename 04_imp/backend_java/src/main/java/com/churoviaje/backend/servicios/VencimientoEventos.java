package com.churoviaje.backend.servicios;

import com.churoviaje.backend.motor.AvisoTrafico;
import com.churoviaje.backend.repositorios.EventoVialRepositorio;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * RN-02.4: cada minuto marca como Vencido todo evento que supero su vigencia. La consulta de eventos no depende de
 * esta tarea, porque calcula la vigencia en cada pedido: la tarea deja la base al dia.
 */
@Component
public class VencimientoEventos {

    private static final Logger LOG = LoggerFactory.getLogger(VencimientoEventos.class);

    private final EventoVialRepositorio eventos;
    private final Clock reloj;
    private final ApplicationEventPublisher avisos;

    public VencimientoEventos(EventoVialRepositorio eventos, Clock reloj, ApplicationEventPublisher avisos) {
        this.eventos = eventos;
        this.reloj = reloj;
        this.avisos = avisos;
    }

    /** Devuelve los identificadores de los eventos que vencio. */
    @Scheduled(fixedDelayString = "${churoviaje.eventos.vencimiento-cada-ms:60000}",
            initialDelayString = "${churoviaje.eventos.vencimiento-cada-ms:60000}")
    @Transactional
    public List<Long> vencer() {
        List<Long> vencidos = eventos.vencerHasta(reloj.instant());
        if (!vencidos.isEmpty()) {
            LOG.info("{} eventos pasaron a Vencido", vencidos.size());
        }
        vencidos.forEach(id -> avisos.publishEvent(AvisoTrafico.vence(id))); // RN-02.15
        return vencidos;
    }
}
