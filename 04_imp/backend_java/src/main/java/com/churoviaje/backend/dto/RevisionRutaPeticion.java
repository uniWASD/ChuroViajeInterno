package com.churoviaje.backend.dto;

import com.churoviaje.backend.dominio.Ubicacion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Cuerpo de {@code POST /rutas/revision} (D-12): lo que la app sabe de la ruta Activa. El servidor no guarda nada
 * de esto (D-05).
 *
 * @param tramos          los {@code id} de los tramos que faltan, en el orden en que llegaron en la ruta
 * @param paradas         las paradas que faltan, en orden (opcional)
 * @param eventosAvisados los eventos que ya provocaron un aviso en esta ruta (opcional, RN-04.19)
 */
public record RevisionRutaPeticion(
        @NotNull(message = "Falta la posición") @Valid Ubicacion posicion,
        @NotNull(message = "Faltan los tramos de la ruta")
        @Size(max = RevisionRutaPeticion.MAXIMO_TRAMOS, message = "La ruta tiene demasiados tramos")
        List<@NotBlank(message = "Un tramo de la ruta no tiene id") String> tramos,
        @NotNull(message = "Falta el destino") @Valid Ubicacion destino,
        @Size(max = RutaPeticion.MAXIMO_PARADAS, message = "Se aceptan hasta 10 paradas")
        List<@NotNull(message = "Una parada no tiene ubicación") @Valid Ubicacion> paradas,
        @Size(max = RevisionRutaPeticion.MAXIMO_AVISADOS, message = "Demasiados eventos avisados")
        List<@NotNull(message = "Un evento avisado no tiene id") Long> eventosAvisados) {

    public static final int MAXIMO_TRAMOS = 5000;
    public static final int MAXIMO_AVISADOS = 500;

    public List<Ubicacion> paradasOVacio() {
        return paradas == null ? List.of() : paradas;
    }

    public List<Long> eventosAvisadosOVacio() {
        return eventosAvisados == null ? List.of() : eventosAvisados;
    }
}
