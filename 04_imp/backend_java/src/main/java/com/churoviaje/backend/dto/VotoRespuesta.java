package com.churoviaje.backend.dto;

import com.churoviaje.backend.dominio.EstadoEvento;
import com.churoviaje.backend.dominio.EventoVial;

/**
 * Estado del evento despues del voto (HU-11) y cuantos votos «real» y «falso» lleva (D-46), para que la app actualice
 * el conteo del detalle. Nunca dice quien voto (RNF-10).
 */
public record VotoRespuesta(long eventoId, String estado, boolean confirmado, int votosReal, int votosFalso) {

    public static VotoRespuesta de(EventoVial evento) {
        return new VotoRespuesta(evento.getId(), evento.getEstado().name(),
                evento.getEstado() == EstadoEvento.CONFIRMADO, evento.getVotosReal(), evento.getVotosFalso());
    }
}
