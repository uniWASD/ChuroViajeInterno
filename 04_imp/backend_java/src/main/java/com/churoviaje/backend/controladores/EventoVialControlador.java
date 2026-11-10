package com.churoviaje.backend.controladores;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.churoviaje.backend.dominio.EventoVial;
import com.churoviaje.backend.dto.EventoVialPeticionDTO;
import com.churoviaje.backend.dto.EventoVialRespuestaDTO;
import com.churoviaje.backend.seguridad.ConductorAutenticado;
import com.churoviaje.backend.servicios.EventoVialServicio;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/eventos")
public class EventoVialControlador {

    private final EventoVialServicio eventoVialServicio;

    public EventoVialControlador(EventoVialServicio eventoVialServicio) {
        this.eventoVialServicio = eventoVialServicio;
    }

    @PostMapping
    public ResponseEntity<EventoVialRespuestaDTO> reportarEvento(
            @Valid @RequestBody EventoVialPeticionDTO peticion,
            @AuthenticationPrincipal ConductorAutenticado conductor) {
        
        // Pasamos los datos extraídos al servicio, usando el ID real del token (conductor.id())
        EventoVial guardado = eventoVialServicio.registrarEvento(
                peticion.tipo(),
                peticion.ubicacion().lon(),
                peticion.ubicacion().lat(),
                peticion.horaConfirmacion(),
                peticion.minutos(),
                conductor.id()
        );

        // Construimos la respuesta con los datos generados por la BD
        EventoVialRespuestaDTO respuesta = new EventoVialRespuestaDTO(
                guardado.getId(),
                guardado.getTipo().name(),
                guardado.getEstado(),
                guardado.getHoraOcurrencia(),
                guardado.getVotos()
        );
        
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }
}