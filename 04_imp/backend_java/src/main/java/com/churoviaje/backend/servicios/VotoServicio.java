package com.churoviaje.backend.servicios;

import com.churoviaje.backend.dominio.EstadoEvento;
import com.churoviaje.backend.dominio.EventoVial;
import com.churoviaje.backend.dominio.ValorVoto;
import com.churoviaje.backend.dominio.Voto;
import com.churoviaje.backend.errores.ExcepcionRechazo;
import com.churoviaje.backend.motor.AvisoTrafico;
import com.churoviaje.backend.repositorios.EventoVialRepositorio;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Votos «real» o «falso» sobre los eventos (CU-02, HU-11). */
@Service
public class VotoServicio {

    private final EventoVialRepositorio eventos;
    private final Clock reloj;
    private final ApplicationEventPublisher avisos;

    public VotoServicio(EventoVialRepositorio eventos, Clock reloj, ApplicationEventPublisher avisos) {
        this.eventos = eventos;
        this.reloj = reloj;
        this.avisos = avisos;
    }

    /**
     * RN-02.10 a RN-02.16. El evento queda bloqueado mientras se cuenta el voto, asi que dos votos que llegan a la
     * vez no se pisan. Devuelve el evento como quedo.
     */
    @Transactional
    public EventoVial votar(long eventoId, long conductorId, ValorVoto valor) {
        Instant ahora = reloj.instant();
        boolean simulado = eventos.conductorSimulado(conductorId)
                .orElseThrow(() -> new ExcepcionRechazo(HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO",
                        "Inicia sesión para continuar."));
        EventoVial evento = eventos.buscarParaVotar(eventoId)
                .orElseThrow(() -> new ExcepcionRechazo(HttpStatus.NOT_FOUND, "EVENTO_NO_ENCONTRADO",
                        "Ese evento ya no existe."));
        if (eventos.yaVoto(eventoId, conductorId)) {
            throw votoRepetido(); // RN-02.11
        }
        EventoVial votado = evento.registrarVoto(valor, conductorId, ahora); // RN-02.10, RN-02.12 a RN-02.14
        try {
            eventos.insertarVoto(new Voto(eventoId, conductorId, valor, ahora, simulado));
        } catch (DuplicateKeyException e) {
            throw votoRepetido(); // la base es la segunda barrera de RN-02.11
        }
        eventos.actualizarVotos(votado, votado.getEstado() != evento.getEstado(), ahora);
        if (votado.getEstado() == EstadoEvento.RETIRADO && evento.getEstado() != EstadoEvento.RETIRADO) {
            avisos.publishEvent(AvisoTrafico.retira(eventoId)); // RN-02.15: deja de pesar en las rutas
        }
        return votado;
    }

    private static ExcepcionRechazo votoRepetido() {
        return new ExcepcionRechazo(HttpStatus.CONFLICT, "VOTO_REPETIDO", "Ya votaste este evento.");
    }
}
