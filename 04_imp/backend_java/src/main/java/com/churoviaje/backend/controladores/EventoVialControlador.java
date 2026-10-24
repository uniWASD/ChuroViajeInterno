package com.churoviaje.backend.controladores;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.churoviaje.backend.dominio.EventoVial;
import com.churoviaje.backend.dominio.Ubicacion;
import com.churoviaje.backend.servicios.EventoVialServicio;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/eventos")
public class EventoVialControlador {

    private final EventoVialServicio eventoVialServicio;

    public EventoVialControlador(EventoVialServicio eventoVialServicio) {
        this.eventoVialServicio = eventoVialServicio;
    }

    // 1. DTO de entrada: Solo acepta lo que dicta el contrato
    public record PeticionReporteEvento(
            @NotBlank String tipo,
            @NotNull @Valid Ubicacion ubicacion,
            @NotNull LocalDateTime horaConfirmacion,
            @NotNull Integer minutosElegidos
    ) {}

    // 2. DTO de salida: No expone la entidad JPA
    public record EventoVialResponseDto(Long id, String tipo, Ubicacion ubicacion, LocalDateTime momentoOcurrencia) {}

    @PostMapping
    public ResponseEntity<EventoVialResponseDto> reportarEvento(@Valid @RequestBody PeticionReporteEvento peticion) {
        
        // 3. Pasamos los datos al servicio para que él construya la entidad
        EventoVial guardado = eventoVialServicio.registrarEvento(
                peticion.tipo(),
                peticion.ubicacion(),
                peticion.horaConfirmacion(),
                peticion.minutosElegidos()
        );

        // 4. Devolvemos los datos reales, no estáticos
        EventoVialResponseDto respuesta = new EventoVialResponseDto(
                guardado.getId(),
                guardado.getTipo(),
                guardado.getUbicacion(),
                guardado.getFechaOcurrencia()
        );
        
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }
}