package com.churoviaje.backend.dto;

import com.churoviaje.backend.dto.RutaRespuesta.Punto;
import com.churoviaje.backend.dto.RutaRespuesta.TramoRuta;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Respuesta de {@code POST /rutas/revision} (CU-04). Las cuatro son respuestas correctas, con 200:
 * <ul>
 *   <li>{@code SIN_CAMBIOS}: ningún evento nuevo toca lo que falta.</li>
 *   <li>{@code SIN_ALTERNATIVA}: hay un evento, pero no un camino mejor (RN-04.16). Trae {@code eventoId}.</li>
 *   <li>{@code ALTERNATIVA}: la ruta alternativa con su costo y el de seguir, y {@code eventoId} (RN-04.16).</li>
 *   <li>{@code RUTA_DESACTUALIZADA}: se recargó la red vial y la app pide la ruta de nuevo.</li>
 * </ul>
 * {@code eventoId} es el evento que provocó la respuesta: la app lo agrega a los ya avisados (RN-04.19).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RevisionRutaRespuesta(
        String estado,
        Long eventoId,
        Long costo,
        Long costoActual,
        Long distanciaM,
        Long tiempoS,
        List<Punto> trazado,
        List<TramoRuta> tramos) {

    public static final String SIN_CAMBIOS = "SIN_CAMBIOS";
    public static final String SIN_ALTERNATIVA = "SIN_ALTERNATIVA";
    public static final String ALTERNATIVA = "ALTERNATIVA";
    public static final String RUTA_DESACTUALIZADA = "RUTA_DESACTUALIZADA";

    public static RevisionRutaRespuesta sinCambios() {
        return new RevisionRutaRespuesta(SIN_CAMBIOS, null, null, null, null, null, null, null);
    }

    public static RevisionRutaRespuesta rutaDesactualizada() {
        return new RevisionRutaRespuesta(RUTA_DESACTUALIZADA, null, null, null, null, null, null, null);
    }

    public static RevisionRutaRespuesta sinAlternativa(long eventoId) {
        return new RevisionRutaRespuesta(SIN_ALTERNATIVA, eventoId, null, null, null, null, null, null);
    }

    /** {@code costoActual} es {@code null} si un bloqueo corta lo que falta y seguir no es posible. */
    public static RevisionRutaRespuesta alternativa(long eventoId, long costo, Long costoActual, long distanciaM,
            long tiempoS, List<Punto> trazado, List<TramoRuta> tramos) {
        return new RevisionRutaRespuesta(ALTERNATIVA, eventoId, costo, costoActual, distanciaM, tiempoS,
                List.copyOf(trazado), List.copyOf(tramos));
    }
}
