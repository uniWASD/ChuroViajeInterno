package com.churoviaje.backend.dominio.catalogo;

import java.util.List;

/**
 * Opcion de «hace cuanto ocurrio» al reportar un evento (RN-01.4, D-09). Si el conductor no elige, vale la marcada
 * por defecto. El {@code EventoVial} hace la resta con los minutos elegidos.
 */
public record TiempoReporte(int minutos, String nombre, boolean porDefecto) {

    public static final List<TiempoReporte> OPCIONES = List.of(
            new TiempoReporte(0, "Ahora mismo", false),
            new TiempoReporte(5, "Hace 5 minutos", true),
            new TiempoReporte(10, "Hace 10 minutos", false),
            new TiempoReporte(30, "Hace 30 minutos", false));
}
