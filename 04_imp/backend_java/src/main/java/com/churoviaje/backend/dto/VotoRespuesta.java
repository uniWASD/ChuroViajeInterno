package com.churoviaje.backend.dto;

import com.churoviaje.backend.dominio.EstadoEvento;
import com.churoviaje.backend.dominio.EventoVial;

/** Estado del evento despues del voto (HU-11). No dice quien voto ni cuantos votos tiene cada opcion. */
public record VotoRespuesta(long eventoId, String estado, boolean confirmado) {

    public static VotoRespuesta de(EventoVial evento) {
        return new VotoRespuesta(evento.getId(), evento.getEstado().name(),
                evento.getEstado() == EstadoEvento.CONFIRMADO);
    }
}
