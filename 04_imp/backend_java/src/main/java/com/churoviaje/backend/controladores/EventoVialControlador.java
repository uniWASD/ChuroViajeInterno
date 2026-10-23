package com.churoviaje.backend.controladores;

import com.churoviaje.backend.dominio.EventoVial;
import com.churoviaje.backend.servicios.EventoVialServicio;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/eventos")
public class EventoVialControlador {

    private final EventoVialServicio eventoVialServicio;

    public EventoVialControlador(EventoVialServicio eventoVialServicio) {
        this.eventoVialServicio = eventoVialServicio;
    }

    // Record de respuesta propio para el controlador (evita exponer la entidad JPA)
    public record EventoVialResponseDto(Long id, String tipo, String descripcion, boolean activo) {}

    @PostMapping
    public ResponseEntity<EventoVialResponseDto> reportarEvento(@Valid @RequestBody EventoVial evento) {
        EventoVial guardado = eventoVialServicio.registrarEjemplo(evento);
        EventoVialResponseDto respuesta = new EventoVialResponseDto(
            guardado.getId(), 
            "REPORTE_VIAL", 
            "Evento registrado correctamente", 
            true
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }
}