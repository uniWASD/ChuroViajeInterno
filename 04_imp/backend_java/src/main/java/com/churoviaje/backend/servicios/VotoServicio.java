package com.churoviaje.backend.servicios;

import com.churoviaje.backend.dominio.Conductor;
import com.churoviaje.backend.dominio.ConductorRepository;
import com.churoviaje.backend.dominio.EstadoEvento;
import com.churoviaje.backend.dominio.EventoRepository;
import com.churoviaje.backend.dominio.EventoVial;
import com.churoviaje.backend.dominio.ValorVoto;
import com.churoviaje.backend.dominio.Voto;
import com.churoviaje.backend.dominio.VotoRepository;
import com.churoviaje.backend.errores.ExcepcionRechazo;
import com.churoviaje.backend.motor.AvisoTrafico;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Votos «real» o «falso» sobre los eventos (CU-02, HU-11). */
@Service
public class VotoServicio {

    private final EventoRepository eventos;
    private final VotoRepository votos;
    private final ConductorRepository conductores;
    private final Clock reloj;
    private final ApplicationEventPublisher avisos;

    public VotoServicio(EventoRepository eventos, VotoRepository votos, ConductorRepository conductores, Clock reloj,
            ApplicationEventPublisher avisos) {
        this.eventos = eventos;
        this.votos = votos;
        this.conductores = conductores;
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
        Conductor votante = conductores.buscarPorId(conductorId)
                .orElseThrow(() -> new ExcepcionRechazo(HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO",
                        "Inicia sesión para continuar."));
        EventoVial evento = eventos.buscarParaVotar(eventoId)
                .orElseThrow(() -> new ExcepcionRechazo(HttpStatus.NOT_FOUND, "EVENTO_NO_ENCONTRADO",
                        "Ese evento ya no existe."));
        if (votos.yaVoto(eventoId, conductorId)) {
            throw votoRepetido(); // RN-02.11
        }
        EventoVial votado = evento.registrarVoto(valor, conductorId, ahora); // RN-02.10, RN-02.12 a RN-02.14
        // RN-02.16: el voto de un conductor simulado queda simulado
        if (!votos.guardarSiNoVoto(new Voto(eventoId, conductorId, valor, ahora, votante.isSimulado()))) {
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
