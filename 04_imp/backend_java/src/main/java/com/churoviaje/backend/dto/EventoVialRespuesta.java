package com.churoviaje.backend.dto;

import com.churoviaje.backend.dominio.EstadoEvento;
import com.churoviaje.backend.dominio.EventoVial;
import com.churoviaje.backend.dominio.Ubicacion;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Un evento vial como lo ve la app (RN-01.12, RN-02.5): tipo, ubicacion, cuando ocurrio, si esta confirmado y su
 * origen. Nunca lleva quien lo reporto ni quien lo voto (RNF-10). El nivel solo aparece en la congestion.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventoVialRespuesta(long id, String tipo, Ubicacion ubicacion, OffsetDateTime momentoOcurrencia,
        String estado, boolean confirmado, String origen, String nivel) {

    /** Las fechas salen en la hora de Bolivia, como en el contrato. */
    public static final ZoneId ZONA = ZoneId.of("America/La_Paz");

    public static EventoVialRespuesta de(EventoVial evento) {
        return new EventoVialRespuesta(evento.getId(), evento.getTipo().codigo(), evento.getUbicacion(),
                OffsetDateTime.ofInstant(evento.getOcurrioEn(), ZONA), evento.getEstado().name(),
                evento.getEstado() == EstadoEvento.CONFIRMADO, evento.getOrigen().name(), evento.getNivel());
    }
}
