package com.churoviaje.backend.dto;

import com.churoviaje.backend.dominio.Ubicacion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Cuerpo de {@code POST /telemetria} (CU-04, RN-04.1): un grupo de lecturas del GPS. La app lee cada 3 segundos y
 * envía cada 15 (P-13), así que lo normal son 5; se aceptan hasta 100 por si se juntaron.
 */
public record TelemetriaPeticion(
        @NotEmpty(message = "Faltan las lecturas")
        @Size(max = TelemetriaPeticion.MAXIMO_LECTURAS, message = "Se aceptan hasta 100 lecturas por envío")
        List<@NotNull(message = "Una lectura está vacía") @Valid Lectura> lecturas) {

    public static final int MAXIMO_LECTURAS = 100;

    /** Una lectura: posición, precisión en metros y momento, sin nada del conductor. */
    public record Lectura(
            @NotNull(message = "Falta la ubicación de una lectura") @Valid Ubicacion ubicacion,
            @NotNull(message = "Falta la precisión de una lectura")
            @PositiveOrZero(message = "La precisión no puede ser negativa") Double precisionM,
            @NotNull(message = "Falta el momento de una lectura") OffsetDateTime momento) {}
}
