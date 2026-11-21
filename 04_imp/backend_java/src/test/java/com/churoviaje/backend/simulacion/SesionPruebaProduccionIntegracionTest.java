package com.churoviaje.backend.simulacion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.churoviaje.backend.config.PruebaIntegracionPostgis;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * CP-141 (#84): en el perfil {@code prod}, {@code POST /sesion/prueba} responde 403 aunque alguien deje definida la
 * credencial de prueba y el modo demostración por error (RN-07.5, P-20). Lo mismo el borrado de datos simulados.
 */
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "DB_URL=jdbc:postgresql://no-se-usa/la-conexion-la-da-testcontainers", "DB_USER=no-se-usa", "DB_PASSWORD=no-se-usa",
        "CHUROVIAJE_GEOJSON_PATH=../datos/mancha_urbana_tarija.geojson",
        "churoviaje.simulacion.credencial=" + SesionPruebaProduccionIntegracionTest.CREDENCIAL,
        "churoviaje.simulacion.demostracion=true"})
class SesionPruebaProduccionIntegracionTest extends PruebaIntegracionPostgis {

    static final String CREDENCIAL = "credencial-de-prueba-definida-por-error-en-prod-0123";

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("CP-141: en el perfil prod, POST /sesion/prueba responde 403")
    void cp141SesionDePruebaEnProduccion() throws Exception {
        mvc.perform(post("/sesion/prueba").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credencial\":\"" + CREDENCIAL + "\",\"pruebaId\":\"carga-1\",\"usuario\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    @DisplayName("RN-07.8: en el perfil prod, el borrado de datos simulados tampoco existe")
    void borradoEnProduccion() throws Exception {
        mvc.perform(post("/simulacion/borrar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credencial\":\"" + CREDENCIAL + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("El perfil prod sigue sin Swagger")
    void sinSwagger() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    }
}
