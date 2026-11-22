package com.churoviaje.backend.controladores;

import com.churoviaje.backend.servicios.SesionServicio;
import com.churoviaje.backend.servicios.SesionServicio.JwtRenovado;
import com.churoviaje.backend.servicios.SesionServicio.SesionIniciada;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

    /**
     * Lo que manda el simulador en {@code POST /sesion/prueba} (CU-07). El número de usuario va de 1 a 200, el
     * máximo de usuarios fantasma de P-18.
     */
    public record PeticionPrueba(
            @NotBlank(message = "Falta la credencial de prueba") @Size(max = 256, message = "La credencial de prueba no es válida") String credencial,
            @NotBlank(message = "Falta el identificador de la prueba de carga")
            @Pattern(regexp = "[A-Za-z0-9._-]{1,64}", message = "El identificador de la prueba de carga solo lleva letras, números, punto, guion y guion bajo, hasta 64") String pruebaId,
            @NotNull(message = "Falta el número de usuario fantasma")
            @Min(value = 1, message = "El número de usuario fantasma va de 1 a 200")
            @Max(value = 200, message = "El número de usuario fantasma va de 1 a 200") Integer usuario) {}

    private final SesionServicio sesionServicio;

    public SesionControlador(SesionServicio sesionServicio) {
        this.sesionServicio = sesionServicio;
    }

    @PostMapping("/google")
    public SesionIniciada iniciarSesion(@Valid @RequestBody PeticionGoogle peticion) {
        return sesionServicio.iniciarSesion(peticion.tokenGoogle());
    }

    /** RN-07.4 y RN-07.5: el JWT de un usuario fantasma, solo donde el ambiente lo permite. */
    @PostMapping("/prueba")
    public JwtRenovado iniciarSesionDePrueba(@Valid @RequestBody PeticionPrueba peticion) {
        return sesionServicio.iniciarSesionDePrueba(peticion.credencial(), peticion.pruebaId(), peticion.usuario());
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
