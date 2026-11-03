package com.churoviaje.backend.servicios;

import java.util.Optional;

/** Valida con Google el token de identidad que manda la app (RN-08.3). */
public interface ValidadorTokenGoogle {

    /**
     * @return el identificador de la cuenta de Google si el token es autentico, de nuestra app y no ha
     *         vencido; vacio si es falso, ajeno o vencido
     * @throws GoogleNoDisponibleException si no se pudo consultar a Google (red, certificados)
     */
    Optional<String> identificarCuenta(String tokenIdentidad);

    class GoogleNoDisponibleException extends RuntimeException {
        public GoogleNoDisponibleException(Throwable causa) {
            super("No se pudo consultar a Google", causa);
        }
    }
}
