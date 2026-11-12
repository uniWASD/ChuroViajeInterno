package com.churoviaje.backend.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.http.HttpStatus;
import com.churoviaje.backend.errores.ExcepcionRechazo;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Persona que usa la app. Se identifica solo por su cuenta de Google (RN-08.1, RN-08.4):
 * se guarda el identificador de la cuenta y nada mas (RN-08.6).
 */
@Entity
@Table(name = "conductor")
public class Conductor {

    /** RN-01.9 y P-04: un conductor puede enviar hasta 5 reportes cada 10 minutos, sean del tipo que sean. */
    public static final int MAXIMO_REPORTES = 5;
    public static final Duration VENTANA_REPORTES = Duration.ofMinutes(10);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "google_id", nullable = false, updatable = false)
    private String googleId;

    @Column(name = "fecha_alta", nullable = false, updatable = false)
    private Instant fechaAlta;

    @Column(nullable = false, updatable = false)
    private boolean simulado;

    protected Conductor() {
        // JPA
    }

    private Conductor(String googleId, Instant fechaAlta, boolean simulado) {
        this.googleId = Objects.requireNonNull(googleId, "googleId");
        this.fechaAlta = Objects.requireNonNull(fechaAlta, "fechaAlta");
        this.simulado = simulado;
        if (googleId.isBlank()) {
            throw new IllegalArgumentException("El identificador de Google no puede estar vacio");
        }
    }

    /** Conductor real que entra por primera vez con su cuenta de Google. */
    public static Conductor deGoogle(String googleId, Instant ahora) {
        return new Conductor(googleId, ahora, false);
    }

    /**
     * RN-01.9: rechaza el reporte si el conductor ya hizo {@link #MAXIMO_REPORTES} en los ultimos
     * {@link #VENTANA_REPORTES}. Quien llama cuenta los reportes con el conductor bloqueado, para que dos
     * reportes que llegan juntos no pasen los dos.
     */
    public static void verificarLimiteDeReportes(long reportesEnLaVentana) {
        if (reportesEnLaVentana >= MAXIMO_REPORTES) {
            throw new ExcepcionRechazo(HttpStatus.TOO_MANY_REQUESTS, "LIMITE_DE_REPORTES",
                    "Hiciste muchos reportes seguidos. Espera unos minutos para reportar de nuevo.");
        }
    }

    public Long getId() {
        return id;
    }

    public String getGoogleId() {
        return googleId;
    }

    public Instant getFechaAlta() {
        return fechaAlta;
    }

    public boolean isSimulado() {
        return simulado;
    }
}
