package com.churoviaje.backend.dominio;

import com.churoviaje.backend.dominio.cobertura.CoberturaManchaUrbana;
import com.churoviaje.backend.errores.ExcepcionRechazo;
import java.math.BigDecimal;
import java.util.Objects;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;

public record Ubicacion(
        @NotNull(message = "Falta la latitud")
        @DecimalMin(value = "-90.0", message = "La latitud no es válida")
        @DecimalMax(value = "90.0", message = "La latitud no es válida")
        BigDecimal latitud,

        @NotNull(message = "Falta la longitud")
        @DecimalMin(value = "-180.0", message = "La longitud no es válida")
        @DecimalMax(value = "180.0", message = "La longitud no es válida")
        BigDecimal longitud
) {
    /** P-39: hasta cuántos metros de su posición puede reportar un conductor (RN-01.5). */
    public static final int RADIO_REPORTE_METROS = 1000;

    private static final double RADIO_TIERRA_METROS = 6_371_000;

    /** RN-01.6 / RN-03.3: regla de dominio, sin dependencia HTTP. */
    public boolean estaDentroDeManchaUrbana(CoberturaManchaUrbana cobertura) {
        Objects.requireNonNull(cobertura, "La cobertura es obligatoria");
        // La validación de rangos del DTO pertenece a @Valid (T-088).
        return cobertura.contiene(Objects.requireNonNull(latitud, "Falta la latitud").doubleValue(),
                Objects.requireNonNull(longitud, "Falta la longitud").doubleValue());
    }

    public void validarParaReporte(CoberturaManchaUrbana cobertura) {
        if (!estaDentroDeManchaUrbana(cobertura)) {
            throw UbicacionFueraCoberturaException.paraReporte();
        }
    }

    public ResultadoUbicacionRuta validarParaRuta(CoberturaManchaUrbana cobertura) {
        return estaDentroDeManchaUrbana(cobertura)
                ? ResultadoUbicacionRuta.VALIDA : ResultadoUbicacionRuta.PUNTO_INVALIDO;
    }

    /**
     * RN-01.5 (#191): el punto del reporte puede ser otro que la posición del conductor, pero a no más de
     * {@link #RADIO_REPORTE_METROS}. Sin la posición (el GPS no respondió, o un cliente sin GPS) no hay con qué
     * comparar y vale el punto marcado, como antes.
     */
    public void validarCercaniaDelConductor(Ubicacion posicionConductor) {
        if (posicionConductor == null) {
            return;
        }
        if (distanciaMetrosA(posicionConductor) > RADIO_REPORTE_METROS) {
            throw new ExcepcionRechazo(HttpStatus.UNPROCESSABLE_ENTITY, "UBICACION_LEJANA",
                    "Solo puedes reportar eventos a 1 km de donde estás.");
        }
    }

    /** Distancia en metros sobre la esfera (haversine); sobra para distancias dentro de la ciudad. */
    public double distanciaMetrosA(Ubicacion otra) {
        double lat1 = Math.toRadians(latitud.doubleValue());
        double lat2 = Math.toRadians(otra.latitud().doubleValue());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(otra.longitud().doubleValue() - longitud.doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * RADIO_TIERRA_METROS * Math.asin(Math.min(1, Math.sqrt(a)));
    }
}
