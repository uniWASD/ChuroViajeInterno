package com.churoviaje.backend.dominio;

import com.churoviaje.backend.dominio.cobertura.CoberturaManchaUrbana;
import java.util.List;
import java.util.Objects;

/**
 * RN-03.2 / RN-03.3: antes de consultar el motor, validar origen,
 * destino y TODAS las paradas. No convierte PUNTO_INVALIDO en HTTP 422.
 * RN-03.4 (proximidad a una intersección) sigue siendo responsabilidad del motor.
 */
public final class ValidadorPuntosRuta {
    private final CoberturaManchaUrbana cobertura;

    public ValidadorPuntosRuta(CoberturaManchaUrbana cobertura) {
        this.cobertura = Objects.requireNonNull(cobertura, "La cobertura es obligatoria");
    }

    public ResultadoUbicacionRuta validar(Ubicacion origen, Ubicacion destino, List<Ubicacion> paradas) {
        Objects.requireNonNull(origen, "El origen es obligatorio");
        Objects.requireNonNull(destino, "El destino es obligatorio");
        Objects.requireNonNull(paradas, "Use una lista vacía cuando no hay paradas");
        if (origen.validarParaRuta(cobertura) == ResultadoUbicacionRuta.PUNTO_INVALIDO
                || destino.validarParaRuta(cobertura) == ResultadoUbicacionRuta.PUNTO_INVALIDO) {
            return ResultadoUbicacionRuta.PUNTO_INVALIDO;
        }
        for (Ubicacion parada : paradas) {
            if (Objects.requireNonNull(parada, "Parada no puede ser nula")
                    .validarParaRuta(cobertura) == ResultadoUbicacionRuta.PUNTO_INVALIDO) {
                return ResultadoUbicacionRuta.PUNTO_INVALIDO;
            }
        }
        return ResultadoUbicacionRuta.VALIDA;
    }
}
