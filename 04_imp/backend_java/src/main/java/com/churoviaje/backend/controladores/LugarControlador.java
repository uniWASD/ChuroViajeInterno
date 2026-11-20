package com.churoviaje.backend.controladores;

import com.churoviaje.backend.dominio.Ubicacion;
import com.churoviaje.backend.dominio.busqueda.TextoBusqueda;
import com.churoviaje.backend.dto.LugaresRespuesta;
import com.churoviaje.backend.errores.ExcepcionRechazo;
import com.churoviaje.backend.servicios.BusquedaServicio;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /lugares?texto=..[&latitud=..&longitud=..]} (D-47, CU-03): busca calles, lugares y surtidores. Pide el
 * JWT de un conductor (SecurityConfig). La ubicación es opcional y solo sirve para ordenar por cercanía.
 */
@RestController
@RequestMapping("/lugares")
public class LugarControlador {

    private static final BigDecimal NOVENTA = BigDecimal.valueOf(90);
    private static final BigDecimal CIENTO_OCHENTA = BigDecimal.valueOf(180);
    private static final int LARGO_MAXIMO = 100;

    private final BusquedaServicio busqueda;

    public LugarControlador(BusquedaServicio busqueda) {
        this.busqueda = busqueda;
    }

    @GetMapping
    public LugaresRespuesta buscar(@RequestParam String texto,
            @RequestParam(required = false) BigDecimal latitud,
            @RequestParam(required = false) BigDecimal longitud) {
        if (texto.length() > LARGO_MAXIMO || TextoBusqueda.normalizar(texto).replace(" ", "").length() < 2) {
            throw new ExcepcionRechazo(HttpStatus.BAD_REQUEST, "PETICION_INVALIDA", "Escribe al menos 2 letras.");
        }
        if ((latitud == null) != (longitud == null)) {
            throw new ExcepcionRechazo(HttpStatus.BAD_REQUEST, "PETICION_INVALIDA", "La ubicación no es válida.");
        }
        Ubicacion cerca = null;
        if (latitud != null) {
            if (latitud.abs().compareTo(NOVENTA) > 0 || longitud.abs().compareTo(CIENTO_OCHENTA) > 0) {
                throw new ExcepcionRechazo(HttpStatus.BAD_REQUEST, "PETICION_INVALIDA", "La ubicación no es válida.");
            }
            cerca = new Ubicacion(latitud, longitud);
        }
        return busqueda.buscar(texto, cerca);
    }
}
