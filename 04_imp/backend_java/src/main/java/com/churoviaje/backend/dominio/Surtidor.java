package com.churoviaje.backend.dominio;

import java.util.List;
import java.util.Objects;

/**
 * Una estación de servicio del catálogo (V2). Los alias son los nombres con que la gente la llama, ya normalizados
 * con {@link com.churoviaje.backend.dominio.catalogo.ClaveAlias}.
 */
public record Surtidor(long id, String nombre, Ubicacion ubicacion, List<String> alias) {

    public Surtidor {
        Objects.requireNonNull(nombre, "nombre");
        Objects.requireNonNull(ubicacion, "ubicacion");
        alias = List.copyOf(alias);
    }
}
