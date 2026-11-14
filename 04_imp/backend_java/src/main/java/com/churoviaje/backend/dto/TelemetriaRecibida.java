package com.churoviaje.backend.dto;

/**
 * Respuesta de {@code POST /telemetria}: el backend recibió las lecturas y se las pasa al motor sin esperar el
 * análisis. Solo dice cuántas llegaron; nunca si hubo congestión.
 */
public record TelemetriaRecibida(String estado, int lecturas) {

    public static TelemetriaRecibida de(int lecturas) {
        return new TelemetriaRecibida("RECIBIDO", lecturas);
    }
}
