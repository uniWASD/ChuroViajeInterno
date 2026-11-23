package com.churoviaje.backend.dominio.anh;

/**
 * Una respuesta de ANH que se descarta completa, sin cambiar ningún estado (RN-09.5, RN-09.6). El mensaje es el motivo
 * para el registro del ciclo (RN-09.19) y no lleva datos de la respuesta.
 */
public class RespuestaDescartada extends Exception {

    public RespuestaDescartada(String motivo) {
        super(motivo);
    }
}
