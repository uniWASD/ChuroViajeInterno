package com.churoviaje.backend.dto;

import jakarta.validation.constraints.NotNull;

public record CoordenadaDTO(
    @NotNull(message = "La latitud es obligatoria") Double lat,
    @NotNull(message = "La longitud es obligatoria") Double lon
) {}