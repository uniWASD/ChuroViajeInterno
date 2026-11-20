package com.churoviaje.backend.dominio.busqueda;

import com.churoviaje.backend.dominio.Ubicacion;
import java.util.Objects;

/** Una calle o un lugar conocido de Tarija que se puede buscar (D-47). {@code clave} es su identificador de OSM. */
public record Lugar(String clave, String nombre, CategoriaLugar categoria, Ubicacion ubicacion) {

    public Lugar {
        Objects.requireNonNull(clave, "clave");
        Objects.requireNonNull(nombre, "nombre");
        Objects.requireNonNull(categoria, "categoria");
        Objects.requireNonNull(ubicacion, "ubicacion");
    }
}
