package com.churoviaje.backend.motor;

import com.churoviaje.backend.dominio.EventoVial;
import com.churoviaje.backend.dominio.Ubicacion;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Lo que el motor tiene que saber de un evento para calcular con el trafico al dia (D-14): que nacio, que cambio
 * su vencimiento, que se retiro o que vencio. Los servicios lo publican como evento interno de Spring y
 * {@link TraficoDelMotor} se lo pasa al nucleo como un mensaje {@code actualizar_trafico}.
 */
public record AvisoTrafico(long eventoId, Accion accion, String tipo, String nivel, String tramo, Ubicacion ubicacion,
        Instant vence) {

    /** Las fechas van con la zona horaria de Bolivia, como en el contrato. */
    private static final ZoneId ZONA = ZoneId.of("America/La_Paz");
    /** Siempre con segundos, como en el ejemplo del contrato: {@code 2026-10-06T16:02:00-04:00}. */
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    public enum Accion {
        NACE("nace"),
        CAMBIA_VENCIMIENTO("cambia_vencimiento"),
        RETIRA("retira"),
        VENCE("vence");

        private final String enMensaje;

        Accion(String enMensaje) {
            this.enMensaje = enMensaje;
        }

        /** «nace» y «cambia_vencimiento» guardan el evento en el nucleo; «retira» y «vence» lo quitan. */
        public boolean guardaElEvento() {
            return this == NACE || this == CAMBIA_VENCIMIENTO;
        }
    }

    public AvisoTrafico {
        Objects.requireNonNull(accion, "accion");
        if (accion.guardaElEvento()) {
            Objects.requireNonNull(tipo, "tipo");
            Objects.requireNonNull(ubicacion, "ubicacion");
            Objects.requireNonNull(vence, "vence");
        }
    }

    public static AvisoTrafico nace(EventoVial evento) {
        return guardar(Accion.NACE, evento);
    }

    /** La congestion que se extiende cambia su vencimiento (RN-04.11, sprint 5). */
    public static AvisoTrafico cambiaVencimiento(EventoVial evento) {
        return guardar(Accion.CAMBIA_VENCIMIENTO, evento);
    }

    public static AvisoTrafico retira(long eventoId) {
        return new AvisoTrafico(eventoId, Accion.RETIRA, null, null, null, null, null);
    }

    public static AvisoTrafico vence(long eventoId) {
        return new AvisoTrafico(eventoId, Accion.VENCE, null, null, null, null, null);
    }

    private static AvisoTrafico guardar(Accion accion, EventoVial evento) {
        return new AvisoTrafico(evento.getId(), accion, evento.getTipo().codigo(), evento.getNivel(),
                evento.getTramoId(), evento.getUbicacion(), evento.venceEn());
    }

    /** El cuerpo del mensaje {@code actualizar_trafico}, sin {@code id} ni {@code operacion}, que pone el wrapper. */
    public ObjectNode aMensaje(ObjectMapper json) {
        ObjectNode mensaje = json.createObjectNode();
        mensaje.put("evento", String.valueOf(eventoId));
        mensaje.put("accion", accion.enMensaje);
        if (accion.guardaElEvento()) {
            mensaje.put("tipo", tipo);
            if (nivel != null) {
                mensaje.put("nivel", nivel);
            }
            if (tramo != null) {
                mensaje.put("tramo", tramo); // D-18: la congestión afecta solo el sentido donde se midió
            }
            mensaje.putArray("ubicacion").add(ubicacion.latitud()).add(ubicacion.longitud());
            mensaje.put("vence", FORMATO.format(OffsetDateTime.ofInstant(vence, ZONA)));
        }
        return mensaje;
    }
}
