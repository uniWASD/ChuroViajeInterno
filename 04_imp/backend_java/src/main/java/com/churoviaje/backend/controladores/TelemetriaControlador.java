package com.churoviaje.backend.controladores;

import com.churoviaje.backend.dto.TelemetriaPeticion;
import com.churoviaje.backend.dto.TelemetriaRecibida;
import com.churoviaje.backend.seguridad.ConductorAutenticado;
import com.churoviaje.backend.servicios.TelemetriaServicio;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /telemetria} (CU-04): pide el JWT de un conductor (RN-04.4). Responde 202 «recibido» apenas toma las
 * lecturas, sin esperar el análisis del motor.
 */
@RestController
@RequestMapping("/telemetria")
public class TelemetriaControlador {

    private final TelemetriaServicio telemetria;

    public TelemetriaControlador(TelemetriaServicio telemetria) {
        this.telemetria = telemetria;
    }

    @PostMapping
    public ResponseEntity<TelemetriaRecibida> recibir(@AuthenticationPrincipal ConductorAutenticado conductor,
            @Valid @RequestBody TelemetriaPeticion peticion) {
        int lecturas = telemetria.recibir(conductor.id(), peticion);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(TelemetriaRecibida.de(lecturas));
    }
}
