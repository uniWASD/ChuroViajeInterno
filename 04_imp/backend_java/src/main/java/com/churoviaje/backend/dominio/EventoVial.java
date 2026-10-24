package com.churoviaje.backend.dominio;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// TODO HU-01 (#53): Implementar modelo completo y tabla evento_vial
@Entity
@Table(name = "eventos_viales")
public class EventoVial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String tipo;
    private LocalDateTime fechaOcurrencia;

    public EventoVial() {}

    public EventoVial(String tipo, LocalDateTime horaConfirmacion, Integer minutosElegidos) {
        this.tipo = tipo;
        this.fechaOcurrencia = horaConfirmacion.minusMinutes(minutosElegidos);
    }

    public Long getId() { return id; }
    public String getTipo() { return tipo; }
    public LocalDateTime getFechaOcurrencia() { return fechaOcurrencia; }
    
    // Método temporal para satisfacer el DTO de respuesta
    public Ubicacion getUbicacion() {
        return new Ubicacion(java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO);
    }
}