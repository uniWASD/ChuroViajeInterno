package com.churoviaje.backend.dominio;

import java.util.Optional;

/** Guarda y recupera conductores (CU-08). La implementacion para PostgreSQL vive en {@code infraestructura.pg} (D-41). */
public interface ConductorRepository {

    Optional<Conductor> buscarPorId(long id);

    /**
     * Busca al conductor y lo deja bloqueado hasta que termine la transaccion, para que dos reportes suyos que
     * llegan juntos se revisen de a uno (RN-01.8, RN-01.9). Hay que llamarlo dentro de una transaccion.
     */
    Optional<Conductor> buscarYBloquear(long id);

    /** RN-08.4: el conductor de esa cuenta de Google, si ya entro alguna vez. */
    Optional<Conductor> buscarPorGoogleId(String googleId);

    /**
     * Guarda un conductor nuevo y lo devuelve con su identificador. Si su cuenta de Google ya esta registrada
     * (dos primeros ingresos que llegan a la vez), no crea otro y devuelve el que ya estaba (RN-08.4).
     */
    Conductor guardarNuevo(Conductor conductor);
}
