package com.churoviaje.backend.dominio.monitoreo;

import com.churoviaje.backend.dominio.Surtidor;
import com.churoviaje.backend.dominio.catalogo.ClaveAlias;
import com.churoviaje.backend.dominio.catalogo.TipoCombustible;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Busca un surtidor por su nombre o por un alias, y un combustible por su nombre o su código, sin importar mayúsculas,
 * tildes ni signos (RN-06.15). Se arma una vez por lote con el catálogo de la base.
 */
public final class CatalogoDeAvisos {

    private final Map<String, Long> surtidores = new HashMap<>();
    private final Map<String, String> combustibles = new HashMap<>();

    public CatalogoDeAvisos(List<Surtidor> surtidores, List<TipoCombustible> combustibles) {
        for (Surtidor s : surtidores) {
            clave(s.nombre()).ifPresent(c -> this.surtidores.put(c, s.id()));
            for (String alias : s.alias()) {
                clave(alias).ifPresent(c -> this.surtidores.putIfAbsent(c, s.id()));
            }
        }
        for (TipoCombustible t : combustibles) {
            clave(t.nombre()).ifPresent(c -> this.combustibles.put(c, t.codigo()));
            clave(t.codigo()).ifPresent(c -> this.combustibles.put(c, t.codigo()));
        }
    }

    public OptionalLong surtidor(String texto) {
        Long id = clave(texto).map(surtidores::get).orElse(null);
        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }

    /** El código del tipo; «Desconocido» también vale (RN-06.8). */
    public Optional<String> combustible(String texto) {
        return clave(texto).map(combustibles::get);
    }

    private static Optional<String> clave(String texto) {
        try {
            return Optional.of(ClaveAlias.normalizar(texto));
        } catch (IllegalArgumentException e) {
            return Optional.empty(); // solo signos o espacios
        }
    }
}
