package com.churoviaje.backend.dominio;

import java.time.OffsetDateTime;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// TODO HU-01 (#53): Implementar modelo completo
@Entity
@Table(name = "evento_vial")
public class EventoVial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String tipo;
    private OffsetDateTime fechaOcurrencia;

    @Embedded
    private Ubicacion ubicacion;

    public EventoVial() {}

    public EventoVial(String tipo, OffsetDateTime horaConfirmacion, Integer minutosElegidos, Ubicacion ubicacion) {
        this.tipo = tipo;
        this.fechaOcurrencia = horaConfirmacion.minusMinutes(minutosElegidos);
        this.ubicacion = ubicacion;
    }

    public Long getId() { return id; }
    public String getTipo() { return tipo; }
    public OffsetDateTime getFechaOcurrencia() { return fechaOcurrencia; }
    public Ubicacion getUbicacion() { return ubicacion; }
}