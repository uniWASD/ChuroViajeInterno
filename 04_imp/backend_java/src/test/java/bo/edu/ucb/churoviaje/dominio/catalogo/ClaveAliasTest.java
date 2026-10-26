package bo.edu.ucb.churoviaje.dominio.catalogo;

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

}
