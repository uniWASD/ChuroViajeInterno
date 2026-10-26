package bo.edu.ucb.churoviaje.dominio.catalogo;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServicioBusquedaSurtidoresTest {
    @Test
    void normalizaClaveFueraDelRepositorio() {
        AtomicReference<String> claveEnviada = new AtomicReference<>();
        BuscadorSurtidores stub = clave -> {
            claveEnviada.set(clave);
            return "san geronimo".equals(clave) ? Optional.of(7L) : Optional.empty();
        };
        var servicio = new ServicioBusquedaSurtidores(stub);
        assertEquals(Optional.of(7L), servicio.buscarPorNombreOAlias("  SAN GERÓNIMO  "));
        assertEquals("san geronimo", claveEnviada.get());
    }
}
