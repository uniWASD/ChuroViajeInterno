package com.churoviaje.backend.dominio;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * DTO de un evento vial como lo ve la app (RN-01.12, RN-02.5, D-42): tipo, ubicacion, cuando ocurrio, si esta
 * confirmado y su origen. Nunca lleva quien lo reporto ni quien lo voto (RNF-10). El nivel solo aparece en la
 * congestion, y el trazado solo en la congestion de telemetria que lo tiene (RN-04.25): la app la dibuja a lo largo
 * de la calle. Los eventos de conductores traen cuantos votos «real» y «falso» llevan (D-46), sin decir quien voto;
 * la congestion de telemetria no se vota (D-20) y no los trae. Lo entrega {@link EventoRepository} en las consultas para mostrar, y el controlador lo arma con
 * {@link #de(EventoVial)} para responder al reporte.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventoVialRespuesta(long id, String tipo, Coordenadas ubicacion, OffsetDateTime momentoOcurrencia,
        String estado, boolean confirmado, String origen, String nivel, List<Coordenadas> trazado,
        Integer votosReal, Integer votosFalso) {

    /** Las fechas salen en la hora de Bolivia, como en el contrato. */
    public static final ZoneId ZONA = ZoneId.of("America/La_Paz");

    /** La ubicacion del evento, en grados decimales. */
    public record Coordenadas(BigDecimal latitud, BigDecimal longitud) {}

    public static EventoVialRespuesta de(EventoVial evento) {
        Ubicacion ubicacion = evento.getUbicacion();
        return new EventoVialRespuesta(evento.getId(), evento.getTipo().codigo(),
                new Coordenadas(ubicacion.latitud(), ubicacion.longitud()),
                OffsetDateTime.ofInstant(evento.getOcurrioEn(), ZONA), evento.getEstado().name(),
                evento.getEstado() == EstadoEvento.CONFIRMADO, evento.getOrigen().name(), evento.getNivel(),
                trazado(evento.getTrazado()), votos(evento, evento.getVotosReal()), votos(evento, evento.getVotosFalso()));
    }

    /** D-46: el conteo solo sale en los eventos que se votan, los de conductores. */
    private static Integer votos(EventoVial evento, int cuantos) {
        return evento.getOrigen() == OrigenEvento.CONDUCTOR ? cuantos : null;
    }

    /** Cada punto con latitud y longitud, igual que el trazado de una ruta en {@code POST /rutas}; sin trazado, no sale. */
    private static List<Coordenadas> trazado(List<Ubicacion> puntos) {
        if (puntos.isEmpty()) {
            return null;
        }
        return puntos.stream().map(p -> new Coordenadas(p.latitud(), p.longitud())).toList();
    }
}
