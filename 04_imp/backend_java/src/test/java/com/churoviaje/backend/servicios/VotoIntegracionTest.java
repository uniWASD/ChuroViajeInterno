package com.churoviaje.backend.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.churoviaje.backend.config.ConfiguracionRelojDePrueba;
import com.churoviaje.backend.config.PruebaIntegracionPostgis;
import com.churoviaje.backend.config.RelojDePrueba;
import com.churoviaje.backend.seguridad.JwtServicio;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** HU-11 (#63): {@code POST /eventos/{id}/votos}. CP-86 a CP-89 y las demas reglas del voto. */
@Import(ConfiguracionRelojDePrueba.class)
class VotoIntegracionTest extends PruebaIntegracionPostgis {

    private static final Instant INICIO = Instant.parse("2026-11-01T12:00:00Z");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtServicio jwtServicio;
    @Autowired RelojDePrueba reloj;

    private long reportante;
    private final List<Long> votantes = new ArrayList<>();

    @BeforeEach
    void preparar() {
        jdbc.update("delete from voto");
        jdbc.update("delete from evento_vial");
        jdbc.update("delete from sesion");
        jdbc.update("delete from conductor");
        reloj.fijar(INICIO);
        reportante = crearConductor("google-reportante");
        votantes.clear();
        for (int i = 1; i <= 6; i++) {
            votantes.add(crearConductor("google-votante-" + i));
        }
    }

    private long crearConductor(String googleId) {
        return jdbc.queryForObject(
                "insert into conductor (google_id, fecha_alta) values (?, now()) returning id", Long.class, googleId);
    }

    private long evento(String tipo, long minutosAtras, String origen, String estado) {
        Instant ocurrio = reloj.instant().minus(Duration.ofMinutes(minutosAtras));
        boolean conductor = origen.equals("CONDUCTOR");
        return jdbc.queryForObject("""
                insert into evento_vial (tipo_codigo, ubicacion, ocurrio_en, registrado_en, actualizado_en,
                                         reportante_id, origen, nivel, estado)
                values (?, ST_SetSRID(ST_MakePoint(-64.7296, -21.5355), 4326)::geography, ?, ?, ?, ?, ?, ?, ?)
                returning id
                """, Long.class, tipo, Timestamp.from(ocurrio), Timestamp.from(ocurrio), Timestamp.from(ocurrio),
                conductor ? reportante : null, origen, tipo.equals("CONGESTION") ? "CONGESTIONADO" : null, estado);
    }

    private long accidente() {
        return evento("ACCIDENTE", 5, "CONDUCTOR", "ACTIVO");
    }

    private ResultActions votar(long quien, long evento, String valor) throws Exception {
        return mvc.perform(post("/eventos/" + evento + "/votos")
                .header("Authorization", "Bearer " + jwtServicio.emitir(quien, reloj.instant()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"valor\":\"" + valor + "\"}"));
    }

    private String estado(long evento) {
        return jdbc.queryForObject("select estado from evento_vial where id = ?", String.class, evento);
    }

    private JsonNode consultar(String marca) throws Exception {
        var peticion = get("/eventos").header("Authorization", "Bearer " + jwtServicio.emitir(reportante, reloj.instant()));
        if (marca != null) {
            peticion = peticion.param("desde", marca);
        }
        return json.readTree(mvc.perform(peticion).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("CP-86: tres conductores votan «falso» un accidente; queda Retirado y no sale en la siguiente GET /eventos")
    void cp86TresFalsosLoRetiran() throws Exception {
        long evento = accidente();
        String marca = consultar(null).get("marca").asText();
        reloj.avanzar(Duration.ofSeconds(5));

        votar(votantes.get(0), evento, "falso").andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("ACTIVO"));
        votar(votantes.get(1), evento, "falso").andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("ACTIVO"));
        votar(votantes.get(2), evento, "falso").andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventoId").value(evento))
                .andExpect(jsonPath("$.estado").value("RETIRADO"))
                .andExpect(jsonPath("$.confirmado").value(false));
        assertEquals("RETIRADO", estado(evento));
        assertEquals(3, jdbc.queryForObject("select count(*) from voto where evento_id = ?", Integer.class, evento));

        reloj.avanzar(Duration.ofSeconds(5));
        assertEquals(0, consultar(null).get("eventos").size());
        JsonNode cambios = consultar(marca);
        assertEquals(0, cambios.get("eventos").size());
        assertEquals(evento, cambios.get("quitados").get(0).asLong(), "la app lo quita en su siguiente consulta");
    }

    @Test
    @DisplayName("CP-87: un evento con 3 votos «real» recibe despues 3 «falso» y sigue Confirmado")
    void cp87ConfirmadoNoSeRetira() throws Exception {
        long evento = accidente();
        String marca = consultar(null).get("marca").asText();
        reloj.avanzar(Duration.ofSeconds(5));
        for (int i = 0; i < 3; i++) {
            votar(votantes.get(i), evento, "real").andExpect(status().isCreated());
        }
        assertEquals("CONFIRMADO", estado(evento));

        reloj.avanzar(Duration.ofSeconds(5));
        JsonNode cambios = consultar(marca);
        assertEquals(evento, cambios.get("eventos").get(0).get("id").asLong(), "la app lo recibe entre los modificados");
        assertTrue(cambios.get("eventos").get(0).get("confirmado").asBoolean());

        for (int i = 3; i < 6; i++) {
            votar(votantes.get(i), evento, "falso").andExpect(status().isCreated())
                    .andExpect(jsonPath("$.estado").value("CONFIRMADO"))
                    .andExpect(jsonPath("$.confirmado").value(true));
        }
        assertEquals("CONFIRMADO", estado(evento));
        assertEquals(1, consultar(null).get("eventos").size());
    }

    @Test
    @DisplayName("CP-88: el mismo conductor vota dos veces; el segundo voto responde 409")
    void cp88SegundoVotoResponde409() throws Exception {
        long evento = accidente();
        votar(votantes.get(0), evento, "real").andExpect(status().isCreated());
        votar(votantes.get(0), evento, "falso").andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("VOTO_REPETIDO"));
        assertEquals(1, jdbc.queryForObject("select votos_real + votos_falso from evento_vial where id = ?",
                Integer.class, evento));
    }

    @Test
    @DisplayName("CP-89: el reportante intenta votar su evento y recibe 422")
    void cp89ElReportanteNoVota() throws Exception {
        long evento = accidente();
        votar(reportante, evento, "real").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VOTO_PROPIO"));
        assertEquals(0, jdbc.queryForObject("select count(*) from voto", Integer.class));
    }

    @Test
    @DisplayName("RN-02.10 y D-20: no se votan la congestion de telemetria, ni un evento retirado, vencido o que ya no existe")
    void soloSeVotanEventosDeConductorVigentes() throws Exception {
        votar(votantes.get(0), evento("CONGESTION", 5, "TELEMETRIA", "ACTIVO"), "falso")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("VOTO_NO_PERMITIDO"));
        votar(votantes.get(0), evento("OTRO", 5, "CONDUCTOR", "RETIRADO"), "real")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("EVENTO_NO_VIGENTE"));
        votar(votantes.get(0), evento("BLOQUEO", 5, "CONDUCTOR", "VENCIDO"), "real")
                .andExpect(status().isUnprocessableEntity());
        // Activo en la base, pero ya paso su vigencia aunque la tarea periodica no lo haya marcado.
        votar(votantes.get(0), evento("CONTROL_POLICIAL", 61, "CONDUCTOR", "ACTIVO"), "real")
                .andExpect(status().isUnprocessableEntity());
        votar(votantes.get(0), 999_999L, "real").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("EVENTO_NO_ENCONTRADO"));
        assertEquals(0, jdbc.queryForObject("select count(*) from voto", Integer.class));
    }

    @Test
    @DisplayName("Un voto que no es «real» ni «falso», o que falta, responde 400")
    void votoInvalidoResponde400() throws Exception {
        long evento = accidente();
        votar(votantes.get(0), evento, "tal vez").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PETICION_INVALIDA"));
        mvc.perform(post("/eventos/" + evento + "/votos")
                        .header("Authorization", "Bearer " + jwtServicio.emitir(votantes.get(0), reloj.instant()))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("La respuesta trae solo el estado del evento, sin datos de quien voto")
    void laRespuestaNoDiceQuienVoto() throws Exception {
        String respuesta = votar(votantes.get(0), accidente(), "real").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        List<String> campos = new ArrayList<>();
        json.readTree(respuesta).fieldNames().forEachRemaining(campos::add);
        assertEquals(List.of("eventoId", "estado", "confirmado"), campos);
    }

    @Test
    @DisplayName("RN-02.11: dos votos del mismo conductor que llegan a la vez dejan uno solo")
    void dosVotosALaVezDelMismoConductor() throws Exception {
        long evento = accidente();
        List<Integer> estados = enParalelo(List.of(
                () -> votar(votantes.get(0), evento, "falso").andReturn().getResponse().getStatus(),
                () -> votar(votantes.get(0), evento, "falso").andReturn().getResponse().getStatus()));
        assertEquals(List.of(201, 409), estados);
        assertEquals(1, jdbc.queryForObject("select votos_falso from evento_vial where id = ?", Integer.class, evento));
    }

    @Test
    @DisplayName("RN-02.13: tres votos «falso» de conductores distintos que llegan a la vez se cuentan todos")
    void tresVotosALaVezSeCuentanTodos() throws Exception {
        long evento = accidente();
        List<Callable<Integer>> votos = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            long votante = votantes.get(i);
            votos.add(() -> votar(votante, evento, "falso").andReturn().getResponse().getStatus());
        }
        assertEquals(List.of(201, 201, 201), enParalelo(votos));
        assertEquals(3, jdbc.queryForObject("select votos_falso from evento_vial where id = ?", Integer.class, evento));
        assertEquals("RETIRADO", estado(evento));
    }

    private static List<Integer> enParalelo(List<Callable<Integer>> tareas) throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(tareas.size());
        try {
            List<Integer> estados = new ArrayList<>();
            for (Future<Integer> resultado : hilos.invokeAll(tareas)) {
                estados.add(resultado.get());
            }
            estados.sort(Integer::compareTo);
            return estados;
        } finally {
            hilos.shutdownNow();
        }
    }
}
