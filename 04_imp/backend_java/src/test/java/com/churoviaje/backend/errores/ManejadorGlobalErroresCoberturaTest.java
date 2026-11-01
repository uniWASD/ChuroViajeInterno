package com.churoviaje.backend.errores;

import com.churoviaje.backend.dominio.UbicacionFueraCoberturaException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ManejadorGlobalErroresCoberturaTest {
    @RestController
    static class ControladorDePrueba {
        @GetMapping("/__test/cobertura")
        public String rechazar() {
            throw UbicacionFueraCoberturaException.paraReporte();
        }
    }

    @Test
    void globalTraduceExcepcionDeDominioA422() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ControladorDePrueba())
                .setControllerAdvice(new ManejadorGlobalErrores())
                .build();
        mvc.perform(get("/__test/cobertura"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("UBICACION_FUERA_COBERTURA"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
    }
}
