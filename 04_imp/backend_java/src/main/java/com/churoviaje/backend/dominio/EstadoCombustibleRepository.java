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
        /**
         * No había dato de ANH para ese surtidor y tipo, o el que había tenía un {@code updated_at} más viejo: quedaron
         * los litros nuevos. La fecha del dato solo cambia si ANH cambió lo que dice (D-50).
         */
        GUARDADO,
        /** El que había tiene el mismo {@code updated_at}: solo cambió el momento de la lectura. */
        RELEIDO,
        /** El que había tiene un {@code updated_at} más reciente: no cambió nada. */
        MAS_VIEJO
    }

    /**
     * RN-09.14: guarda una lectura de ANH, cuya {@code fecha} es el {@code updated_at} que informa ANH. Una lectura más
     * vieja que la última aceptada no entra; con el mismo {@code updated_at} solo se actualiza {@code consultado_en}.
     * Con uno más nuevo se guardan los litros y el estado de ANH, y la fecha del dato guardado, que es desde cuándo ANH
     * dice lo mismo, solo pasa al {@code updated_at} nuevo si cambió la disponibilidad o subieron los litros (D-50). La
     * comparación es solo entre datos de ANH y la hace la base en la misma sentencia.
     */
    ResultadoAnh guardarDeAnh(EstadoCombustible estado, Instant ahora);

    /** Todos los datos guardados, de todos los orígenes; la consulta elige cuál mostrar (RN-05.3). */
    List<EstadoCombustible> todos();
}
