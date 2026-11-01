package com.churoviaje.backend.dominio;

import com.churoviaje.backend.dominio.cobertura.CoberturaManchaUrbana;
import java.math.BigDecimal;
import java.util.Objects;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

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

}
