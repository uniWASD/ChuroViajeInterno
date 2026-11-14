package com.churoviaje.backend.dominio;

import java.time.Instant;
import java.util.List;

/** Los estados de combustible (V8). La implementación para PostgreSQL vive en {@code infraestructura.pg} (D-41). */
public interface EstadoCombustibleRepository {

    /**
     * Guarda el dato si no hay otro del mismo surtidor, tipo y origen, o si el que hay es más viejo (RN-06.17). La
     * comparación la hace la base en la misma sentencia, así dos lotes a la vez no se pisan.
     *
     * @return verdadero si el dato quedó guardado
     */
    boolean guardarSiEsMasReciente(EstadoCombustible estado, Instant ahora);

    /** Todos los datos guardados, de todos los orígenes; la consulta elige cuál mostrar (RN-05.3). */
    List<EstadoCombustible> todos();
}
