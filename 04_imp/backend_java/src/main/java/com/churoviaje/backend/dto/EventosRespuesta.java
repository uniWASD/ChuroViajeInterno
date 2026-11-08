package com.churoviaje.backend.dto;

import java.util.List;

/**
 * Respuesta de {@code GET /eventos} (RN-02.6). Sin marca, {@code completa} es verdadero y {@code eventos} trae
 * todos los vigentes. Con la marca de la consulta anterior, trae solo los eventos nuevos o cambiados y, en
 * {@code quitados}, los identificadores de los que dejaron de mostrarse, por vencidos o retirados. La app guarda
 * {@code marca} y la manda en la siguiente consulta.
 */
public record EventosRespuesta(String marca, boolean completa, List<EventoVialRespuesta> eventos,
        List<Long> quitados) {}
