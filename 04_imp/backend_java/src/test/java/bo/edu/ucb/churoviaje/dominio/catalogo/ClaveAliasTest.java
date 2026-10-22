package bo.edu.ucb.churoviaje.dominio.catalogo;

import bo.edu.ucb.churoviaje.dominio.catalogo.ClaveAlias;
import bo.edu.ucb.churoviaje.dominio.catalogo.TipoCombustible;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClaveAliasTest {
    @Test
    void normalizaAcentosMayusculasYEspacios() {
        assertEquals("san geronimo", ClaveAlias.normalizar("  SAN   GERÓNIMO  "));
        assertEquals("moto mendez", ClaveAlias.normalizar("Moto Méndez"));
        assertEquals("ypfb morros blancos", ClaveAlias.normalizar("YPFB Morros Blancos"));
        assertThrows(IllegalArgumentException.class, () -> ClaveAlias.normalizar(" - "));
    }

    @Test
    void desconocidoNoEsOpcionDeRecarga() {
        assertFalse(new TipoCombustible("DESCONOCIDO", "Desconocido").disponibleParaConductor());
        assertTrue(new TipoCombustible("DIESEL", "Diesel").disponibleParaConductor());
    }
}
