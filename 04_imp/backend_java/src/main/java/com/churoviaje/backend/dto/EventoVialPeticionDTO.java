package com.churoviaje.backend.dto;

import java.time.OffsetDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record EventoVialPeticionDTO(
    @NotNull(message = "El tipo es obligatorio")
    String tipo,

    @NotNull(message = "La ubicacion es obligatoria")
    @Valid
    CoordenadaDTO ubicacion,

    @NotNull(message = "La hora de confirmacion es obligatoria")
    OffsetDateTime horaConfirmacion,

    Integer minutos // Este es opcional (no lleva @NotNull)
) {}