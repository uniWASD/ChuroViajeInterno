package com.churoviaje.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasSize;

@SpringBootTest
@AutoConfigureMockMvc
public class CatalogoControladorTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser // Simula un usuario autenticado para no rebotar por falta de JWT
    public void obtenerCatalogos_DebeRetornarTodosLosCatalogos() throws Exception {
        mockMvc.perform(get("/catalogos"))
                .andExpect(status().isOk())
                // CP-36: 5 tipos de evento, 5 de combustible, 4 de fila y 4 tiempos de reporte
                .andExpect(jsonPath("$.tiposEvento", hasSize(5)))
                .andExpect(jsonPath("$.tiposCombustible", hasSize(5)))
                .andExpect(jsonPath("$.tiemposReporte", hasSize(4)))
                .andExpect(jsonPath("$.valoresFila", hasSize(4)));
    }
}