package com.churoviaje.backend.controladores;

import com.churoviaje.backend.dominio.Ubicacion;
import com.churoviaje.backend.dto.ConfirmacionRespuesta;
import com.churoviaje.backend.dto.SurtidoresRespuesta;
import com.churoviaje.backend.errores.ExcepcionRechazo;
import com.churoviaje.backend.seguridad.ConductorAutenticado;
import com.churoviaje.backend.servicios.SurtidorServicio;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /surtidores?latitud=..&longitud=..} (CU-05): pide el JWT de un conductor (RN-05.1, SecurityConfig). */
@RestController
@RequestMapping("/surtidores")
public class SurtidorControlador {

    private static final BigDecimal NOVENTA = BigDecimal.valueOf(90);
    private static final BigDecimal CIENTO_OCHENTA = BigDecimal.valueOf(180);

    private final SurtidorServicio surtidores;

    public SurtidorControlador(SurtidorServicio surtidores) {
        this.surtidores = surtidores;
    }

    @GetMapping
    public SurtidoresRespuesta consultar(@RequestParam BigDecimal latitud, @RequestParam BigDecimal longitud) {
        if (latitud.abs().compareTo(NOVENTA) > 0 || longitud.abs().compareTo(CIENTO_OCHENTA) > 0) {
            throw new ExcepcionRechazo(HttpStatus.BAD_REQUEST, "PETICION_INVALIDA", "La ubicación no es válida.");
        }
        return surtidores.consultar(new Ubicacion(latitud, longitud));
    }

    /** Lo que manda la app al confirmar una recarga: el tipo y la ubicación donde detectó la parada (HU-12). */
    public record PeticionConfirmacion(
            @NotBlank(message = "Falta el tipo de combustible") String tipo,
            @NotNull(message = "Falta la ubicación") @Valid Ubicacion ubicacion) {}

    /** RN-05.9 a RN-05.14: registra la confirmación y devuelve el dato que dejó. */
    @PostMapping("/{id}/confirmaciones")
    public ResponseEntity<ConfirmacionRespuesta> confirmar(@AuthenticationPrincipal ConductorAutenticado conductor,
            @PathVariable("id") long id, @Valid @RequestBody PeticionConfirmacion peticion) {
        var confirmacion = surtidores.confirmarRecarga(conductor.id(), id, peticion.tipo(), peticion.ubicacion());
        return ResponseEntity.status(HttpStatus.CREATED).body(ConfirmacionRespuesta.de(confirmacion));
    }
}
