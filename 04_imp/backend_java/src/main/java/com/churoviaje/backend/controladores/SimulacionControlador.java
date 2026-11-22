package com.churoviaje.backend.controladores;

import com.churoviaje.backend.servicios.SimulacionServicio;
import com.churoviaje.backend.servicios.SimulacionServicio.ResultadoBorrado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Mantenimiento del simulador (CU-07): el comando que borra los datos simulados (RN-07.8). */
@RestController
@RequestMapping("/simulacion")
public class SimulacionControlador {

    public record PeticionBorrado(
            @NotBlank(message = "Falta la credencial de prueba") @Size(max = 256, message = "La credencial de prueba no es válida") String credencial) {}

    private final SimulacionServicio simulacion;

    public SimulacionControlador(SimulacionServicio simulacion) {
        this.simulacion = simulacion;
    }

    @PostMapping("/borrar")
    public ResultadoBorrado borrar(@Valid @RequestBody PeticionBorrado peticion) {
        return simulacion.borrarDatosSimulados(peticion.credencial());
    }
}
