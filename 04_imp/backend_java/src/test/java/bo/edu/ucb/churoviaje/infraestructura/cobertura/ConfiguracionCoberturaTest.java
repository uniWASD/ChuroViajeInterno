package bo.edu.ucb.churoviaje.infraestructura.cobertura;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfiguracionCoberturaTest {
    private final ConfiguracionCobertura configuracion = new ConfiguracionCobertura();
    private final ObjectMapper mapper = new ObjectMapper();

    private String archivo() {
        return Path.of(System.getProperty("t089.raiz", "../.."),
                "04_imp/datos/mancha_urbana_tarija.geojson").toAbsolutePath().toString();
    }

    @Test
    void permiteBorradorSoloCuandoSeAutorizaEnDesarrollo() throws Exception {
        var cobertura = configuracion.coberturaManchaUrbana(mapper, archivo(), true);
        assertTrue(cobertura.contiene(-21.533912, -64.734297));
    }

    @Test
    void rechazaManchaUrbanaProvisionalEnProduccion() {
        assertThrows(IllegalStateException.class,
                () -> configuracion.coberturaManchaUrbana(mapper, archivo(), false));
    }

    @Test
    void rechazaRutaInexistenteSinBuscarOtroArchivo() {
        assertThrows(IllegalStateException.class,
                () -> configuracion.coberturaManchaUrbana(mapper, "ruta_inexistente.geojson", true));
    }
}
