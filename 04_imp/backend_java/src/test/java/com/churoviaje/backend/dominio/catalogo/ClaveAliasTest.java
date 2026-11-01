package com.churoviaje.backend.dominio.catalogo;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ClaveAliasTest {
    @Test void normalizaTildesMayusculasEspaciosYPuntuacion() {
        assertEquals("ypfb morros blancos", ClaveAlias.normalizar("YPFB Morros Blancos"));
        assertEquals("san martin", ClaveAlias.normalizar(" San  Martín "));
        assertEquals("estacion s r l", ClaveAlias.normalizar("Estación S.R.L."));
    }
    @Test void rechazaVacio() {
        assertThrows(IllegalArgumentException.class, () -> ClaveAlias.normalizar("---"));
    }
}
