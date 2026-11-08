package com.churoviaje.backend.dominio;

import com.churoviaje.backend.errores.ExcepcionRechazo;
import java.util.Locale;
import org.springframework.http.HttpStatus;

/** Lo que dice un conductor sobre un evento (RN-02.11): que es real o que es falso. */
public enum ValorVoto {
    REAL,
    FALSO;

    /** Lee el valor que manda la app, sin importar mayusculas; otro valor responde 400. */
    public static ValorVoto desdeTexto(String texto) {
        try {
            return valueOf(texto.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ExcepcionRechazo(HttpStatus.BAD_REQUEST, "PETICION_INVALIDA",
                    "El voto debe ser «real» o «falso».");
        }
    }
}
