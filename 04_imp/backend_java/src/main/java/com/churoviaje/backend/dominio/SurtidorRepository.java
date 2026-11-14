package com.churoviaje.backend.dominio;

import java.util.List;

/** El catálogo de surtidores (V2). Solo lectura. La implementación para PostgreSQL vive en {@code infraestructura.pg}. */
public interface SurtidorRepository {

    /** Todos los surtidores con sus alias, ordenados por nombre. */
    List<Surtidor> todos();

    /** Un surtidor con su distancia en metros a un punto. */
    record Cercano(Surtidor surtidor, double distanciaM) {}

    /** RN-05.2: todos los surtidores, del más cercano al más lejano de {@code punto}, medido en PostGIS. */
    List<Cercano> porCercania(Ubicacion punto);
}
