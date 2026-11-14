package com.churoviaje.backend.controladores;

import com.churoviaje.backend.dominio.catalogo.CatalogosRespuesta;
import com.churoviaje.backend.servicios.CatalogoServicio;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /catalogos}: pide el JWT de un conductor como todas las rutas de la app (RNF-08). */
@RestController
@RequestMapping("/catalogos")
public class CatalogoControlador {

    private final CatalogoServicio catalogoServicio;

    public CatalogoControlador(CatalogoServicio catalogoServicio) {
        this.catalogoServicio = catalogoServicio;
    }

    @GetMapping
    public CatalogosRespuesta obtenerCatalogos() {
        return catalogoServicio.obtenerCatalogos();
    }
}
