package bo.edu.ucb.churoviaje.integracion;

import bo.edu.ucb.churoviaje.dominio.ubicacion.CoberturaManchaUrbana;
import bo.edu.ucb.churoviaje.infraestructura.persistencia.CatalogosJdbc;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
    classes = T089ArranqueAisladoTest.AplicacionT089.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("dev")
class T089ArranqueAisladoTest {
    @SpringBootApplication(
        scanBasePackages = "bo.edu.ucb.churoviaje",
        exclude = {HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class}
    )
    static class AplicacionT089 {}

    @DynamicPropertySource
    static void configurarCobertura(DynamicPropertyRegistry registro) {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path ruta = cwd.resolve("datos/mancha_urbana_tarija.geojson");
        if (!Files.isRegularFile(ruta)) {
            ruta = cwd.resolve("../datos/mancha_urbana_tarija.geojson").normalize();
        }
        if (!Files.isRegularFile(ruta)) {
            throw new IllegalStateException("No se encontró GeoJSON desde " + cwd);
        }
        final Path rutaFinal = ruta;
        registro.add("churoviaje.cobertura.ruta", () -> rutaFinal.toString());
    }
    @Autowired CatalogosJdbc catalogos;
    @Autowired CoberturaManchaUrbana cobertura;
    @LocalServerPort int puerto;

    @Test
    void arrancaT089ConPostgisYFlyway() {
        assertTrue(puerto > 0);
        assertNotNull(cobertura);
        assertEquals(5, catalogos.tiposEvento().size());
        assertEquals(5, catalogos.tiposCombustible().size());
        assertTrue(cobertura.contiene(-21.533912, -64.734297));
        // Esta base dev es sin V2: si se cargaron surtidores, verificar y ajustar prueba.
        assertEquals(0, catalogos.surtidoresCercanos(-21.5355, -64.7296).size());
    }
}
