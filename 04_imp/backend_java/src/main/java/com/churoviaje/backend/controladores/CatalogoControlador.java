package com.churoviaje.backend.controladores;

import com.churoviaje.backend.dto.CatalogosRespuesta;
import com.churoviaje.backend.servicios.CatalogoServicio;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/catalogos")
public class CatalogoControlador {

    private final CatalogoServicio catalogoServicio;

    public CatalogoControlador(CatalogoServicio catalogoServicio) {
        this.catalogoServicio = catalogoServicio;
    }

    @GetMapping
    public ResponseEntity<CatalogosRespuesta> obtenerCatalogos() {
        return ResponseEntity.ok(catalogoServicio.obtenerCatalogos());
    }
}