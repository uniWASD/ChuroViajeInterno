package com.churoviaje.backend.dto;

import java.util.List;

public class CatalogosRespuesta {
    private List<TipoEventoDto> tiposEvento;
    private List<TipoCombustibleDto> tiposCombustible;
    private List<TiempoReporteDto> tiemposReporte;
    private List<ValorFilaDto> valoresFila;

    public CatalogosRespuesta(List<TipoEventoDto> tiposEvento, List<TipoCombustibleDto> tiposCombustible,
            List<TiempoReporteDto> tiemposReporte, List<ValorFilaDto> valoresFila) {
        this.tiposEvento = tiposEvento;
        this.tiposCombustible = tiposCombustible;
        this.tiemposReporte = tiemposReporte;
        this.valoresFila = valoresFila;
    }

    public List<TipoEventoDto> getTiposEvento() {
        return tiposEvento;
    }

    public void setTiposEvento(List<TipoEventoDto> tiposEvento) {
        this.tiposEvento = tiposEvento;
    }

    public List<TipoCombustibleDto> getTiposCombustible() {
        return tiposCombustible;
    }

    public void setTiposCombustible(List<TipoCombustibleDto> tiposCombustible) {
        this.tiposCombustible = tiposCombustible;
    }

    public List<TiempoReporteDto> getTiemposReporte() {
        return tiemposReporte;
    }

    public void setTiemposReporte(List<TiempoReporteDto> tiemposReporte) {
        this.tiemposReporte = tiemposReporte;
    }

    public List<ValorFilaDto> getValoresFila() {
        return valoresFila;
    }

    public void setValoresFila(List<ValorFilaDto> valoresFila) {
        this.valoresFila = valoresFila;
    }

    public static class TipoEventoDto {
        private String nombre;
        private String color;
        private int vigenciaMinutos;
        private boolean reportable;

        public TipoEventoDto(String nombre, String color, int vigenciaMinutos, boolean reportable) {
            this.nombre = nombre;
            this.color = color;
            this.vigenciaMinutos = vigenciaMinutos;
            this.reportable = reportable;
        }

        public String getNombre() {
            return nombre;
        }

        public String getColor() {
            return color;
        }

        public int getVigenciaMinutos() {
            return vigenciaMinutos;
        }

        public boolean isReportable() {
            return reportable;
        }
    }

    public static class TipoCombustibleDto {
        private String nombre;
        private boolean seleccionableRecarga;

        public TipoCombustibleDto(String nombre, boolean seleccionableRecarga) {
            this.nombre = nombre;
            this.seleccionableRecarga = seleccionableRecarga;
        }

        public String getNombre() {
            return nombre;
        }

        public boolean isSeleccionableRecarga() {
            return seleccionableRecarga;
        }
    }

    public static class TiempoReporteDto {
        private String descripcion;
        private int minutosAtras;
        private boolean marcadoPorDefecto;

        public TiempoReporteDto(String descripcion, int minutosAtras, boolean marcadoPorDefecto) {
            this.descripcion = descripcion;
            this.minutosAtras = minutosAtras;
            this.marcadoPorDefecto = marcadoPorDefecto;
        }

        public String getDescripcion() {
            return descripcion;
        }

        public int getMinutosAtras() {
            return minutosAtras;
        }

        public boolean isMarcadoPorDefecto() {
            return marcadoPorDefecto;
        }
    }

    public static class ValorFilaDto {
        private String nombre;
        private String nivel;

        public ValorFilaDto(String nombre, String nivel) {
            this.nombre = nombre;
            this.nivel = nivel;
        }

        public String getNombre() {
            return nombre;
        }

        public String getNivel() {
            return nivel;
        }
    }
}