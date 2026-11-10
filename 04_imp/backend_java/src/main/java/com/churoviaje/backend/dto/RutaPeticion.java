package com.churoviaje.backend.dto;

import com.churoviaje.backend.dominio.Ubicacion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Cuerpo de {@code POST /rutas} (RN-03.2): origen y destino obligatorios y paradas opcionales, en el orden que
 * eligió el conductor.
 */
public record RutaPeticion(
        @NotNull(message = "Falta el origen") @Valid Ubicacion origen,
        @NotNull(message = "Falta el destino") @Valid Ubicacion destino,
        @Size(max = RutaPeticion.MAXIMO_PARADAS, message = "Se aceptan hasta 10 paradas")
        List<@NotNull(message = "Una parada no tiene ubicación") @Valid Ubicacion> paradas) {

    public static final int MAXIMO_PARADAS = 10;

    /** Las paradas, o una lista vacía si no vinieron. */
    public List<Ubicacion> paradasOVacio() {
        return paradas == null ? List.of() : paradas;
    }
}
