package com.churoviaje.backend.controladores;

import com.churoviaje.backend.dto.RutaPeticion;
import com.churoviaje.backend.dto.RutaRespuesta;
import com.churoviaje.backend.servicios.RutaServicio;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /rutas} (CU-03): pide el JWT de un conductor, como el resto de la API de la app (RN-03.1). Calcular
 * una ruta no crea nada, así que responde 200 con el DTO, también cuando no hay ruta (D-43).
 */
@RestController
@RequestMapping("/rutas")
public class RutaControlador {

    private final RutaServicio rutaServicio;

    public RutaControlador(RutaServicio rutaServicio) {
        this.rutaServicio = rutaServicio;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public RutaRespuesta calcular(@Valid @RequestBody RutaPeticion peticion) {
        return rutaServicio.calcular(peticion);
    }
}
