package com.churoviaje.backend.dominio;

import com.churoviaje.backend.errores.ExcepcionRechazo;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.http.HttpStatus;

/**
 * Persona que usa la app. Se identifica solo por su cuenta de Google (RN-08.1, RN-08.4):
 * se guarda el identificador de la cuenta y nada mas (RN-08.6). Lo guarda y lo recupera un
 * {@link ConductorRepository}; el conductor no sabe de la base de datos (D-41).
 */
public class Conductor {

    /** RN-01.9 y P-04: un conductor puede enviar hasta 5 reportes cada 10 minutos, sean del tipo que sean. */
    public static final int MAXIMO_REPORTES = 5;
    public static final Duration VENTANA_REPORTES = Duration.ofMinutes(10);

    /** Vacio mientras el conductor no se guardo: el identificador lo da la base. */
    private final Long id;
    private final String googleId;
    private final Instant fechaAlta;
    private final boolean simulado;

    private Conductor(Long id, String googleId, Instant fechaAlta, boolean simulado) {
        this.id = id;
        this.googleId = Objects.requireNonNull(googleId, "googleId");
        this.fechaAlta = Objects.requireNonNull(fechaAlta, "fechaAlta");
        this.simulado = simulado;
        if (googleId.isBlank()) {
            throw new IllegalArgumentException("El identificador de Google no puede estar vacio");
        }
    }

    /** Conductor real que entra por primera vez con su cuenta de Google. */
    public static Conductor deGoogle(String googleId, Instant ahora) {
        return new Conductor(null, googleId, ahora, false);
    }

    /** Reconstruye un conductor guardado. */
    public static Conductor desdeRegistro(long id, String googleId, Instant fechaAlta, boolean simulado) {
        return new Conductor(id, googleId, fechaAlta, simulado);
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
