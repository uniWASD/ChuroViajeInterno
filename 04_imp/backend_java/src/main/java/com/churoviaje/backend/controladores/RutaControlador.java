package com.churoviaje.backend.controladores;

import com.churoviaje.backend.dto.RevisionRutaPeticion;
import com.churoviaje.backend.dto.RevisionRutaRespuesta;
import com.churoviaje.backend.dto.RutaPeticion;
import com.churoviaje.backend.dto.RutaRespuesta;
import com.churoviaje.backend.servicios.RevisionRutaServicio;
import com.churoviaje.backend.servicios.RutaServicio;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /rutas} (CU-03) y {@code POST /rutas/revision} (CU-04): piden el JWT de un conductor, como el resto de
 * la API de la app (RN-03.1). Ninguna crea nada, así que responden 200 con el DTO, también cuando no hay ruta o no
 * hay una alternativa mejor (D-43).
 */
@RestController
@RequestMapping("/rutas")
public class RutaControlador {

    private final RutaServicio rutaServicio;
    private final RevisionRutaServicio revisionServicio;

    public RutaControlador(RutaServicio rutaServicio, RevisionRutaServicio revisionServicio) {
        this.rutaServicio = rutaServicio;
        this.revisionServicio = revisionServicio;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public RutaRespuesta calcular(@Valid @RequestBody RutaPeticion peticion) {
        return rutaServicio.calcular(peticion);
    }

    /** RN-04.13 a RN-04.16 y RN-04.19: revisa lo que falta de la ruta Activa con el tráfico al día. */
    @PostMapping(path = "/revision", produces = MediaType.APPLICATION_JSON_VALUE)
    public RevisionRutaRespuesta revisar(@Valid @RequestBody RevisionRutaPeticion peticion) {
        return revisionServicio.revisar(peticion);
    }
}
