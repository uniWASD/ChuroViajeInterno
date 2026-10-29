package com.churoviaje.backend.servicios;

import com.churoviaje.backend.dto.CatalogosRespuesta;
import com.churoviaje.backend.dto.CatalogosRespuesta.*;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CatalogoServicio {

    public CatalogosRespuesta obtenerCatalogos() {
        return new CatalogosRespuesta(
                obtenerTiposEvento(),
                obtenerTiposCombustible(),
                obtenerTiemposReporte(),
                obtenerValoresFila());
    }

    private List<TipoEventoDto> obtenerTiposEvento() {
        return List.of(
                new TipoEventoDto("Bloqueo", "#FF0000", 120, true),
                new TipoEventoDto("Congestión", "#FFA500", 60, true),
                new TipoEventoDto("Accidente", "#8B0000", 90, true),
                new TipoEventoDto("Otro", "#808080", 60, true),
                new TipoEventoDto("Informativo", "#0000FF", 30, false));
    }

    private List<TipoCombustibleDto> obtenerTiposCombustible() {
        return List.of(
                new TipoCombustibleDto("Gasolina Especial", true),
                new TipoCombustibleDto("Gasolina Premium", true),
                new TipoCombustibleDto("Diésel", true),
                new TipoCombustibleDto("GNV", true),
                new TipoCombustibleDto("Desconocido", false));
    }

    private List<TiempoReporteDto> obtenerTiemposReporte() {
        return List.of(
                new TiempoReporteDto("Ahora mismo", 0, false),
                new TiempoReporteDto("Hace 5 minutos", 5, true),
                new TiempoReporteDto("Hace 10 minutos", 10, false),
                new TiempoReporteDto("Hace 30 minutos", 30, false));
    }

    private List<ValorFilaDto> obtenerValoresFila() {
        return List.of(
                new ValorFilaDto("Sin fila", "sin_fila"),
                new ValorFilaDto("Corta", "corta"),
                new ValorFilaDto("Media", "media"),
                new ValorFilaDto("Larga", "larga"));
    }
}