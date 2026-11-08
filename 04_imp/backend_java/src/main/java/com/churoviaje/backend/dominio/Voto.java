package com.churoviaje.backend.dominio;

import java.time.Instant;
import java.util.Objects;

/**
 * El voto de un conductor sobre un evento (RN-02.11, RN-02.16). Nunca sale en una respuesta: de el solo se
 * conoce el estado en que dejo al evento.
 */
public record Voto(long eventoId, long conductorId, ValorVoto valor, Instant fecha, boolean simulado) {

    public Voto {
        Objects.requireNonNull(valor, "valor");
        Objects.requireNonNull(fecha, "fecha");
    }
}
