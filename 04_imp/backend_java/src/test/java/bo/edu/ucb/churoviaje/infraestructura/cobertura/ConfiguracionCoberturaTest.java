package bo.edu.ucb.churoviaje.infraestructura.cobertura;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfiguracionCoberturaTest {
    private final ConfiguracionCobertura configuracion = new ConfiguracionCobertura();
    private final ObjectMapper mapper = new ObjectMapper();

    private String archivo() throws Exception {
        var recurso = getClass().getResource("/fixtures/mancha_urbana_tarija_provisional.geojson");
        return Path.of(recurso.toURI()).toString();
    }

    @Test
    void permiteBorradorSoloCuandoSeAutorizaEnDesarrollo() throws Exception {
        var cobertura = configuracion.coberturaManchaUrbana(mapper, archivo(), true);
        assertTrue(cobertura.contiene(-21.533912, -64.734297));
    }

    @Test
    void rechazaManchaUrbanaProvisionalEnProduccion() throws Exception {
        assertThrows(IllegalStateException.class,
                () -> configuracion.coberturaManchaUrbana(mapper, archivo(), false));
    }

    @Test
    void rechazaDocumentoAprobadoConFeaturePendiente() throws Exception {
        var documento = mapper.readTree(getClass().getResourceAsStream(
                "/fixtures/mancha_urbana_tarija_provisional.geojson"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) documento).put("estado_validacion", "APROBADO");
        assertFalse(ConfiguracionCobertura.todasLasGeometriasAprobadas(documento));
        ((com.fasterxml.jackson.databind.node.ObjectNode)
                documento.path("features").get(0).path("properties")).put("revision_equipo", "APROBADO");
        assertTrue(ConfiguracionCobertura.todasLasGeometriasAprobadas(documento));
    }

    @Test
    void rechazaRutaInexistenteSinBuscarOtroArchivo() {
        assertThrows(IllegalStateException.class,
                () -> configuracion.coberturaManchaUrbana(mapper, "ruta_inexistente.geojson", true));
    }
}
