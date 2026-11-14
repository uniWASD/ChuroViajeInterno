package com.churoviaje.backend.dominio;

import java.util.List;

/** El catálogo de surtidores (V2). Solo lectura. La implementación para PostgreSQL vive en {@code infraestructura.pg}. */
public interface SurtidorRepository {

    /** Todos los surtidores con sus alias, ordenados por nombre. */
    List<Surtidor> todos();
}
