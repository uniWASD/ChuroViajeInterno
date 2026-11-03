package com.churoviaje.backend.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.churoviaje.backend.dominio.catalogo.TiempoReporte;
import com.churoviaje.backend.dominio.catalogo.TipoCombustible;
import com.churoviaje.backend.dominio.catalogo.ValorFila;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogoDominioTest {

    @Test
    void soloDesconocidoNoSePuedeElegirAlConfirmarUnaRecarga() {
        assertFalse(new TipoCombustible("DESCONOCIDO", "Desconocido").seleccionable());
        assertTrue(new TipoCombustible("DIESEL_ULS", "Diesel ULS").seleccionable());
    }

    @Test
    void elReporteOfreceCuatroTiemposYPorDefectoVale5Minutos() {
        assertEquals(List.of(0, 5, 10, 30), TiempoReporte.OPCIONES.stream().map(TiempoReporte::minutos).toList());
        assertEquals(List.of(5), TiempoReporte.OPCIONES.stream().filter(TiempoReporte::porDefecto).map(TiempoReporte::minutos).toList());
    }

    @Test
    void laFilaTieneCuatroValores() {
        assertEquals(4, ValorFila.values().length);
    }
}
