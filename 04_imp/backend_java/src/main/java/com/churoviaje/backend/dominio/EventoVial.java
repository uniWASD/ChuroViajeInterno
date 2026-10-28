package com.churoviaje.backend.dominio;

import java.time.OffsetDateTime;

/** TODO HU-01 (#53): persistir este modelo al incorporar evento_vial en su propia migración. */
public class EventoVial {
    private final String tipo;
    private final OffsetDateTime fechaOcurrencia;
    private final Ubicacion ubicacion;

    // Conserva la firma de T-088, pero no es entidad @Entity (T-089 no crea la tabla).
    public EventoVial(String tipo, OffsetDateTime horaConfirmacion,
            Integer minutosElegidos, Ubicacion ubicacion) {
        this.tipo = java.util.Objects.requireNonNull(tipo, "tipo");
        this.fechaOcurrencia = java.util.Objects.requireNonNull(horaConfirmacion,"horaConfirmacion")
                .minusMinutes(java.util.Objects.requireNonNull(minutosElegidos,"minutosElegidos"));
        this.ubicacion = java.util.Objects.requireNonNull(ubicacion,"ubicacion");
    }

    public Long getId() { return null; } // Sin persistencia hasta HU-01 (#53)
    public String getTipo() { return tipo; }
    public OffsetDateTime getFechaOcurrencia() { return fechaOcurrencia; }
    public Ubicacion getUbicacion() { return ubicacion; }
}
