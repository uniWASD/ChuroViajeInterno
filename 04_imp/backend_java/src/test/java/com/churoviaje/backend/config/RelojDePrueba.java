package com.churoviaje.backend.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Reloj que las pruebas mueven a mano: asi se prueba que el JWT vence a la hora y la renovacion a los 30 dias. */
public class RelojDePrueba extends Clock {

    private volatile Instant ahora = Instant.parse("2026-11-01T12:00:00Z");

    public void fijar(Instant instante) {
        this.ahora = instante;
    }

    public void avanzar(Duration tiempo) {
        this.ahora = ahora.plus(tiempo);
    }

    @Override
    public Instant instant() {
        return ahora;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zona) {
        return this;
    }
}
