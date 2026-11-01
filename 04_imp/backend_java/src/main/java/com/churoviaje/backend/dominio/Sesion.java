package com.churoviaje.backend.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Sesion de un conductor: lo que respalda a su token de renovacion (RN-08.5, RN-08.12).
 * Solo guarda la huella del token; el token en si solo lo conoce la app.
 */
@Entity
@Table(name = "sesion")
public class Sesion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conductor_id", nullable = false, updatable = false)
    private Conductor conductor;

    @Column(name = "token_renovacion_hash", nullable = false, updatable = false, length = 64)
    private String tokenRenovacionHash;

    @Column(name = "emitida_en", nullable = false, updatable = false)
    private Instant emitidaEn;

    @Column(name = "expira_en", nullable = false, updatable = false)
    private Instant expiraEn;

    protected Sesion() {
        // JPA
    }

    private Sesion(Conductor conductor, String tokenRenovacionHash, Instant emitidaEn, Instant expiraEn) {
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
        return new Sesion(conductor, tokenRenovacionHash, ahora, ahora.plus(duracion));
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
