package com.churoviaje.backend.servicios;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.churoviaje.backend.config.PruebaIntegracionPostgis;
import com.churoviaje.backend.seguridad.JwtServicio;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** T-094: GET /catalogos contra la base real que dejo T-089. */
class CatalogosIntegracionTest extends PruebaIntegracionPostgis {

    @Autowired MockMvc mvc;
    @Autowired JwtServicio jwt;

    private String autorizacion() {
        return "Bearer " + jwt.emitir(1L, Instant.now());
    }

    @Test
    @DisplayName("CP-36: devuelve 5 tipos de evento (4 reportables), 5 de combustible, 4 valores de fila y 4 tiempos de reporte")
    void cp36DevuelveTodosLosCatalogos() throws Exception {
        mvc.perform(get("/catalogos").header("Authorization", autorizacion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiposEvento", hasSize(5)))
                .andExpect(jsonPath("$.tiposEvento[?(@.reportable == true)]", hasSize(4)))
                .andExpect(jsonPath("$.tiposCombustible", hasSize(5)))
                .andExpect(jsonPath("$.valoresFila", hasSize(4)))
                .andExpect(jsonPath("$.tiemposReporte", hasSize(4)));
    }

    @Test
    @DisplayName("RN-01.3, RN-02.3, RN-02.7: tipos de evento con color, vigencia y si son reportables, en orden estable")
    void tiposDeEventoConColorVigenciaYReportable() throws Exception {
        mvc.perform(get("/catalogos").header("Authorization", autorizacion()))
                .andExpect(jsonPath("$.tiposEvento[*].codigo",
                        contains("ACCIDENTE", "CONTROL_POLICIAL", "BLOQUEO", "OTRO", "CONGESTION")))
                .andExpect(jsonPath("$.tiposEvento[0].nombre").value("Accidente"))
                .andExpect(jsonPath("$.tiposEvento[0].color").value("#E53935"))
                .andExpect(jsonPath("$.tiposEvento[0].vigenciaMinutos").value(60))
                .andExpect(jsonPath("$.tiposEvento[0].reportable").value(true))
                .andExpect(jsonPath("$.tiposEvento[4].codigo").value("CONGESTION"))
                .andExpect(jsonPath("$.tiposEvento[4].vigenciaMinutos").value(30))
                .andExpect(jsonPath("$.tiposEvento[4].reportable").value(false));
    }

    @Test
    @DisplayName("RN-05.9: Desconocido viene marcado como no seleccionable; los otros cuatro si")
    void desconocidoNoSePuedeElegir() throws Exception {
        mvc.perform(get("/catalogos").header("Authorization", autorizacion()))
                .andExpect(jsonPath("$.tiposCombustible[*].codigo",
                        contains("GASOLINA", "DIESEL", "GASOLINA_PREMIUM", "DIESEL_ULS", "DESCONOCIDO")))
                .andExpect(jsonPath("$.tiposCombustible[?(@.seleccionable == true)]", hasSize(4)))
                .andExpect(jsonPath("$.tiposCombustible[4].seleccionable").value(false));
    }

    @Test
    @DisplayName("RN-01.4, D-09: ahora mismo, hace 5 (marcado), hace 10 y hace 30 minutos")
    void tiemposDelReporte() throws Exception {
        mvc.perform(get("/catalogos").header("Authorization", autorizacion()))
                .andExpect(jsonPath("$.tiemposReporte[*].minutos", contains(0, 5, 10, 30)))
                .andExpect(jsonPath("$.tiemposReporte[?(@.porDefecto == true)]", hasSize(1)))
                .andExpect(jsonPath("$.tiemposReporte[1].porDefecto").value(true))
                .andExpect(jsonPath("$.tiemposReporte[3].nombre").value("Hace 30 minutos"));
    }

    @Test
    @DisplayName("P-15: sin fila, corta, media y larga")
    void valoresDeLaFila() throws Exception {
        mvc.perform(get("/catalogos").header("Authorization", autorizacion()))
                .andExpect(jsonPath("$.valoresFila[*].codigo", contains("SIN_FILA", "CORTA", "MEDIA", "LARGA")))
                .andExpect(jsonPath("$.valoresFila[2].nombre").value("Media"));
    }

    @Test
    @DisplayName("RNF-08: sin JWT, o con uno falso, responde 401")
    void exigeJwtVigente() throws Exception {
        mvc.perform(get("/catalogos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/catalogos").header("Authorization", "Bearer falso")).andExpect(status().isUnauthorized());
    }
}
