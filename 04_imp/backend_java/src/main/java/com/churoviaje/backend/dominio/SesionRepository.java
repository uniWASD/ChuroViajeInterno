package com.churoviaje.backend.dominio;

import java.util.Optional;

/**
 * Guarda, busca y borra sesiones por la huella de su token de renovacion (CU-08, D-34). La implementacion para
 * PostgreSQL vive en {@code infraestructura.pg} (D-41).
 */
public interface SesionRepository {

    /** Guarda una sesion nueva de un conductor ya guardado. */
    void guardar(Sesion sesion);

    /** La sesion de ese token, con su conductor, o vacio si no existe. */
    Optional<Sesion> buscarPorHuellaDeToken(String huella);

    /** Invalida el token de renovacion (RN-08.14). Borrar una sesion que ya no existe no es un error. */
    void borrarPorHuellaDeToken(String huella);
}
