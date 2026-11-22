package com.churoviaje.backend.dto;

import com.churoviaje.backend.dominio.ConfirmacionRecarga;
import com.churoviaje.backend.dominio.EventoVialRespuesta;
import java.time.OffsetDateTime;

/**
 * Respuesta de {@code POST /surtidores/{id}/confirmaciones} (CU-05): el dato que dejó la confirmación, sin nada del
 * conductor (RN-05.7). La fecha sale con la hora de Bolivia, como en {@code GET /surtidores}.
 */
public record ConfirmacionRespuesta(long surtidorId, String tipo, boolean disponible, String origen,
        OffsetDateTime fecha) {

    public static ConfirmacionRespuesta de(ConfirmacionRecarga c) {
        var estado = c.comoEstado();
        return new ConfirmacionRespuesta(c.surtidorId(), c.tipoCodigo(), estado.disponible(), estado.origen().name(),
                OffsetDateTime.ofInstant(c.fecha(), EventoVialRespuesta.ZONA));
    }
}
