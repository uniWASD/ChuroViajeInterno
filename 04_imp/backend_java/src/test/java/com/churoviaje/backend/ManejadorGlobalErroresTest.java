package com.churoviaje.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("pruebas")
class ManejadorGlobalErroresTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("CP-21: Una petición con un cuerpo mal formado responde 400 con {codigo, mensaje} en español")
    void cp21PeticionConCuerpoMalFormadoResponde400EnEspanol() throws Exception {
        String jsonMalFormado = "{ \"tipo\": \"ACCIDENTE\", \"ubicacion\": ";

        mockMvc.perform(post("/eventos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMalFormado))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").isNotEmpty())
                .andExpect(jsonPath("$.mensaje").value("La petición no tiene el formato esperado."));
    }
}