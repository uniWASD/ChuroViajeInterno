package com.churoviaje.backend.dominio.catalogo;

/** Valores de la fila aproximada (P-15, RN-05.4, RN-06.7). Es la unica lista: la app y el servicio de WhatsApp la usan tal cual. */
public enum ValorFila {
    SIN_FILA("Sin fila"),
    CORTA("Corta"),
    MEDIA("Media"),
    LARGA("Larga");

    private final String nombre;

    ValorFila(String nombre) {
        this.nombre = nombre;
    }

    public String nombre() {
        return nombre;
    }
}
