package com.churoviaje.backend.dto;

import java.util.List;

/**
 * {@code GET /monitoreo/surtidores}: lo que el servicio de WhatsApp/LLM le pasa al LLM (RN-06.7, RN-06.8). Los
 * combustibles son los cuatro del catálogo, sin «Desconocido», y las filas son las de P-15.
 */
public record CatalogoMonitoreo(List<SurtidorMonitoreo> surtidores, List<String> combustibles, List<String> filas) {

    public record SurtidorMonitoreo(String nombre, List<String> alias) {}
}
