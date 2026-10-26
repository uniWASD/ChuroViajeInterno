package bo.edu.ucb.churoviaje.infraestructura.http;

import bo.edu.ucb.churoviaje.dominio.ubicacion.UbicacionFueraCoberturaException;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Emula el fallback genérico de T-088; prueba la prioridad 422 de T-089 sin fingir tener su código. */
class T089_ManejadoresMvcTest {
    @RestController
    public static class ControladorDePrueba {
        @GetMapping("/__t089_test/reporte_fuera")
        public String reporteFuera() {
            throw UbicacionFueraCoberturaException.paraReporte();
        }
    }
    @RestControllerAdvice
    @Order(Ordered.LOWEST_PRECEDENCE)
    public static class FallbackSimulado {
        @ExceptionHandler(Exception.class)
        public ResponseEntity<String> respuesta(Exception ex) {
            return ResponseEntity.internalServerError().body("FALLBACK_500");
        }
    }
    @Test
    void fueraDeCoberturaEs422AnteFallbackGenerico() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ControladorDePrueba())
            .setControllerAdvice(new FallbackSimulado(), new ManejadorUbicacionFueraCobertura())
            .build();
        mvc.perform(get("/__t089_test/reporte_fuera"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("UBICACION_FUERA_COBERTURA"));
    }
}
