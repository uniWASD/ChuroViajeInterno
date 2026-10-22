package com.churoviaje.backend.dominio;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "eventos_viales")
public class EventoVial {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tipo;

    @Embedded
    private Ubicacion ubicacion;

    private OffsetDateTime fechaOcurrencia;

    protected EventoVial() {}

    public EventoVial(String tipo, Ubicacion ubicacion, OffsetDateTime horaConfirmacion, int minutosElegidos) {
        this.tipo = tipo;
        this.ubicacion = ubicacion;
        // RN-01.4 (05_contratos.md): El EventoVial hace la resta de los minutos elegidos
        this.fechaOcurrencia = horaConfirmacion.minusMinutes(minutosElegidos);
    }

    public boolean sigueVigente(OffsetDateTime ahora) {
        return fechaOcurrencia.plusMinutes(60).isAfter(ahora);
    }

    public Long getId() { return id; }
    public String getTipo() { return tipo; }
    public Ubicacion getUbicacion() { return ubicacion; }
    public OffsetDateTime getFechaOcurrencia() { return fechaOcurrencia; }
}