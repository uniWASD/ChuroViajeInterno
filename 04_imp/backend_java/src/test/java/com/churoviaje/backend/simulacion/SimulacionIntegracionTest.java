package com.churoviaje.backend.simulacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.churoviaje.backend.config.ConfiguracionRelojDePrueba;
import com.churoviaje.backend.config.PruebaIntegracionPostgis;
import com.churoviaje.backend.config.RelojDePrueba;
import com.churoviaje.backend.dominio.DatosSimuladosRepository;
import com.churoviaje.backend.motor.MotorDeRutas;
import com.churoviaje.backend.motor.TraficoDelMotor;
import com.churoviaje.backend.seguridad.JwtServicio;
import com.churoviaje.backend.servicios.RegistroPruebasCarga;
import com.churoviaje.backend.servicios.TelemetriaServicio;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * HU-09 (#84): el backend recibe a los usuarios fantasma en un ambiente con las credenciales de prueba habilitadas y
 * sin modo demostración, como el ambiente de pruebas con el modo apagado. CP-142, CP-143 y las demás reglas de CU-07.
 */
@Import(ConfiguracionRelojDePrueba.class)
@TestPropertySource(properties = {
        "churoviaje.simulacion.credencial=" + SimulacionIntegracionTest.CREDENCIAL,
        "churoviaje.simulacion.demostracion=false",
        "churoviaje.simulacion.registro=${java.io.tmpdir}/churoviaje-pruebas-carga-test.log",
        "motor.habilitado=true", "motor.ejecutable=no-se-usa", "motor.red-vial=no-se-usa"})
class SimulacionIntegracionTest extends PruebaIntegracionPostgis {

    static final String CREDENCIAL = "credencial-de-prueba-solo-para-las-pruebas-0123456789";
    private static final Instant INICIO = Instant.parse("2026-11-22T14:00:00Z");
    private static final Path ARCHIVO_REGISTRO = Path.of(System.getProperty("java.io.tmpdir"),
            "churoviaje-pruebas-carga-test.log");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtServicio jwtServicio;
    @Autowired RelojDePrueba reloj;
    @Autowired RegistroPruebasCarga registro;
    @Autowired TelemetriaServicio telemetria;
    @Autowired TraficoDelMotor trafico;
    @Autowired DatosSimuladosRepository datosSimulados;
    @MockitoBean MotorDeRutas motor;

    private long real;

    @BeforeEach
    void preparar() throws Exception {
        jdbc.update("delete from voto");
        jdbc.update("delete from evento_vial");
        jdbc.update("delete from sesion");
        jdbc.update("delete from conductor");
        reloj.fijar(INICIO);
        reset(motor);
        when(motor.actualizarTrafico(any())).thenReturn(json.readTree("{\"ok\":true,\"tramos\":[]}"));
        when(motor.analizarTelemetria(any())).thenReturn(json.readTree(
                "{\"ok\":true,\"congestiones\":[],\"lecturas_validas\":1,\"lecturas_descartadas\":0}"));
        real = jdbc.queryForObject(
                "insert into conductor (google_id, fecha_alta) values ('google-real', now()) returning id", Long.class);
        Files.deleteIfExists(ARCHIVO_REGISTRO);
    }

    private ResultActions pedirSesion(String credencial, String prueba, Object usuario) throws Exception {
        String cuerpo = json.writeValueAsString(Map.of("credencial", credencial, "pruebaId", prueba, "usuario", usuario));
        return mvc.perform(post("/sesion/prueba").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    /** JWT de un usuario fantasma, pedido como lo pide el simulador. */
    private String jwtFantasma(String prueba, int usuario) throws Exception {
        String r = pedirSesion(CREDENCIAL, prueba, usuario).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(r).path("jwt").asText();
    }

    private String jwtReal() {
        return jwtServicio.emitir(real, reloj.instant());
    }

    private String hora() {
        return OffsetDateTime.ofInstant(reloj.instant(), ZoneOffset.ofHours(-4)).toString();
    }

    private ResultActions reportar(String jwt, String tipo, double lat, double lon) throws Exception {
        String cuerpo = "{\"tipo\":\"" + tipo + "\",\"ubicacion\":{\"latitud\":" + lat + ",\"longitud\":" + lon
                + "},\"horaConfirmacion\":\"" + hora() + "\"}";
        return mvc.perform(post("/eventos").header("Authorization", "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private long reportarYDevolverId(String jwt, String tipo, double lat, double lon) throws Exception {
        String r = reportar(jwt, tipo, lat, lon).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(r).path("id").asLong();
    }

    private ResultActions votar(String jwt, long evento, String valor) throws Exception {
        return mvc.perform(post("/eventos/" + evento + "/votos").header("Authorization", "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON).content("{\"valor\":\"" + valor + "\"}"));
    }

    private JsonNode consultar(String jwt, String marca) throws Exception {
        var peticion = get("/eventos").header("Authorization", "Bearer " + jwt);
        if (marca != null) {
            peticion = peticion.param("desde", marca);
        }
        return json.readTree(mvc.perform(peticion).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
    }

    private static List<Long> ids(JsonNode eventos) {
        return java.util.stream.StreamSupport.stream(eventos.spliterator(), false).map(e -> e.path("id").asLong())
                .toList();
    }

    @Test
    @DisplayName("RN-07.4: POST /sesion/prueba entrega el JWT de un conductor simulado; el mismo número es el mismo conductor")
    void sesionDePruebaCreaUnConductorSimulado() throws Exception {
        pedirSesion(CREDENCIAL, "carga-1", 3).andExpect(status().isOk())
                .andExpect(jsonPath("$.jwt").isNotEmpty())
                .andExpect(jsonPath("$.expiraEn").isNotEmpty())
                .andExpect(jsonPath("$.tokenRenovacion").doesNotExist());
        pedirSesion(CREDENCIAL, "carga-1", 3).andExpect(status().isOk());
        pedirSesion(CREDENCIAL, "carga-1", 4).andExpect(status().isOk());

        List<Map<String, Object>> fantasmas = jdbc.queryForList(
                "select google_id, simulado from conductor where google_id like 'prueba:%' order by google_id");
        assertThat(fantasmas).hasSize(2);
        assertThat(fantasmas.get(0)).containsEntry("google_id", "prueba:carga-1:3").containsEntry("simulado", true);
        assertThat(fantasmas.get(1)).containsEntry("google_id", "prueba:carga-1:4").containsEntry("simulado", true);
        assertThat(jdbc.queryForObject("select count(*) from sesion", Integer.class))
                .as("sin token de renovación no hay fila de sesión").isZero();
    }

    @Test
    @DisplayName("RN-07.5: credencial equivocada responde 401; identificador o número fuera de formato, 400")
    void sesionDePruebaValidaLaPeticion() throws Exception {
        pedirSesion("otra-credencial-que-no-es-la-de-este-ambiente-123456", "carga-1", 1)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIAL_PRUEBA_INVALIDA"));
        pedirSesion(CREDENCIAL, "carga con espacios", 1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PETICION_INVALIDA"));
        pedirSesion(CREDENCIAL, "carga-1", 0).andExpect(status().isBadRequest());
        pedirSesion(CREDENCIAL, "carga-1", 201).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from conductor where simulado", Integer.class)).isZero();
    }

    @Test
    @DisplayName("CP-142: un evento simulado no aparece a un conductor real fuera del modo demostración")
    void cp142EventoSimuladoNoLoVeUnConductorReal() throws Exception {
        String fantasma = jwtFantasma("carga-1", 1);
        String marca = consultar(jwtReal(), null).path("marca").asText();
        reloj.avanzar(Duration.ofSeconds(5));

        long simulado = reportarYDevolverId(fantasma, "ACCIDENTE", -21.5355, -64.7296);
        long realId = reportarYDevolverId(jwtReal(), "CONTROL_POLICIAL", -21.5300, -64.7260);
        assertThat(jdbc.queryForObject("select simulado from evento_vial where id = ?", Boolean.class, simulado))
                .as("RN-01.17: el evento de un usuario fantasma queda simulado").isTrue();
        reloj.avanzar(Duration.ofSeconds(5));

        assertThat(ids(consultar(jwtReal(), null).path("eventos"))).containsExactly(realId);
        assertThat(ids(consultar(jwtReal(), marca).path("eventos")))
                .as("tampoco en la consulta por cambios").containsExactly(realId);
        assertThat(ids(consultar(fantasma, null).path("eventos")))
                .as("el usuario fantasma sí ve lo simulado: es parte de la prueba").containsExactly(simulado, realId);
    }

    @Test
    @DisplayName("RN-07.7: un evento simulado pasa por las mismas reglas: fuera de Tarija se rechaza y el duplicado también")
    void eventoSimuladoPasaPorLasMismasReglas() throws Exception {
        String fantasma = jwtFantasma("carga-1", 1);
        reportar(fantasma, "ACCIDENTE", -16.5, -68.15).andExpect(status().isUnprocessableEntity());
        reportar(fantasma, "ACCIDENTE", -21.5355, -64.7296).andExpect(status().isCreated());
        reportar(fantasma, "ACCIDENTE", -21.5355, -64.7296).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("RN-02.16 y RN-07.9: el voto de un fantasma queda simulado, y no puede votar un evento real")
    void votosDeUsuariosFantasma() throws Exception {
        String uno = jwtFantasma("carga-1", 1);
        String dos = jwtFantasma("carga-1", 2);
        long simulado = reportarYDevolverId(uno, "ACCIDENTE", -21.5355, -64.7296);
        long realId = reportarYDevolverId(jwtReal(), "ACCIDENTE", -21.5300, -64.7260);

        votar(dos, simulado, "real").andExpect(status().isCreated());
        votar(dos, realId, "falso").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VOTO_NO_PERMITIDO"));
        assertThat(jdbc.queryForObject("select simulado from voto where evento_id = ?", Boolean.class, simulado))
                .isTrue();
        assertThat(jdbc.queryForObject("select votos_falso from evento_vial where id = ?", Integer.class, realId))
                .as("el evento real queda igual").isZero();
    }

    @Test
    @DisplayName("RN-04.20: la telemetría de un fantasma viaja al motor con la marca de simulado y su congestión queda simulada")
    void telemetriaSimulada() throws Exception {
        String fantasma = jwtFantasma("carga-1", 1);
        when(motor.analizarTelemetria(any())).thenReturn(json.readTree("{\"ok\":true,\"congestiones\":[{\"tramo\":"
                + "\"osm:7101:2:ida\",\"punto\":[-21.54,-64.73794],\"nivel\":\"CONGESTIONADO\",\"simulado\":true}],"
                + "\"lecturas_validas\":1,\"lecturas_descartadas\":0}"));
        mvc.perform(post("/telemetria").header("Authorization", "Bearer " + fantasma)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"lecturas":[{"ubicacion":{"latitud":-21.54,"longitud":-64.738},"precisionM":8,
                          "momento":"2026-11-22T10:00:00-04:00"}]}"""))
                .andExpect(status().isAccepted());
        assertThat(telemetria.esperarPendientes(5000)).isTrue();

        ArgumentCaptor<ObjectNode> mensaje = ArgumentCaptor.forClass(ObjectNode.class);
        verify(motor, timeout(5000)).analizarTelemetria(mensaje.capture());
        assertThat(mensaje.getValue().path("recorridos").get(0).path("simulado").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("select simulado from evento_vial where tipo_codigo = 'CONGESTION'",
                Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("CP-143: después del borrado no queda ninguna fila simulada y las reales siguen intactas")
    void cp143BorradoDeDatosSimulados() throws Exception {
        // Datos reales: un conductor más, su evento, un voto real y una congestión real.
        long otroReal = jdbc.queryForObject(
                "insert into conductor (google_id, fecha_alta) values ('google-real-2', now()) returning id", Long.class);
        long eventoReal = reportarYDevolverId(jwtReal(), "ACCIDENTE", -21.5300, -64.7260);
        votar(jwtServicio.emitir(otroReal, reloj.instant()), eventoReal, "real").andExpect(status().isCreated());
        jdbc.update("""
                insert into evento_vial (tipo_codigo, ubicacion, ocurrio_en, registrado_en, actualizado_en, origen,
                                         nivel, estado, tramo_id)
                values ('CONGESTION', ST_SetSRID(ST_MakePoint(-64.73, -21.54), 4326)::geography, ?, ?, ?, 'TELEMETRIA',
                        'CONGESTIONADO', 'ACTIVO', 'osm:1:1:ida')
                """, Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()));

        // Datos simulados: dos fantasmas, sus eventos, sus votos y una congestión simulada.
        String uno = jwtFantasma("carga-1", 1);
        String dos = jwtFantasma("carga-1", 2);
        long eventoSimulado = reportarYDevolverId(uno, "BLOQUEO", -21.5355, -64.7296);
        votar(dos, eventoSimulado, "real").andExpect(status().isCreated());
        jdbc.update("""
                insert into evento_vial (tipo_codigo, ubicacion, ocurrio_en, registrado_en, actualizado_en, origen,
                                         nivel, estado, tramo_id, simulado)
                values ('CONGESTION', ST_SetSRID(ST_MakePoint(-64.73, -21.54), 4326)::geography, ?, ?, ?, 'TELEMETRIA',
                        'CONGESTIONADO', 'ACTIVO', 'osm:1:1:ida', true)
                """, Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()));

        List<Map<String, Object>> conductoresAntes = jdbc.queryForList("select * from conductor where not simulado order by id");
        List<Map<String, Object>> eventosAntes = jdbc.queryForList(
                "select id, tipo_codigo, estado, votos_real, votos_falso, actualizado_en from evento_vial where not simulado order by id");
        List<Map<String, Object>> votosAntes = jdbc.queryForList("select * from voto where not simulado order by id");
        assertThat(datosSimulados.contarFilasSimuladas()).isEqualTo(5); // 2 conductores, 2 eventos y 1 voto
        assertThat(eventosAntes).hasSize(2);

        mvc.perform(post("/simulacion/borrar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credencial\":\"" + CREDENCIAL + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.votos").value(1))
                .andExpect(jsonPath("$.eventos").value(2))
                .andExpect(jsonPath("$.conductores").value(2));

        assertThat(datosSimulados.contarFilasSimuladas()).isZero();
        assertThat(jdbc.queryForList("select * from conductor order by id")).isEqualTo(conductoresAntes);
        assertThat(jdbc.queryForList(
                "select id, tipo_codigo, estado, votos_real, votos_falso, actualizado_en from evento_vial order by id"))
                .isEqualTo(eventosAntes);
        assertThat(jdbc.queryForList("select * from voto order by id")).isEqualTo(votosAntes);

        // RN-02.15: los simulados que estaban en el mapa dejan de pesar en las rutas.
        trafico.enviarPendientes();
        ArgumentCaptor<ObjectNode> avisos = ArgumentCaptor.forClass(ObjectNode.class);
        verify(motor, org.mockito.Mockito.atLeastOnce()).actualizarTrafico(avisos.capture());
        assertThat(avisos.getAllValues()).anyMatch(a -> a.path("accion").asText().equals("retira")
                && a.path("evento").asText().equals(Long.toString(eventoSimulado)));
    }

    @Test
    @DisplayName("RN-07.8: el borrado con otra credencial responde 401 y no borra nada")
    void borradoConOtraCredencial() throws Exception {
        jwtFantasma("carga-1", 1);
        mvc.perform(post("/simulacion/borrar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credencial\":\"no-es-la-credencial-de-este-ambiente-0123456789\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(datosSimulados.contarFilasSimuladas()).isEqualTo(1);
    }

    @Test
    @DisplayName("RN-07.10 y RN-07.11: cada prueba de carga queda registrada con su identificador")
    void registroDeLaPruebaDeCarga() throws Exception {
        String uno = jwtFantasma("carga-registro", 1);
        jwtFantasma("carga-registro", 2);
        reloj.avanzar(Duration.ofSeconds(10));
        consultar(uno, null);
        reportar(uno, "ACCIDENTE", -21.5355, -64.7296).andExpect(status().isCreated());
        reportar(uno, "ACCIDENTE", -21.5355, -64.7296).andExpect(status().isConflict());
        consultar(jwtReal(), null); // un conductor real no cuenta

        var resumen = registro.resumen("carga-registro").orElseThrow();
        assertThat(resumen.usuarios()).isEqualTo(2);
        assertThat(resumen.peticiones()).isEqualTo(3);
        assertThat(resumen.errores()).isZero();
        assertThat(resumen.primeraEntrada()).isEqualTo(INICIO);
        assertThat(resumen.ultimaPeticion()).isEqualTo(INICIO.plusSeconds(10));

        registro.peticion("carga-registro", 503, 4000); // así se ve una petición cuando el backend se satura
        reloj.avanzar(Duration.ofMinutes(3));
        registro.informar();
        assertThat(registro.resumen("carga-registro")).as("terminó: sin peticiones hace más de 2 minutos").isEmpty();

        String texto = Files.readString(ARCHIVO_REGISTRO, StandardCharsets.UTF_8);
        assertThat(texto).contains("Prueba carga-registro: entró el primer usuario fantasma")
                .contains("Prueba carga-registro: el backend empezó a responder 503")
                .contains("Prueba carga-registro: terminó usuarios=2 peticiones=4 errores=1 saturadas=1 lentas=1")
                .doesNotContain("google").doesNotContain("prueba:");
    }
}
