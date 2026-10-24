package com.churoviaje.backend.dominio;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record Ubicacion(
        @NotNull
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        BigDecimal latitud,

        @NotNull
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        BigDecimal longitud
) {}