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

    /** Qué pasó con un dato de ANH al guardarlo (RN-09.14). */
    enum ResultadoAnh {
        /** No había dato de ANH para ese surtidor y tipo, o el que había era más viejo: quedó el nuevo. */
        GUARDADO,
        /** El que había tiene la misma fecha: solo cambió el momento de la lectura. */
        RELEIDO,
        /** El que había es más reciente: no cambió nada. */
        MAS_VIEJO
    }

    /**
     * RN-09.14: guarda un dato de ANH. Un dato más viejo no reemplaza al que hay; con la misma fecha solo se actualiza
     * {@code consultado_en}. La comparación es solo entre datos de ANH y la hace la base en la misma sentencia.
     */
    ResultadoAnh guardarDeAnh(EstadoCombustible estado, Instant ahora);

    /** Todos los datos guardados, de todos los orígenes; la consulta elige cuál mostrar (RN-05.3). */
    List<EstadoCombustible> todos();
}
