package com.churoviaje.backend.dominio.monitoreo;

/**
 * Un aviso del lote tal como llegó, todo como texto. {@code null} en un campo obligatorio quiere decir que faltaba o
 * que no era texto: es un formato inválido (RN-06.15). {@code fila} puede faltar.
 */
public record AvisoRecibido(String surtidor, String combustible, String estado, String fila, String fecha) {

    /** Un elemento del lote que ni siquiera es un objeto. */
    public static final AvisoRecibido SIN_FORMATO = new AvisoRecibido(null, null, null, null, null);
}
