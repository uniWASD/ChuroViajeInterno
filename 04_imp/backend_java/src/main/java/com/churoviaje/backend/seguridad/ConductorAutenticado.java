package com.churoviaje.backend.seguridad;

/**
 * Quien hace la peticion cuando entro con el JWT de un conductor. Los controladores lo reciben con
 * {@code @AuthenticationPrincipal}.
 *
 * @param id          el conductor
 * @param pruebaCarga el identificador de la prueba de carga si es un usuario fantasma (CU-07); {@code null} si es un
 *                    conductor real
 */
public record ConductorAutenticado(long id, String pruebaCarga) {

    public ConductorAutenticado(long id) {
        this(id, null);
    }

    /** RN-07.4: un usuario fantasma del simulador. */
    public boolean simulado() {
        return pruebaCarga != null;
    }
}
