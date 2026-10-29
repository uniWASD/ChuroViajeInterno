package com.churoviaje.backend.controladores;

import com.churoviaje.backend.dto.LoginPeticion;
import com.churoviaje.backend.dto.SesionRespuesta;
import com.churoviaje.backend.servicios.SesionServicio;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/sesion")
public class SesionControlador {

    private final SesionServicio sesionServicio;

    public SesionControlador(SesionServicio sesionServicio) {
        this.sesionServicio = sesionServicio;
    }

    @PostMapping("/google")
    public ResponseEntity<SesionRespuesta> iniciarSesionConGoogle(@RequestBody LoginPeticion peticion) {
        SesionRespuesta respuesta = sesionServicio.iniciarSesion(peticion);
        return ResponseEntity.ok(respuesta);
    }

    // CP-34: Renovar JWT usando el token de renovación (RN-08.12)
    @PostMapping("/renovar")
    public ResponseEntity<SesionRespuesta> renovarSesion(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        SesionRespuesta respuesta = sesionServicio.renovarToken(refreshToken);
        return ResponseEntity.ok(respuesta);
    }

    // CP-35: Cerrar sesión para invalidar el token de renovación (RN-08.14)
    @PostMapping("/cerrar")
    public ResponseEntity<Void> cerrarSesion(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        sesionServicio.cerrarSesion(refreshToken);
        return ResponseEntity.ok().build();
    }
}