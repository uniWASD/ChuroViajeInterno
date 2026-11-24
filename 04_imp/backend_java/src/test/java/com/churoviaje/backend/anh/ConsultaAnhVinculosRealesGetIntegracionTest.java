package com.churoviaje.backend.anh;

import static com.churoviaje.backend.anh.ServidorAnhFalso.catalogo;
import static com.churoviaje.backend.anh.ServidorAnhFalso.estacion;
import static com.churoviaje.backend.anh.ServidorAnhFalso.listado;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.churoviaje.backend.config.ConfiguracionRelojDePrueba;
import com.churoviaje.backend.config.PruebaIntegracionPostgis;
import com.churoviaje.backend.config.RelojDePrueba;
import com.churoviaje.backend.seguridad.JwtServicio;
import com.churoviaje.backend.servicios.ConsultaAnh;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T-118 + HU-14: aplica V13 en PostGIS, consulta un vínculo real (2977/El Molle)
 * contra un servidor ANH falso, persiste gasolina y diésel y comprueba el resultado en GET /surtidores.
 * Es una prueba E2E automatizada de integración; no es una consulta de producción a ANH.
 */
@Import(ConfiguracionRelojDePrueba.class)
class ConsultaAnhVinculosRealesGetIntegracionTest extends PruebaIntegracionPostgis {

    private static final ServidorAnhFalso ANH = new ServidorAnhFalso();
    private static final Instant AHORA = Instant.parse("2026-11-23T14:05:00Z");
    private static final String HORA_ANH = "2026-11-23T10:00:00";
    private static final String GASOLINA = "estaciones/?departamento=6&producto=0";
    private static final String DIESEL = "estaciones/?departamento=6&producto=1";
    private static final int ANH_EL_MOLLE = 2977;

    @DynamicPropertySource
    static void anhFalso(DynamicPropertyRegistry registro) {
        registro.add("churoviaje.anh.base", () -> ANH.base().toString());
        registro.add("churoviaje.anh.pausa", () -> "PT0S");
    }

    @AfterAll
    static void apagar() {
        ANH.close();
    }

    @Autowired ConsultaAnh consulta;
    @Autowired JdbcTemplate jdbc;
    @Autowired RelojDePrueba reloj;
    @Autowired MockMvc mvc;
    @Autowired JwtServicio jwtServicio;
    @Autowired ObjectMapper json;

    @BeforeEach
    void preparar() {
        ANH.reiniciar();
        ANH.responder("departamentos/?producto=0", ServidorAnhFalso.Respuesta.json(catalogo()));
        ANH.responder(GASOLINA, ServidorAnhFalso.Respuesta.json(listado(estacion(
                ANH_EL_MOLLE, "EL MOLLE S.R.L.", -21.4530516, -64.7533344, 0,
                "1234.5", "alto", HORA_ANH))));
        ANH.responder(DIESEL, ServidorAnhFalso.Respuesta.json(listado(estacion(
                ANH_EL_MOLLE, "EL MOLLE S.R.L.", -21.4530516, -64.7533344, 1,
                "678.25", "medio", HORA_ANH))));
        ReflectionTestUtils.setField(consulta, "noConsultarAntesDe", null);
        reloj.fijar(AHORA);
        jdbc.update("delete from estado_combustible");
    }

    @Test
    @DisplayName("V13: 14 vínculos únicos; ANH -> PostGIS -> GET /surtidores devuelve gasolina y diésel")
    void v14ConsultaAnhPersisteYExponeEnGetSurtidores() throws Exception {
        assertThat(jdbc.queryForObject("select count(*) from surtidor where anh_id is not null", Integer.class))
                .isEqualTo(14);
        assertThat(jdbc.queryForObject("select count(distinct anh_id) from surtidor where anh_id is not null", Integer.class))
                .isEqualTo(14);

        Long elMolle = jdbc.queryForObject("select id from surtidor where nombre='El Molle' and anh_id=?",
                Long.class, ANH_EL_MOLLE);
        assertThat(elMolle).isNotNull();

        ConsultaAnh.Resumen resumen = consulta.consultar();
        assertThat(resumen.consulto()).isTrue();
        assertThat(resumen.candidatas()).isEmpty();
        assertThat(resumen.rutas().stream().filter(r -> r.ruta().equals(GASOLINA))
                .mapToInt(ConsultaAnh.Ruta::aceptadas).sum()).isEqualTo(1);
        assertThat(resumen.rutas().stream().filter(r -> r.ruta().equals(DIESEL))
                .mapToInt(ConsultaAnh.Ruta::aceptadas).sum()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from estado_combustible where surtidor_id=? and origen='ANH'",
                Integer.class, elMolle)).isEqualTo(2);

        long conductor = jdbc.queryForObject(
                "insert into conductor (google_id, fecha_alta) values ('google-anh-v14-' || gen_random_uuid(), now()) returning id",
                Long.class);
        String jwt = "Bearer " + jwtServicio.emitir(conductor, AHORA);
        String cuerpo = mvc.perform(get("/surtidores")
                        .param("latitud", "-21.5234844")
                        .param("longitud", "-64.724772")
                        .header("Authorization", jwt))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode molle = null;
        for (JsonNode surtidor : json.readTree(cuerpo).path("surtidores")) {
            if (surtidor.path("id").asLong() == elMolle) {
                molle = surtidor;
                break;
            }
        }
        assertThat(molle).isNotNull();
        JsonNode gasolina = null;
        JsonNode diesel = null;
        for (JsonNode combustible : molle.path("combustibles")) {
            if ("GASOLINA".equals(combustible.path("tipo").asText())) gasolina = combustible;
            if ("DIESEL".equals(combustible.path("tipo").asText())) diesel = combustible;
        }
        assertThat(gasolina).isNotNull();
        assertThat(diesel).isNotNull();
        assertThat(gasolina.path("origen").asText()).isEqualTo("ANH");
        assertThat(gasolina.path("litros").decimalValue()).isEqualByComparingTo("1234.5");
        assertThat(gasolina.path("estadoAnh").asText()).isEqualTo("ALTO");
        assertThat(gasolina.path("disponible").asBoolean()).isTrue();
        assertThat(diesel.path("origen").asText()).isEqualTo("ANH");
        assertThat(diesel.path("litros").decimalValue()).isEqualByComparingTo("678.25");
        assertThat(diesel.path("estadoAnh").asText()).isEqualTo("MEDIO");
        assertThat(diesel.path("disponible").asBoolean()).isTrue();
    }
}
