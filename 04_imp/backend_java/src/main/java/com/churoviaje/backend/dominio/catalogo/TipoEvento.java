package com.churoviaje.backend.dominio.catalogo;

/**
 * Un tipo de evento vial tal como esta en el catalogo (RN-01.3, RN-02.3, RN-02.7).
 *
 * @param codigo           clave estable, por ejemplo {@code ACCIDENTE}
 * @param vigenciaMinutos  cuanto dura un evento de este tipo desde que ocurrio
 * @param reportable       si un conductor puede reportarlo; la congestion solo la detecta el sistema
 */
public record TipoEvento(String codigo, String nombre, String color, int vigenciaMinutos, boolean reportable) {}
