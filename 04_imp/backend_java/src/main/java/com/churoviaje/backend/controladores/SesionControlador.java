package com.churoviaje.backend.controladores;

import com.churoviaje.backend.servicios.SesionServicio;
import com.churoviaje.backend.servicios.SesionServicio.JwtRenovado;
import com.churoviaje.backend.servicios.SesionServicio.SesionIniciada;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controlador REST de sesion (CU-08). Son las unicas rutas que no piden JWT. */
@RestController
@RequestMapping("/sesion")
public class SesionControlador {

    public record PeticionGoogle(
            @NotBlank(message = "Falta el token de Google") @Size(max = 8192, message = "El token de Google no es válido") String tokenGoogle) {}

    public record PeticionRenovacion(
            @NotBlank(message = "Falta el token de renovación") @Size(max = 256, message = "El token de renovación no es válido") String tokenRenovacion) {}

    private final SesionServicio sesionServicio;

    public SesionControlador(SesionServicio sesionServicio) {
        this.sesionServicio = sesionServicio;
    }

    @PostMapping("/google")
    public SesionIniciada iniciarSesion(@Valid @RequestBody PeticionGoogle peticion) {
        return sesionServicio.iniciarSesion(peticion.tokenGoogle());
    }

    @PostMapping("/renovar")
    public JwtRenovado renovar(@Valid @RequestBody PeticionRenovacion peticion) {
        return sesionServicio.renovar(peticion.tokenRenovacion());
    }

    @PostMapping("/cerrar")
    public ResponseEntity<Void> cerrar(@Valid @RequestBody PeticionRenovacion peticion) {
        sesionServicio.cerrar(peticion.tokenRenovacion());
        return ResponseEntity.noContent().build();
    }
}
