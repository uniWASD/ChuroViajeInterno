package com.churoviaje.backend.dto;

import java.util.List;

/** Respuesta de {@code GET /catalogos} (D-06): la app no tiene valores propios, todo sale de aqui. */
public record CatalogosRespuesta(
        List<TipoEventoDto> tiposEvento,
        List<TipoCombustibleDto> tiposCombustible,
        List<TiempoReporteDto> tiemposReporte,
        List<ValorFilaDto> valoresFila) {

    public record TipoEventoDto(String codigo, String nombre, String color, int vigenciaMinutos, boolean reportable) {}

    /** {@code seleccionable} es falso para «Desconocido»: el conductor no puede elegirlo (RN-05.9). */
    public record TipoCombustibleDto(String codigo, String nombre, boolean seleccionable) {}

    public record TiempoReporteDto(int minutos, String nombre, boolean porDefecto) {}

    public record ValorFilaDto(String codigo, String nombre) {}
}
