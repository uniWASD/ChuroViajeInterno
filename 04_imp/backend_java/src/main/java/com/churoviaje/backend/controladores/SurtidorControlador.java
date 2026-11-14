package com.churoviaje.backend.controladores;

import com.churoviaje.backend.dominio.Ubicacion;
import com.churoviaje.backend.dto.SurtidoresRespuesta;
import com.churoviaje.backend.errores.ExcepcionRechazo;
import com.churoviaje.backend.servicios.SurtidorServicio;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
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
}
