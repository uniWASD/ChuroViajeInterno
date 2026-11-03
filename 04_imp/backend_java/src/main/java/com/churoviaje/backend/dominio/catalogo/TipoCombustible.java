package com.churoviaje.backend.dominio.catalogo;

/** Un tipo de combustible del catalogo. */
public record TipoCombustible(String codigo, String nombre) {

    public static final String DESCONOCIDO = "DESCONOCIDO";

    /**
     * RN-05.9: «Desconocido» puede traerlo una fuente (ANH o el analisis de WhatsApp), pero el conductor
     * no puede elegirlo al confirmar una recarga.
     */
    public boolean seleccionable() {
        return !DESCONOCIDO.equals(codigo);
    }
}
