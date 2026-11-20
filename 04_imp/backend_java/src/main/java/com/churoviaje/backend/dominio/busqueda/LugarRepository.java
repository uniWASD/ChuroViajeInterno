package com.churoviaje.backend.dominio.busqueda;

import com.churoviaje.backend.dominio.Ubicacion;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Los lugares que se pueden buscar (V10). La implementación para PostgreSQL vive en {@code infraestructura.pg}. */
public interface LugarRepository {

    /** El extracto cargado, si hay uno. */
    record Extracto(LocalDate fecha, int cantidad) {}

    Optional<Extracto> extractoCargado();

    /** Borra los lugares y guarda estos, con la fecha de su extracto. Debe correr dentro de una transacción. */
    void reemplazar(LocalDate fechaExtracto, List<Lugar> lugares);

    /** Un lugar encontrado, con su distancia en metros si se dio un punto. */
    record Encontrado(String nombre, String nombreNormalizado, CategoriaLugar categoria, Ubicacion ubicacion,
            Double distanciaM) {}

    /**
     * Los lugares cuyo nombre tiene todas las {@code palabras} al comienzo de alguna de sus palabras, del más cercano
     * a {@code cerca} al más lejano (o por nombre si {@code cerca} es nulo), como mucho {@code limite}.
     */
    List<Encontrado> buscar(List<String> palabras, Ubicacion cerca, int limite);
}
