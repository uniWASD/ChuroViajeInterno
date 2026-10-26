package bo.edu.ucb.churoviaje.dominio.catalogo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TipoCombustibleTest {
    @Test
    void desconocidoNoEsOpcionDeRecarga() {
        assertFalse(new TipoCombustible("DESCONOCIDO", "Desconocido").disponibleParaConductor());
        assertTrue(new TipoCombustible("DIESEL", "Diesel").disponibleParaConductor());
    }
}
