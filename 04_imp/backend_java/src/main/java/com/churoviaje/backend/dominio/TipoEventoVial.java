package com.churoviaje.backend.dominio;

public enum TipoEventoVial {
    ACCIDENTE(true),
    CONTROL_POLICIAL(true),
    BLOQUEO(true),
    OTRO(true),
    CONGESTION(false); // RN-01.3: La congestión se rechaza si un usuario intenta reportarla

    private final boolean reportable;

    TipoEventoVial(boolean reportable) {
        this.reportable = reportable;
    }

    public boolean isReportable() {
        return reportable;
    }
}