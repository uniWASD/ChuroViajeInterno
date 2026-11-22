package com.churoviaje.backend.simulacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.churoviaje.backend.config.PruebaIntegracionPostgis;
import com.churoviaje.backend.seguridad.JwtServicio;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** RN-07.13 (#84): en modo demostración, como el ambiente de pruebas del servidor, un conductor real ve lo simulado. */
@TestPropertySource(properties = {
        "churoviaje.simulacion.credencial=credencial-de-prueba-solo-para-las-pruebas-0123456789",
        "churoviaje.simulacion.demostracion=true"})
class SimulacionDemostracionIntegracionTest extends PruebaIntegracionPostgis {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtServicio jwtServicio;

    private long real;

    @BeforeEach
    void preparar() {
        jdbc.update("delete from voto");
        jdbc.update("delete from evento_vial");
        jdbc.update("delete from sesion");
        jdbc.update("delete from conductor");
        real = jdbc.queryForObject(
                "insert into conductor (google_id, fecha_alta) values ('google-real', now()) returning id", Long.class);
    }

    @Test
    @DisplayName("RN-07.13: en modo demostración el conductor real ve el evento simulado, y su voto también queda simulado")
    void modoDemostracion() throws Exception {
        String sesion = mvc.perform(post("/sesion/prueba").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"credencial\":\"credencial-de-prueba-solo-para-las-pruebas-0123456789\",\"pruebaId\":\"demo\",\"usuario\":1}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String fantasma = json.readTree(sesion).path("jwt").asText();
        String hora = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.ofHours(-4)).toString();
        String creado = mvc.perform(post("/eventos").header("Authorization", "Bearer " + fantasma)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"ACCIDENTE\",\"ubicacion\":"
                                + "{\"latitud\":-21.5355,\"longitud\":-64.7296},\"horaConfirmacion\":\"" + hora + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(creado).path("id").asLong();

        String jwtReal = jwtServicio.emitir(real, Instant.now());
        String lista = mvc.perform(get("/eventos").header("Authorization", "Bearer " + jwtReal))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(lista).path("eventos").get(0).path("id").asLong()).isEqualTo(id);

        mvc.perform(post("/eventos/" + id + "/votos").header("Authorization", "Bearer " + jwtReal)
                .contentType(MediaType.APPLICATION_JSON).content("{\"valor\":\"real\"}")).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select simulado from voto where conductor_id = ?", Boolean.class, real))
                .as("al borrar lo simulado se va con su evento sin tocar una fila real").isTrue();
    }
}
