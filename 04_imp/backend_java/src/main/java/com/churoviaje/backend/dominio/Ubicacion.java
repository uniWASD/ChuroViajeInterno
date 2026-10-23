package com.churoviaje.backend.dominio;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;

@Embeddable
public record Ubicacion(
    @NotNull Double latitud,
    @NotNull Double longitud
) {}