package com.churoviaje.backend.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Sesion de un conductor: lo que respalda a su token de renovacion (RN-08.5, RN-08.12).
 * Solo guarda la huella del token; el token en si solo lo conoce la app. La guarda y la recupera un
 * {@link SesionRepository} (D-41).
 */
public class Sesion {

    /** Vacio mientras la sesion no se guardo: el identificador lo da la base. */
    private final Long id;
    private final Conductor conductor;
    private final String tokenRenovacionHash;
    private final Instant emitidaEn;
    private final Instant expiraEn;

    private Sesion(Long id, Conductor conductor, String tokenRenovacionHash, Instant emitidaEn, Instant expiraEn) {
        this.id = id;
        this.conductor = Objects.requireNonNull(conductor, "conductor");
        this.tokenRenovacionHash = Objects.requireNonNull(tokenRenovacionHash, "tokenRenovacionHash");
        this.emitidaEn = emitidaEn;
        this.expiraEn = expiraEn;
    }

    /** Abre una sesion que vale {@code duracion} desde {@code ahora} (RN-08.12: 30 dias). */
    public static Sesion iniciar(Conductor conductor, String tokenRenovacionHash, Instant ahora, Duration duracion) {
        Objects.requireNonNull(ahora, "ahora");
        Objects.requireNonNull(duracion, "duracion");
        if (duracion.isZero() || duracion.isNegative()) {
            throw new IllegalArgumentException("La duracion de la sesion debe ser positiva");
        }
        return new Sesion(null, conductor, tokenRenovacionHash, ahora, ahora.plus(duracion));
    }

    /** Reconstruye una sesion guardada. */
    public static Sesion desdeRegistro(long id, Conductor conductor, String tokenRenovacionHash, Instant emitidaEn,
            Instant expiraEn) {
        return new Sesion(id, conductor, tokenRenovacionHash, emitidaEn, expiraEn);
    }

    /** Pasado el plazo, el conductor inicia sesion de nuevo con Google (RN-08.12). */
    public boolean estaVigente(Instant ahora) {
        return ahora.isBefore(expiraEn);
    }

    public Long getId() {
        return id;
    }

    public Conductor getConductor() {
        return conductor;
    }

    public String getTokenRenovacionHash() {
        return tokenRenovacionHash;
    }

    public Instant getEmitidaEn() {
        return emitidaEn;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }
}
