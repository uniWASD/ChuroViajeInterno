package com.churoviaje.backend.dominio;

import java.time.Instant;

/** Las confirmaciones de recarga (V11). La implementación para PostgreSQL vive en {@code infraestructura.pg} (D-41). */
public interface ConfirmacionRecargaRepository {

    /** RN-05.14: si el conductor ya confirmó ese surtidor y tipo después de {@code desde}. */
    boolean existeDesde(long conductorId, long surtidorId, String tipoCodigo, Instant desde);

    void guardar(ConfirmacionRecarga confirmacion);
}
