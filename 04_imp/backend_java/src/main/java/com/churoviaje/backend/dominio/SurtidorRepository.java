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

    /** RN-05.13: el surtidor {@code id} con su distancia en metros a {@code punto}, medida en PostGIS; vacío si no existe. */
    java.util.Optional<Cercano> buscarConDistancia(long id, Ubicacion punto);

    /** RN-09.9 y RN-09.10: el surtidor de cada estación de ANH vinculada, por su {@code anh_id} (V12). */
    java.util.Map<Integer, Long> vinculadosAnh();
}
