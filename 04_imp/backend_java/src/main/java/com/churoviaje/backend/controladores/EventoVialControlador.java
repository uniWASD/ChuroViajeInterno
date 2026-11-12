package com.churoviaje.backend.controladores;

import com.churoviaje.backend.dominio.Ubicacion;
import com.churoviaje.backend.dto.EventoVialRespuesta;
import com.churoviaje.backend.dto.EventosRespuesta;
import com.churoviaje.backend.seguridad.ConductorAutenticado;
import com.churoviaje.backend.servicios.ConsultaEventosServicio;
import com.churoviaje.backend.servicios.EventoVialServicio;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Eventos viales: reportarlos (CU-01) y consultarlos (CU-02). */
@RestController
@RequestMapping("/eventos")
public class EventoVialControlador {

    private final EventoVialServicio eventoVialServicio;
    private final ConsultaEventosServicio consultaEventos;

    public EventoVialControlador(EventoVialServicio eventoVialServicio, ConsultaEventosServicio consultaEventos) {
        this.eventoVialServicio = eventoVialServicio;
        this.consultaEventos = consultaEventos;
    }

    /**
     * Lo que manda la app al reportar (RN-01.2). Los minutos son opcionales: sin ellos, el evento ocurrio hace 5
     * minutos (RN-01.4).
     */
    public record PeticionReporteEvento(
            @NotBlank(message = "Falta el tipo de evento") String tipo,
            @NotNull(message = "Falta la ubicación") @Valid Ubicacion ubicacion,
            @NotNull(message = "Falta la hora de confirmación") OffsetDateTime horaConfirmacion,
            Integer minutosElegidos) {}

    @PostMapping
    public ResponseEntity<EventoVialRespuesta> reportarEvento(@AuthenticationPrincipal ConductorAutenticado conductor,
            @Valid @RequestBody PeticionReporteEvento peticion) {
        var evento = eventoVialServicio.registrarEvento(conductor.id(), peticion.tipo(), peticion.ubicacion(),
                peticion.horaConfirmacion(), peticion.minutosElegidos());
        return ResponseEntity.status(HttpStatus.CREATED).body(EventoVialRespuesta.de(evento));
    }

    /**
     * RN-02.2 a RN-02.6: los eventos vigentes, o solo los cambios desde la consulta anterior si la app manda su
     * marca en {@code desde}.
     */
    @GetMapping
    public EventosRespuesta consultarEventos(@RequestParam(name = "desde", required = false) String desde) {
        return consultaEventos.consultar(desde);
    }
}
