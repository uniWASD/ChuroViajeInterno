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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Test
    @DisplayName("Se aceptan fechas en formato ISO 8601 con zona horaria sin devolver 400")
    void peticionConFechaYZonaHorariaEsAceptada() throws Exception {
        String jsonValido = """
            {
                "tipo": "ACCIDENTE",
                "ubicacion": {
                    "latitud": -21.53,
                    "longitud": -64.73
                },
                "horaConfirmacion": "2026-10-06T15:02:00-04:00",
                "minutosElegidos": 0
            }
            """;

        mockMvc.perform(post("/eventos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonValido))
                .andExpect(status().isCreated());
    }
}