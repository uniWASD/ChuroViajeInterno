package com.churoviaje.backend.servicios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
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
import com.churoviaje.backend.motor.MotorDeRutas;
import com.churoviaje.backend.motor.MotorNoDisponibleException;
import com.churoviaje.backend.motor.TraficoDelMotor;
import com.churoviaje.backend.seguridad.JwtServicio;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
 * HU-13 (#74): {@code POST /telemetria} de punta a punta en el backend. El motor es un doble que devuelve las
 * congestiones de cada prueba y registra los avisos de tráfico que le llegan.
 */
@Import(ConfiguracionRelojDePrueba.class)
@TestPropertySource(properties = {"motor.habilitado=true", "motor.ejecutable=no-se-usa", "motor.red-vial=no-se-usa"})
class TelemetriaIntegracionTest extends PruebaIntegracionPostgis {

    private static final Instant INICIO = Instant.parse("2026-11-14T14:00:00Z");
    private static final String LECTURAS = """
            {"lecturas":[
              {"ubicacion":{"latitud":-21.5400,"longitud":-64.7380},"precisionM":8,"momento":"2026-11-14T10:00:00-04:00"},
              {"ubicacion":{"latitud":-21.5400,"longitud":-64.73794},"precisionM":6.5,"momento":"2026-11-14T10:00:03-04:00"}]}""";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtServicio jwtServicio;
    @Autowired RelojDePrueba reloj;
    @Autowired TelemetriaServicio telemetria;
    @Autowired TraficoDelMotor trafico;
    @MockitoBean MotorDeRutas motor;

    private long conductor;

    @BeforeEach
    void preparar() throws Exception {
        jdbc.update("delete from voto");
        jdbc.update("delete from evento_vial");
        jdbc.update("delete from sesion");
        jdbc.update("delete from conductor");
        reloj.fijar(INICIO);
        conductor = crearConductor("google-telemetria", false);
        reset(motor);
        when(motor.actualizarTrafico(any())).thenReturn(json.readTree("{\"ok\":true,\"tramos\":[]}"));
        motorDetecta();
        trafico.enviarPendientes();
        clearInvocations(motor);
    }

    private long crearConductor(String googleId, boolean simulado) {
        return jdbc.queryForObject("insert into conductor (google_id, fecha_alta, simulado) values (?, now(), ?) returning id",
                Long.class, googleId, simulado);
    }

    /** El doble del motor responde estas congestiones a cada analizar_telemetria. */
    private void motorDetecta(String... congestiones) throws Exception {
        when(motor.analizarTelemetria(any())).thenReturn(json.readTree("{\"ok\":true,\"congestiones\":["
                + String.join(",", congestiones) + "],\"lecturas_validas\":2,\"lecturas_descartadas\":0}"));
    }

    private static String congestion(String tramo, String nivel, boolean simulado) {
        return "{\"tramo\":\"" + tramo + "\",\"punto\":[-21.54,-64.73794],\"nivel\":\"" + nivel + "\",\"simulado\":"
                + simulado + "}";
    }

    private ResultActions enviar(long quien, String cuerpo) throws Exception {
        return mvc.perform(post("/telemetria").header("Authorization", "Bearer " + jwtServicio.emitir(quien, reloj.instant()))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    /** Envía, espera a que el hilo de la telemetría termine y a que el aviso le llegue al motor. */
    private void enviarYEsperar(long quien) throws Exception {
        enviar(quien, LECTURAS).andExpect(status().isAccepted());
        assertThat(telemetria.esperarPendientes(5000)).isTrue();
        trafico.enviarPendientes();
    }

    private JsonNode eventos() throws Exception {
        String r = mvc.perform(get("/eventos").header("Authorization", "Bearer " + jwtServicio.emitir(conductor, reloj.instant())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(r).path("eventos");
    }

    private List<JsonNode> avisosAlMotor() {
        ArgumentCaptor<ObjectNode> mensajes = ArgumentCaptor.forClass(ObjectNode.class);
        verify(motor, atLeastOnce()).actualizarTrafico(mensajes.capture());
        return new ArrayList<>(mensajes.getAllValues());
    }

    private JsonNode mensajeAlMotor() throws Exception {
        ArgumentCaptor<ObjectNode> mensaje = ArgumentCaptor.forClass(ObjectNode.class);
        verify(motor, timeout(5000).atLeastOnce()).analizarTelemetria(mensaje.capture());
        return mensaje.getValue();
    }

    @Test
    @DisplayName("CP-119: cuando el motor informa congestión en un tramo, aparece un evento Congestión con origen Telemetría en GET /eventos")
    void cp119CongestionEnGetEventos() throws Exception {
        motorDetecta(congestion("osm:7101:2:ida", "MUY_CONGESTIONADO", false));
        enviarYEsperar(conductor);

        JsonNode lista = eventos();
        assertThat(lista).hasSize(1);
        JsonNode e = lista.get(0);
        assertThat(e.path("tipo").asText()).isEqualTo("CONGESTION");
        assertThat(e.path("origen").asText()).isEqualTo("TELEMETRIA");
        assertThat(e.path("nivel").asText()).isEqualTo("MUY_CONGESTIONADO");
        assertThat(e.path("ubicacion").path("latitud").asDouble()).isEqualTo(-21.54);

        Map<String, Object> fila = jdbc.queryForMap("select reportante_id, tramo_id, simulado from evento_vial");
        assertThat(fila.get("reportante_id")).as("RN-04.12: sin reportante").isNull();
        assertThat(fila.get("tramo_id")).isEqualTo("osm:7101:2:ida");
        assertThat(fila.get("simulado")).isEqualTo(false);

        // D-14 y P-12: el motor la recibe con su nivel y su tramo, para que pese ×4,0 solo en ese sentido.
        JsonNode aviso = avisosAlMotor().get(0);
        assertThat(aviso.path("accion").asText()).isEqualTo("nace");
        assertThat(aviso.path("tipo").asText()).isEqualTo("CONGESTION");
        assertThat(aviso.path("nivel").asText()).isEqualTo("MUY_CONGESTIONADO");
        assertThat(aviso.path("tramo").asText()).isEqualTo("osm:7101:2:ida");
        assertThat(aviso.path("vence").asText()).isEqualTo("2026-11-14T10:30:00-04:00"); // 30 minutos de vigencia
    }

    @Test
    @DisplayName("CP-120: una segunda congestión en el mismo tramo no crea otro evento; mueve el momento del existente")
    void cp120SegundaCongestionMueveElMomento() throws Exception {
        motorDetecta(congestion("osm:7101:2:ida", "CONGESTIONADO", false));
        enviarYEsperar(conductor);
        long id = jdbc.queryForObject("select id from evento_vial", Long.class);
        clearInvocations(motor);

        reloj.avanzar(Duration.ofMinutes(5));
        motorDetecta(congestion("osm:7101:2:ida", "MUY_CONGESTIONADO", false));
        enviarYEsperar(conductor);

        assertThat(jdbc.queryForObject("select count(*) from evento_vial", Long.class)).isEqualTo(1);
        Map<String, Object> fila = jdbc.queryForMap("select id, nivel, ocurrio_en, actualizado_en from evento_vial");
        assertThat(fila.get("id")).isEqualTo(id);
        assertThat(fila.get("nivel")).isEqualTo("MUY_CONGESTIONADO");
        assertThat(((java.sql.Timestamp) fila.get("ocurrio_en")).toInstant()).isEqualTo(INICIO.plus(Duration.ofMinutes(5)));

        JsonNode aviso = avisosAlMotor().get(0);
        assertThat(aviso.path("accion").asText()).isEqualTo("cambia_vencimiento");
        assertThat(aviso.path("evento").asText()).isEqualTo(String.valueOf(id));
        assertThat(aviso.path("vence").asText()).isEqualTo("2026-11-14T10:35:00-04:00");

        // La app que consulta solo los cambios la recibe otra vez, con el momento y el nivel nuevos (RN-02.6).
        String marca = INICIO.plus(Duration.ofMinutes(1)).toString();
        mvc.perform(get("/eventos").param("desde", marca)
                        .header("Authorization", "Bearer " + jwtServicio.emitir(conductor, reloj.instant())))
                .andExpect(jsonPath("$.eventos[0].id").value(id))
                .andExpect(jsonPath("$.eventos[0].nivel").value("MUY_CONGESTIONADO"));
    }

    @Test
    @DisplayName("Una congestión vencida que la tarea todavía no marcó se cierra y nace otra")
    void congestionVencidaNoSeExtiende() throws Exception {
        motorDetecta(congestion("osm:7101:2:ida", "CONGESTIONADO", false));
        enviarYEsperar(conductor);
        reloj.avanzar(Duration.ofMinutes(31));
        enviarYEsperar(conductor);

        assertThat(jdbc.queryForList("select estado from evento_vial order by id", String.class))
                .containsExactly("VENCIDO", "ACTIVO");
    }

    @Test
    @DisplayName("Otro tramo, u otra marca de simulado, es otra congestión")
    void otroTramoEsOtraCongestion() throws Exception {
        motorDetecta(congestion("osm:7101:2:ida", "CONGESTIONADO", false),
                congestion("osm:7101:2:vuelta", "CONGESTIONADO", false),
                congestion("osm:7101:2:ida", "CONGESTIONADO", true));
        enviarYEsperar(conductor);
        assertThat(jdbc.queryForObject("select count(*) from evento_vial", Long.class)).isEqualTo(3);
    }

    @Test
    @DisplayName("CP-121: después de procesar las lecturas, la base no tiene ninguna fila de telemetría")
    void cp121LaBaseNoGuardaTelemetria() throws Exception {
        List<String> tablas = jdbc.queryForList("""
                select table_name from information_schema.tables
                where table_schema = 'public' and table_type = 'BASE TABLE'""", String.class);
        assertThat(tablas).noneMatch(t -> t.contains("telemetr") || t.contains("lectura") || t.contains("recorrido"));
        long antes = filasEn(tablas);

        enviarYEsperar(conductor);
        enviarYEsperar(conductor);

        assertThat(filasEn(tablas)).as("ninguna tabla ganó filas con las lecturas").isEqualTo(antes);
        String columnas = String.join(",", jdbc.queryForList(
                "select column_name from information_schema.columns where table_schema = 'public'", String.class));
        assertThat(columnas).doesNotContain("recorrido").doesNotContain("precision");
    }

    private long filasEn(List<String> tablas) {
        long total = 0;
        for (String t : tablas) {
            total += jdbc.queryForObject("select count(*) from \"" + t + "\"", Long.class);
        }
        return total;
    }

    @Test
    @DisplayName("Al motor llegan las lecturas con un recorrido que no es el conductor y que no cambia al renovar el JWT")
    void elMotorRecibeUnRecorridoSinDatosDelConductor() throws Exception {
        enviar(conductor, LECTURAS).andExpect(status().isAccepted());
        JsonNode m = mensajeAlMotor();
        JsonNode grupo = m.path("recorridos").path(0);
        String recorrido = grupo.path("recorrido").asText();
        assertThat(recorrido).matches("[0-9a-f]{32}");
        assertThat(m.toString()).doesNotContain("google-telemetria").doesNotContain("\"" + conductor + "\"");
        assertThat(grupo.path("simulado").asBoolean()).isFalse();
        assertThat(grupo.path("lecturas")).hasSize(2);
        JsonNode lectura = grupo.path("lecturas").path(1);
        assertThat(lectura.path("posicion").path(1).asDouble()).isEqualTo(-64.73794);
        assertThat(lectura.path("precision_m").asDouble()).isEqualTo(6.5);
        assertThat(lectura.path("momento").asText()).isEqualTo("2026-11-14T10:00:03-04:00");

        // Un JWT nuevo (renovado una hora después) es el mismo recorrido; otro conductor, otro.
        clearInvocations(motor);
        reloj.avanzar(Duration.ofHours(1));
        enviar(conductor, LECTURAS).andExpect(status().isAccepted());
        assertThat(mensajeAlMotor().path("recorridos").path(0).path("recorrido").asText()).isEqualTo(recorrido);
        clearInvocations(motor);
        enviar(crearConductor("google-otro", false), LECTURAS).andExpect(status().isAccepted());
        assertThat(mensajeAlMotor().path("recorridos").path(0).path("recorrido").asText()).isNotEqualTo(recorrido);
    }

    @Test
    @DisplayName("RN-04.20: la telemetría de un conductor simulado va marcada y deja una congestión simulada")
    void conductorSimulado() throws Exception {
        long fantasma = crearConductor("simulado-1", true);
        motorDetecta(congestion("osm:7101:2:ida", "CONGESTIONADO", true));
        enviar(fantasma, LECTURAS).andExpect(status().isAccepted());
        assertThat(mensajeAlMotor().path("recorridos").path(0).path("simulado").asBoolean()).isTrue();
        assertThat(telemetria.esperarPendientes(5000)).isTrue();
        assertThat(jdbc.queryForObject("select simulado from evento_vial", Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("Responde «recibido» en menos de 300 ms aunque el motor tarde en analizar")
    void respondeSinEsperarAlMotor() throws Exception {
        when(motor.analizarTelemetria(any())).thenAnswer(invocacion -> {
            Thread.sleep(1500);
            return json.readTree("{\"ok\":true,\"congestiones\":[]}");
        });
        enviar(conductor, LECTURAS).andExpect(status().isAccepted()); // la primera calienta el controlador
        long inicio = System.nanoTime();
        enviar(conductor, LECTURAS).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.estado").value("RECIBIDO"))
                .andExpect(jsonPath("$.lecturas").value(2));
        assertThat((System.nanoTime() - inicio) / 1_000_000).isLessThan(300);
        assertThat(telemetria.esperarPendientes(10_000)).isTrue();
    }

    @Test
    @DisplayName("Con el motor caído la app recibe 202 igual y no queda nada guardado")
    void motorCaido() throws Exception {
        when(motor.analizarTelemetria(any())).thenThrow(new MotorNoDisponibleException());
        enviarYEsperar(conductor);
        assertThat(jdbc.queryForObject("select count(*) from evento_vial", Long.class)).isZero();
    }

    @Test
    @DisplayName("RN-04.4: sin JWT o con el de un conductor que ya no existe responde 401 y no llama al motor")
    void soloConductoresConSesion() throws Exception {
        mvc.perform(post("/telemetria").contentType(MediaType.APPLICATION_JSON).content(LECTURAS))
                .andExpect(status().isUnauthorized());
        enviar(999_999L, LECTURAS).andExpect(status().isUnauthorized());
        verify(motor, after(300).never()).analizarTelemetria(any());
    }

    @Test
    @DisplayName("Sin lecturas, con una lectura incompleta o con precisión negativa responde 400")
    void peticionInvalida() throws Exception {
        enviar(conductor, "{\"lecturas\":[]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PETICION_INVALIDA"));
        enviar(conductor, """
                {"lecturas":[{"ubicacion":{"latitud":-21.54,"longitud":-64.738},"momento":"2026-11-14T10:00:00-04:00"}]}""")
                .andExpect(status().isBadRequest());
        enviar(conductor, """
                {"lecturas":[{"ubicacion":{"latitud":-21.54,"longitud":-64.738},"precisionM":-1,
                  "momento":"2026-11-14T10:00:00-04:00"}]}""")
                .andExpect(status().isBadRequest());
        verify(motor, after(300).never()).analizarTelemetria(any());
    }
}
