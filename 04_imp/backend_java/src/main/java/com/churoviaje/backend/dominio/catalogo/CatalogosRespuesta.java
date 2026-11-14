package com.churoviaje.backend.dominio.catalogo;

import java.util.List;

/**
 * DTO de {@code GET /catalogos} (D-06, D-42): la app no tiene valores propios, todo sale de aqui. Los tipos de evento
 * y de combustible los entrega {@link CatalogoRepository} ya como DTO.
 */
public record CatalogosRespuesta(
        List<TipoEventoDto> tiposEvento,
        List<TipoCombustibleDto> tiposCombustible,
        List<TiempoReporteDto> tiemposReporte,
        List<ValorFilaDto> valoresFila) {

    public record TipoEventoDto(String codigo, String nombre, String color, int vigenciaMinutos, boolean reportable) {

        public static TipoEventoDto de(TipoEvento tipo) {
            return new TipoEventoDto(tipo.codigo(), tipo.nombre(), tipo.color(), tipo.vigenciaMinutos(),
                    tipo.reportable());
        }
    }

    /** {@code seleccionable} es falso para «Desconocido»: el conductor no puede elegirlo (RN-05.9). */
    public record TipoCombustibleDto(String codigo, String nombre, boolean seleccionable) {

        public static TipoCombustibleDto de(TipoCombustible tipo) {
            return new TipoCombustibleDto(tipo.codigo(), tipo.nombre(), tipo.seleccionable());
        }
    }

    public record TiempoReporteDto(int minutos, String nombre, boolean porDefecto) {}

    public record ValorFilaDto(String codigo, String nombre) {}
}
