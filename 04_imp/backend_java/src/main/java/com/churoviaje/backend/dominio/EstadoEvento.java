package com.churoviaje.backend.dominio;

/** Estados de un evento vial (RN-01.10, RN-02.2, RN-02.4, RN-02.13, RN-02.14). */
public enum EstadoEvento {
    ACTIVO,
    CONFIRMADO,
    RETIRADO,
    VENCIDO;

    /** Solo los eventos Activos y Confirmados se muestran (RN-02.2). */
    public boolean seMuestra() {
        return this == ACTIVO || this == CONFIRMADO;
    }
}
