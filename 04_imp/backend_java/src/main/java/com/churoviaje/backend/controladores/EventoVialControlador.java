package com.churoviaje.backend.controladores;

import com.churoviaje.backend.dominio.EventoVial;
import com.churoviaje.backend.dominio.Ubicacion;
import com.churoviaje.backend.servicios.EventoVialServicio;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.OffsetDateTime;

@RestController
@RequestMapping("/eventos")
public class EventoVialControlador {
    private final EventoVialServicio servicio;

    public EventoVialControlador(EventoVialServicio servicio) {
        this.servicio = servicio;
    }

    public record PeticionReporteEvento(
        @NotBlank String tipo,
        @NotNull @Valid Ubicacion ubicacion,
        @NotNull OffsetDateTime horaConfirmacion,
        @NotNull Integer minutosElegidos
    ) {}

    @PostMapping
    public ResponseEntity<EventoVial> reportarEvento(@Valid @RequestBody PeticionReporteEvento peticion) {
        EventoVial creado = servicio.registrarEjemplo(
            peticion.tipo(),
            peticion.ubicacion(),
            peticion.horaConfirmacion(),
            peticion.minutosElegidos()
        );
        return ResponseEntity.ok(creado);
    }
}