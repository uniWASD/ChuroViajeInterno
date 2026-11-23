package com.churoviaje.backend.anh;

import static com.churoviaje.backend.anh.ServidorAnhFalso.catalogo;
import static com.churoviaje.backend.anh.ServidorAnhFalso.listado;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.churoviaje.backend.anh.ServidorAnhFalso.Respuesta;
import com.churoviaje.backend.config.ConfiguracionRelojDePrueba;
import com.churoviaje.backend.config.PruebaIntegracionPostgis;
import com.churoviaje.backend.config.RelojDePrueba;
import com.churoviaje.backend.seguridad.JwtServicio;
import com.churoviaje.backend.servicios.ConsultaAnh;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
 * HU-14 (#114): la consulta a ANH de punta a punta, con la base real, el catálogo de V2, la mancha urbana real y un
 * servidor de ANH falso. CP-161 a CP-165 y las demás reglas de CU-09 que tocan la base.
 */
@Import(ConfiguracionRelojDePrueba.class)
class ConsultaAnhIntegracionTest extends PruebaIntegracionPostgis {

    private static final ServidorAnhFalso ANH = new ServidorAnhFalso();

    /** 10:05 en Tarija. */
    private static final Instant AHORA = Instant.parse("2026-11-23T14:05:00Z");
    /** El updated_at de ANH, sin zona: 10:00 en Tarija, 14:00 UTC (RN-09.13). */
    private static final String HORA_ANH = "2026-11-23T10:00:00";
    private static final Instant HORA_ANH_UTC = Instant.parse("2026-11-23T14:00:00Z");

    private static final String GASOLINA = "estaciones/?departamento=6&producto=0";
    private static final String DIESEL = "estaciones/?departamento=6&producto=1";

    // Estaciones del servidor falso: dos vinculadas a surtidores del catálogo, una sin vincular dentro de la mancha
    // urbana y una en Yacuiba, fuera de la mancha.
    private static final int EL_MOLLE = 2001;
    private static final int LAS_VEGAS = 2002;
    private static final int SIN_VINCULAR = 3000;
    private static final int YACUIBA = 2900;

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

    private long elMolle;
    private long lasVegas;
    private List<Map<String, Object>> vinculosGuardados;

    @BeforeEach
    void preparar() {
        vinculosGuardados = jdbc.queryForList("select id, anh_id from surtidor where anh_id is not null");
        ANH.reiniciar();
        ANH.responder("departamentos/?producto=0", Respuesta.json(catalogo()));
        ReflectionTestUtils.setField(consulta, "noConsultarAntesDe", null);
        reloj.fijar(AHORA);
        jdbc.update("delete from estado_combustible");
        jdbc.update("update surtidor set anh_id = null");
        elMolle = jdbc.queryForObject("select id from surtidor where nombre = 'El Molle'", Long.class);
        lasVegas = jdbc.queryForObject("select id from surtidor where nombre = 'Las Vegas'", Long.class);
        jdbc.update("update surtidor set anh_id = ? where id = ?", EL_MOLLE, elMolle);
        jdbc.update("update surtidor set anh_id = ? where id = ?", LAS_VEGAS, lasVegas);
    }

    /** Deja los vínculos como estaban (los de la migración de datos de #113, si ya está), para las demás pruebas. */
    @AfterEach
    void restaurar() {
        jdbc.update("delete from estado_combustible");
        jdbc.update("update surtidor set anh_id = null");
        for (Map<String, Object> v : vinculosGuardados) {
            jdbc.update("update surtidor set anh_id = ? where id = ?", v.get("anh_id"), v.get("id"));
        }
    }

    private static String elMolle(int producto, String litros, String estado, String hora) {
        return ServidorAnhFalso.estacion(EL_MOLLE, "EL MOLLE S.R.L.", -21.4530516, -64.7533344, producto, litros, estado,
                hora);
    }

    private static String lasVegas(int producto, String litros, String hora) {
        return ServidorAnhFalso.estacion(LAS_VEGAS, "LAS VEGAS", -21.5234844, -64.724772, producto, litros, "medio",
                hora);
    }

    private List<Map<String, Object>> estadosDeAnh() {
        return jdbc.queryForList("""
                select surtidor_id, tipo_codigo, disponible, litros, estado_anh, fecha_dato, consultado_en,
                       actualizado_en
                from estado_combustible where origen = 'ANH' order by surtidor_id, tipo_codigo""");
    }

    private void guardarAnh(long surtidor, String litros, Instant fecha, Instant leido) {
        jdbc.update("""
                insert into estado_combustible (surtidor_id, tipo_codigo, origen, disponible, fecha_dato, litros,
                                                estado_anh, consultado_en, actualizado_en)
                values (?, 'GASOLINA', 'ANH', ?, ?, ?, 'MEDIO', ?, ?)""",
                surtidor, new BigDecimal(litros).signum() > 0, Timestamp.from(fecha), new BigDecimal(litros),
                Timestamp.from(leido), Timestamp.from(leido));
    }

    @Test
    @DisplayName("CP-161: de tres estaciones, solo la vinculada y dentro de la mancha crea un estado agotado de ANH")
    void cp161SoloLaVinculadaDentroDeLaMancha() throws Exception {
        ANH.responder(GASOLINA, Respuesta.json(listado(
                elMolle(0, "0", "bajo", HORA_ANH),
                ServidorAnhFalso.estacion(YACUIBA, "YACUIBA", -22.0144, -63.6778, 0, "150", "alto", HORA_ANH),
                ServidorAnhFalso.estacion(SIN_VINCULAR, "NUEVA", -21.5350, -64.7300, 0, "80", "alto", HORA_ANH))));
        ANH.responder(DIESEL, Respuesta.json(listado()));

        ConsultaAnh.Resumen resumen = consulta.consultar();

        List<Map<String, Object>> estados = estadosDeAnh();
        assertThat(estados).hasSize(1);
        Map<String, Object> estado = estados.get(0);
        assertThat(estado.get("surtidor_id")).isEqualTo(elMolle);
        assertThat(estado.get("tipo_codigo")).isEqualTo("GASOLINA");
        assertThat(estado.get("disponible")).isEqualTo(false);
        assertThat((BigDecimal) estado.get("litros")).isEqualByComparingTo("0");
        assertThat(estado.get("estado_anh")).isEqualTo("BAJO");
        assertThat(((Timestamp) estado.get("fecha_dato")).toInstant()).isEqualTo(HORA_ANH_UTC);
        assertThat(((Timestamp) estado.get("consultado_en")).toInstant()).isEqualTo(AHORA);

        ConsultaAnh.Ruta gasolina = resumen.rutas().stream().filter(r -> r.ruta().equals(GASOLINA)).findFirst()
                .orElseThrow();
        assertThat(gasolina.llegaron()).isEqualTo(3);
        assertThat(gasolina.aceptadas()).isEqualTo(1);
        assertThat(gasolina.ignoradas()).isEqualTo(2);
        assertThat(resumen.candidatas()).containsExactly(SIN_VINCULAR);
    }

    @Test
    @DisplayName("CP-162: ANH responde 503 en todos los intentos; ningún estado cambia y se conserva el último dato")
    void cp162Siempre503() throws Exception {
        Instant antes = AHORA.minus(Duration.ofHours(2));
        guardarAnh(elMolle, "500", antes, antes);
        ANH.responder(GASOLINA, Respuesta.estado(503));
        ANH.responder(DIESEL, Respuesta.estado(503));

        ConsultaAnh.Resumen resumen = consulta.consultar();

        assertThat(ANH.pedidos(GASOLINA)).isEqualTo(2);
        assertThat(ANH.pedidos(DIESEL)).isEqualTo(2);
        List<Map<String, Object>> estados = estadosDeAnh();
        assertThat(estados).hasSize(1);
        assertThat((BigDecimal) estados.get(0).get("litros")).isEqualByComparingTo("500");
        assertThat(((Timestamp) estados.get(0).get("fecha_dato")).toInstant()).isEqualTo(antes);
        assertThat(((Timestamp) estados.get(0).get("consultado_en")).toInstant()).isEqualTo(antes);
        assertThat(resumen.rutas()).filteredOn(r -> r.ruta().startsWith("estaciones"))
                .allSatisfy(r -> assertThat(r.motivo()).contains("HTTP"));
    }

    @Test
    @DisplayName("CP-163: una estación de otro departamento descarta la respuesta completa; nada se actualiza")
    void cp163OtroDepartamento() throws Exception {
        String otra = lasVegas(0, "40", HORA_ANH).replace("\"departamento_id\": 6", "\"departamento_id\": 3");
        ANH.responder(GASOLINA, Respuesta.json(listado(elMolle(0, "100", "alto", HORA_ANH), otra)));
        ANH.responder(DIESEL, Respuesta.json(listado()));

        ConsultaAnh.Resumen resumen = consulta.consultar();

        assertThat(estadosDeAnh()).isEmpty();
        assertThat(resumen.rutas()).filteredOn(r -> r.ruta().equals(GASOLINA))
                .singleElement().satisfies(r -> assertThat(r.motivo()).contains("otro departamento"));
    }

    @Test
    @DisplayName("CP-164: con saldo «abc» la estación queda sin dato y la que trae 20 litros queda disponible")
    void cp164SaldoInvalido() throws Exception {
        ANH.responder(GASOLINA, Respuesta.json(listado(elMolle(0, "\"abc\"", "alto", HORA_ANH),
                lasVegas(0, "20", HORA_ANH))));
        ANH.responder(DIESEL, Respuesta.json(listado()));

        consulta.consultar();

        List<Map<String, Object>> estados = estadosDeAnh();
        assertThat(estados).hasSize(1);
        assertThat(estados.get(0).get("surtidor_id")).isEqualTo(lasVegas);
        assertThat(estados.get(0).get("disponible")).isEqualTo(true);
        assertThat((BigDecimal) estados.get(0).get("litros")).isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("CP-165: un dato de ANH con fecha anterior al guardado no lo reemplaza")
    void cp165DatoMasViejo() throws Exception {
        Instant reciente = HORA_ANH_UTC.plus(Duration.ofMinutes(30));
        guardarAnh(elMolle, "900", reciente, AHORA.minus(Duration.ofMinutes(5)));
        ANH.responder(GASOLINA, Respuesta.json(listado(elMolle(0, "0", "bajo", HORA_ANH))));
        ANH.responder(DIESEL, Respuesta.json(listado()));

        consulta.consultar();

        Map<String, Object> estado = estadosDeAnh().get(0);
        assertThat((BigDecimal) estado.get("litros")).isEqualByComparingTo("900");
        assertThat(((Timestamp) estado.get("fecha_dato")).toInstant()).isEqualTo(reciente);
        assertThat(((Timestamp) estado.get("consultado_en")).toInstant()).isEqualTo(AHORA.minus(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("RN-09.12: los litros se guardan sin redondear la precisión decimal recibida de ANH")
    void conservaPrecisionDecimalDeLitros() throws Exception {
        String litrosRecibidos = "1234.567890123456789";
        ANH.responder(GASOLINA, Respuesta.json(listado(elMolle(0, litrosRecibidos, "medio", HORA_ANH))));
        ANH.responder(DIESEL, Respuesta.json(listado()));

        consulta.consultar();

        BigDecimal litrosGuardados = jdbc.queryForObject(
                "select litros from estado_combustible where surtidor_id = ? and tipo_codigo = 'GASOLINA' and origen = 'ANH'",
                BigDecimal.class, elMolle);
        assertThat(litrosGuardados).isEqualByComparingTo(litrosRecibidos);
    }

    @Test
    @DisplayName("RN-09.14: con la misma fecha solo se actualiza el momento de la lectura")
    void mismaFechaSoloReleido() throws Exception {
        guardarAnh(elMolle, "300", HORA_ANH_UTC, AHORA.minus(Duration.ofMinutes(10)));
        ANH.responder(GASOLINA, Respuesta.json(listado(elMolle(0, "250", "bajo", HORA_ANH))));
        ANH.responder(DIESEL, Respuesta.json(listado()));

        consulta.consultar();

        Map<String, Object> estado = estadosDeAnh().get(0);
        assertThat((BigDecimal) estado.get("litros")).isEqualByComparingTo("300");
        assertThat(estado.get("estado_anh")).isEqualTo("MEDIO");
        assertThat(((Timestamp) estado.get("consultado_en")).toInstant()).isEqualTo(AHORA);
        assertThat(((Timestamp) estado.get("actualizado_en")).toInstant())
                .isEqualTo(AHORA.minus(Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("RN-09.14: un dato más reciente reemplaza al de ANH, y no toca los de WhatsApp")
    void datoMasRecienteReemplaza() throws Exception {
        Instant antes = HORA_ANH_UTC.minus(Duration.ofHours(1));
        guardarAnh(elMolle, "300", antes, antes);
        jdbc.update("""
                insert into estado_combustible (surtidor_id, tipo_codigo, origen, disponible, fecha_dato, actualizado_en)
                values (?, 'GASOLINA', 'WHATSAPP', true, ?, ?)""", elMolle, Timestamp.from(AHORA), Timestamp.from(AHORA));
        ANH.responder(GASOLINA, Respuesta.json(listado(elMolle(0, "0", "bajo", HORA_ANH))));
        ANH.responder(DIESEL, Respuesta.json(listado()));

        consulta.consultar();

        Map<String, Object> anh = estadosDeAnh().get(0);
        assertThat((BigDecimal) anh.get("litros")).isEqualByComparingTo("0");
        assertThat(anh.get("disponible")).isEqualTo(false);
        assertThat(jdbc.queryForObject("select disponible from estado_combustible where origen = 'WHATSAPP'",
                Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("RN-09.16: si falla la gasolina, el diésel se actualiza igual")
    void unCombustibleFallaElOtroSigue() throws Exception {
        ANH.responder(GASOLINA, Respuesta.estado(500));
        ANH.responder(DIESEL, Respuesta.json(listado(elMolle(1, "45", "medio", HORA_ANH))));

        consulta.consultar();

        assertThat(estadosDeAnh()).singleElement().satisfies(e -> {
            assertThat(e.get("tipo_codigo")).isEqualTo("DIESEL");
            assertThat(e.get("disponible")).isEqualTo(true);
        });
    }

    @Test
    @DisplayName("RN-09.2: sin Tarija en ningún catálogo no se piden las estaciones")
    void sinTarija() throws Exception {
        String sinTarija = "{\"decCodigo\": 1, \"oResultado\": [{\"id\": 1, \"nombre\": \"La Paz\"}]}";
        ANH.responder("departamentos/?producto=0", Respuesta.json(sinTarija));
        ANH.responder("departamentos/?producto=1", Respuesta.json(sinTarija));

        consulta.consultar();

        assertThat(ANH.pedidos(GASOLINA)).isZero();
        assertThat(ANH.pedidos("departamentos/?producto=1")).isEqualTo(1);
    }

    @Test
    @DisplayName("RN-09.2: si el primer catálogo falla, se usa el alternativo")
    void catalogoAlternativo() throws Exception {
        ANH.responder("departamentos/?producto=0", Respuesta.estado(500));
        ANH.responder("departamentos/?producto=1", Respuesta.json(catalogo()));
        ANH.responder(GASOLINA, Respuesta.json(listado(elMolle(0, "10", "bajo", HORA_ANH))));
        ANH.responder(DIESEL, Respuesta.json(listado()));

        consulta.consultar();

        assertThat(estadosDeAnh()).hasSize(1);
    }

    @Test
    @DisplayName("RN-09.4: con Retry-After de más de 30 s no se vuelve a consultar hasta pasado ese plazo")
    void retryAfterLargoPausaLaConsulta() throws Exception {
        ANH.responder(GASOLINA, Respuesta.estado(429).conCabecera("Retry-After", "900"));
        ANH.responder(DIESEL, Respuesta.json(listado(elMolle(1, "45", "medio", HORA_ANH))));

        consulta.consultar();
        assertThat(ANH.pedidos(GASOLINA)).isEqualTo(1);
        assertThat(ANH.pedidos(DIESEL)).isZero();

        reloj.avanzar(Duration.ofMinutes(10));
        ConsultaAnh.Resumen pausado = consulta.consultar();
        assertThat(pausado.consulto()).isFalse();
        assertThat(ANH.pedidos("departamentos/?producto=0")).isEqualTo(1);

        reloj.avanzar(Duration.ofMinutes(6));
        ANH.responder(GASOLINA, Respuesta.json(listado()));
        consulta.consultar();
        assertThat(ANH.pedidos(DIESEL)).isEqualTo(1);
    }

    @Test
    @DisplayName("El dato de ANH sale en GET /surtidores con origen ANH, litros y estado; vale 6 h desde la lectura")
    void salenEnLaConsultaDeSurtidores() throws Exception {
        // updated_at de hace 8 horas, pero leído ahora: sigue vigente (RN-09.17).
        ANH.responder(GASOLINA, Respuesta.json(listado(elMolle(0, "1234.5", "alto", "2026-11-23T02:00:00"))));
        ANH.responder(DIESEL, Respuesta.json(listado()));
        consulta.consultar();
        long conductor = jdbc.queryForObject(
                "insert into conductor (google_id, fecha_alta) values ('google-anh-' || gen_random_uuid(), now()) returning id",
                Long.class);
        String jwt = "Bearer " + jwtServicio.emitir(conductor, AHORA);

        String cuerpo = mvc.perform(get("/surtidores").param("latitud", "-21.5234844").param("longitud", "-64.724772")
                        .header("Authorization", jwt))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        JsonNode molle = null;
        for (JsonNode s : json.readTree(cuerpo).path("surtidores")) {
            if (s.path("id").asLong() == elMolle) {
                molle = s;
            }
        }
        assertThat(molle).isNotNull();
        JsonNode gasolina = molle.path("combustibles").get(0);
        assertThat(gasolina.path("origen").asText()).isEqualTo("ANH");
        assertThat(gasolina.path("litros").decimalValue()).isEqualByComparingTo("1234.5");
        assertThat(gasolina.path("estadoAnh").asText()).isEqualTo("ALTO");
        assertThat(gasolina.path("disponible").asBoolean()).isTrue();

        // A las 6 horas de la lectura deja de mostrarse (P-30, contado desde consultado_en).
        reloj.avanzar(Duration.ofHours(6));
        String despues = mvc.perform(get("/surtidores").param("latitud", "-21.5234844").param("longitud", "-64.724772")
                        .header("Authorization", "Bearer " + jwtServicio.emitir(conductor, reloj.instant())))
                .andReturn().getResponse().getContentAsString();
        for (JsonNode s : json.readTree(despues).path("surtidores")) {
            if (s.path("id").asLong() == elMolle) {
                assertThat(s.path("combustibles")).isEmpty();
            }
        }
    }

    @Test
    @DisplayName("RN-09.18: no se guarda nada de las estaciones ignoradas ni las respuestas")
    void noGuardaLoIgnorado() throws Exception {
        ANH.responder(GASOLINA, Respuesta.json(listado(
                ServidorAnhFalso.estacion(SIN_VINCULAR, "NUEVA", -21.5350, -64.7300, 0, "80", "alto", HORA_ANH))));
        ANH.responder(DIESEL, Respuesta.json(listado()));

        consulta.consultar();

        assertThat(jdbc.queryForObject("select count(*) from estado_combustible", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from surtidor where anh_id = ?", Integer.class, SIN_VINCULAR))
                .isZero();
    }
}
