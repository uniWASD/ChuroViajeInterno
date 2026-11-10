package com.churoviaje.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Respuesta de {@code POST /rutas}. «Sin ruta» y «punto inválido» son respuestas correctas, con 200 y su mensaje
 * (RN-03.3, RN-03.12); solo una ruta encontrada trae distancia, tiempo, trazado y tramos (RN-03.13).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RutaRespuesta(
        String estado,
        String mensaje,
        Long distanciaM,
        Long tiempoS,
        List<Punto> trazado,
        List<TramoRuta> tramos) {

    public static final String ENCONTRADA = "ENCONTRADA";
    public static final String SIN_RUTA = "SIN_RUTA";
    public static final String PUNTO_INVALIDO = "PUNTO_INVALIDO";
    public static final String MENSAJE_SIN_RUTA = "No se encontró una ruta válida";
    public static final String MENSAJE_PUNTO_INVALIDO = "Origen o destino inválido";

    /** Un punto del trazado, en grados decimales. */
    public record Punto(double latitud, double longitud) {}

    /** Un tramo de la ruta: su identificador, que la app devuelve al revisar la ruta (D-12), y su calle. */
    public record TramoRuta(String id, String calle) {}

    public static RutaRespuesta encontrada(long distanciaM, long tiempoS, List<Punto> trazado, List<TramoRuta> tramos) {
        return new RutaRespuesta(ENCONTRADA, null, distanciaM, tiempoS, List.copyOf(trazado), List.copyOf(tramos));
    }

    public static RutaRespuesta sinRuta() {
        return new RutaRespuesta(SIN_RUTA, MENSAJE_SIN_RUTA, null, null, null, null);
    }

    public static RutaRespuesta puntoInvalido() {
        return new RutaRespuesta(PUNTO_INVALIDO, MENSAJE_PUNTO_INVALIDO, null, null, null, null);
    }
}
