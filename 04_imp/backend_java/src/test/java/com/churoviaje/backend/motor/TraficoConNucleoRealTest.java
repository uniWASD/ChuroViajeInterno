package com.churoviaje.backend.motor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.churoviaje.backend.config.PruebaIntegracionPostgis;
import com.churoviaje.backend.seguridad.JwtServicio;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T-099 (#67), CP-99: el backend entero con el nucleo C++ de verdad y la red irregular de prueba
 * ({@code 04_imp/motor_cpp/tests/datos/red_irregular.json}), donde la unica calle que llega al Mirador sale de la
 * Universidad. Corre solo con {@code MOTOR_PATH} y con {@code RED_VIAL_PATH} apuntando a esa red.
 */
@EnabledIfEnvironmentVariable(named = "MOTOR_PATH", matches = ".+")
@EnabledIfEnvironmentVariable(named = "RED_VIAL_PATH", matches = ".*red_irregular\\.json")
@TestPropertySource(properties = {"motor.habilitado=true", "motor.ejecutable=${MOTOR_PATH}",
        "motor.red-vial=${RED_VIAL_PATH}"})
class TraficoConNucleoRealTest extends PruebaIntegracionPostgis {

    private static final String PLAZA_A_MIRADOR = """
            {"origen":{"latitud":-21.5355,"longitud":-64.7296},"destino":{"latitud":-21.529,"longitud":-64.722}}""";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtServicio jwtServicio;
    @Autowired MotorDeRutas motor;

    private long conductor(String googleId) {
        return jdbc.queryForObject(
                "insert into conductor (google_id, fecha_alta) values (?, now()) returning id", Long.class, googleId);
    }

    private String autorizacion(long id) {
        return "Bearer " + jwtServicio.emitir(id, Instant.now());
    }

    private String estadoDeLaRuta(long quien) throws Exception {
        String respuesta = mvc.perform(post("/rutas").header("Authorization", autorizacion(quien))
                .contentType(MediaType.APPLICATION_JSON).content(PLAZA_A_MIRADOR))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("estado").asText();
    }

    /** El aviso al motor sale en su propio hilo: se espera hasta 3 segundos a que la ruta cambie. */
    private String esperarEstado(long quien, String esperado) throws Exception {
        long limite = System.currentTimeMillis() + 3000;
        String estado = estadoDeLaRuta(quien);
        while (!estado.equals(esperado) && System.currentTimeMillis() < limite) {
            Thread.sleep(50);
            estado = estadoDeLaRuta(quien);
        }
        return estado;
    }

    @Test
    @DisplayName("CP-99: un bloqueo en la unica calle entre dos puntos deja la ruta «sin ruta»; al retirarlo por votos, vuelve")
    void cp99BloqueoReportadoYRetiradoPorVotos() throws Exception {
        jdbc.update("delete from voto");
        jdbc.update("delete from evento_vial");
        long limite = System.currentTimeMillis() + 30_000;
        while (!motor.estaListo() && System.currentTimeMillis() < limite) {
            Thread.sleep(50);
        }
        assertThat(motor.estaListo()).isTrue();

        long reportante = conductor("cp99-reportante");
        assertThat(estadoDeLaRuta(reportante)).isEqualTo("ENCONTRADA");

        // Bloqueo en la mitad de la calle Universidad-Mirador.
        String bloqueo = "{\"tipo\":\"BLOQUEO\",\"ubicacion\":{\"latitud\":-21.5295,\"longitud\":-64.724},"
                + "\"horaConfirmacion\":\"" + OffsetDateTime.now(ZoneOffset.ofHours(-4)) + "\",\"minutosElegidos\":0}";
        String creado = mvc.perform(post("/eventos").header("Authorization", autorizacion(reportante))
                .contentType(MediaType.APPLICATION_JSON).content(bloqueo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(creado).get("id").asLong();
        assertThat(esperarEstado(reportante, "SIN_RUTA")).isEqualTo("SIN_RUTA");

        for (int i = 1; i <= 3; i++) {
            mvc.perform(post("/eventos/" + id + "/votos").header("Authorization", autorizacion(conductor("cp99-v" + i)))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"valor\":\"falso\"}"))
                    .andExpect(status().isCreated());
        }
        assertThat(esperarEstado(reportante, "ENCONTRADA")).isEqualTo("ENCONTRADA");
        System.out.println("CP-99: con el bloqueo la ruta Plaza-Mirador respondio SIN_RUTA; retirado por votos, ENCONTRADA");
    }
}
