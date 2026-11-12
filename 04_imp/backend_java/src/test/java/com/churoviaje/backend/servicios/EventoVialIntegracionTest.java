package com.churoviaje.backend.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.churoviaje.backend.config.ConfiguracionRelojDePrueba;
import com.churoviaje.backend.config.PruebaIntegracionPostgis;
import com.churoviaje.backend.config.RelojDePrueba;
import com.churoviaje.backend.seguridad.JwtServicio;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

/** HU-01 (#53): {@code POST /eventos} contra una base PostGIS real. CP-52 a CP-56 y las demas reglas de CU-01. */
@Import(ConfiguracionRelojDePrueba.class)
class EventoVialIntegracionTest extends PruebaIntegracionPostgis {

    private static final Instant INICIO = Instant.parse("2026-11-01T12:00:00Z");
    // Plaza Luis de Fuentes; unos 0,00045 grados de latitud son 50 m.
    private static final double LAT = -21.5355;
    private static final double LON = -64.7296;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtServicio jwtServicio;
    @Autowired RelojDePrueba reloj;

    private long conductor;

    @BeforeEach
    void preparar() {
        jdbc.update("delete from evento_vial");
        jdbc.update("delete from sesion");
        jdbc.update("delete from conductor");
        reloj.fijar(INICIO);
        conductor = crearConductor("google-reportante", false);
    }

    private long crearConductor(String googleId, boolean simulado) {
        return jdbc.queryForObject(
                "insert into conductor (google_id, fecha_alta, simulado) values (?, now(), ?) returning id",
                Long.class, googleId, simulado);
    }

    private String ahoraEnBolivia(Duration desplazamiento) {
        return OffsetDateTime.ofInstant(reloj.instant().plus(desplazamiento), ZoneOffset.ofHours(-4)).toString();
    }

    private String cuerpo(String tipo, double lat, double lon, Integer minutos) {
        return "{\"tipo\":\"" + tipo + "\",\"ubicacion\":{\"latitud\":" + lat + ",\"longitud\":" + lon + "},"
                + "\"horaConfirmacion\":\"" + ahoraEnBolivia(Duration.ZERO) + "\""
                + (minutos == null ? "" : ",\"minutosElegidos\":" + minutos) + "}";
    }

    private ResultActions reportar(long quien, String cuerpo) throws Exception {
        return mvc.perform(post("/eventos").header("Authorization", "Bearer " + jwtServicio.emitir(quien, reloj.instant()))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions reportar(String tipo, double lat, double lon, Integer minutos) throws Exception {
        return reportar(conductor, cuerpo(tipo, lat, lon, minutos));
    }

    private long eventosGuardados() {
        return jdbc.queryForObject("select count(*) from evento_vial", Long.class);
    }

    @Test
    @DisplayName("CP-52: un accidente «hace 5 minutos» en Tarija se registra Activo, 5 minutos antes de confirmar")
    void cp52AccidenteSeRegistraActivo() throws Exception {
        String respuesta = reportar("ACCIDENTE", LAT, LON, 5)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.tipo").value("ACCIDENTE"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.confirmado").value(false))
                .andExpect(jsonPath("$.origen").value("CONDUCTOR"))
                .andExpect(jsonPath("$.ubicacion.latitud").value(LAT))
                .andExpect(jsonPath("$.nivel").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        JsonNode cuerpo = json.readTree(respuesta);
        assertEquals(INICIO.minus(Duration.ofMinutes(5)),
                OffsetDateTime.parse(cuerpo.get("momentoOcurrencia").asText()).toInstant());
        Map<String, Object> fila = jdbc.queryForMap("select * from evento_vial where id = ?", cuerpo.get("id").asLong());
        assertEquals("ACTIVO", fila.get("estado"));
        assertEquals("CONDUCTOR", fila.get("origen"));
        assertEquals(0, fila.get("votos_real"));
        assertEquals(0, fila.get("votos_falso"));
        assertEquals(conductor, ((Number) fila.get("reportante_id")).longValue());
        assertEquals(false, fila.get("simulado"));
        Double distancia = jdbc.queryForObject("select ST_Distance(ubicacion, "
                + "ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography) from evento_vial", Double.class, LON, LAT);
        assertTrue(distancia < 0.01, "la ubicacion se guarda como punto PostGIS con longitud y latitud en orden");
    }

    @Test
    @DisplayName("RN-01.12: la respuesta nunca dice quien reporto el evento")
    void laRespuestaNoTraeAlReportante() throws Exception {
        String respuesta = reportar("OTRO", LAT, LON, 0).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode cuerpo = json.readTree(respuesta);
        List<String> campos = new ArrayList<>();
        cuerpo.fieldNames().forEachRemaining(campos::add);
        assertEquals(List.of("id", "tipo", "ubicacion", "momentoOcurrencia", "estado", "confirmado", "origen"), campos);
        assertFalse(respuesta.contains("reportante") || respuesta.contains("conductor") || respuesta.contains("google"));
    }

    @Test
    @DisplayName("CP-53: un reporte sin ubicacion responde 400 y no se guarda nada; lo mismo sin tipo o sin hora")
    void cp53SinDatosObligatoriosResponde400() throws Exception {
        reportar(conductor, "{\"tipo\":\"ACCIDENTE\",\"horaConfirmacion\":\"" + ahoraEnBolivia(Duration.ZERO) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PETICION_INVALIDA"))
                .andExpect(jsonPath("$.mensaje").value("Falta la ubicación"));
        reportar(conductor, "{\"ubicacion\":{\"latitud\":" + LAT + ",\"longitud\":" + LON + "},"
                + "\"horaConfirmacion\":\"" + ahoraEnBolivia(Duration.ZERO) + "\"}")
                .andExpect(status().isBadRequest());
        reportar(conductor, "{\"tipo\":\"ACCIDENTE\",\"ubicacion\":{\"latitud\":" + LAT + ",\"longitud\":" + LON + "}}")
                .andExpect(status().isBadRequest());
        assertEquals(0, eventosGuardados());
    }

    @Test
    @DisplayName("CP-54: dos controles policiales del mismo conductor a 50 m en 3 minutos: el segundo responde 409")
    void cp54ReporteDuplicadoResponde409() throws Exception {
        reportar("CONTROL_POLICIAL", LAT, LON, 0).andExpect(status().isCreated());
        reloj.avanzar(Duration.ofMinutes(3));
        reportar("CONTROL_POLICIAL", LAT + 0.00045, LON, 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("REPORTE_DUPLICADO"));
        assertEquals(1, eventosGuardados());
    }

    @Test
    @DisplayName("RN-01.8: no es duplicado si cambia el tipo, si esta a mas de 100 m, si pasaron 10 minutos o si es otro conductor")
    void noSonDuplicados() throws Exception {
        reportar("CONTROL_POLICIAL", LAT, LON, 0).andExpect(status().isCreated());
        reportar("ACCIDENTE", LAT, LON, 0).andExpect(status().isCreated());
        reportar("CONTROL_POLICIAL", LAT + 0.0018, LON, 0).andExpect(status().isCreated()); // unos 200 m
        reportar(crearConductor("google-otro", false), cuerpo("CONTROL_POLICIAL", LAT, LON, 0))
                .andExpect(status().isCreated());
        reloj.avanzar(Duration.ofMinutes(10));
        reportar("CONTROL_POLICIAL", LAT, LON, 0).andExpect(status().isCreated());
        assertEquals(5, eventosGuardados());
    }

    @Test
    @DisplayName("CP-55: el sexto reporte de un conductor en 10 minutos responde 429; pasados los 10 minutos, vuelve a poder")
    void cp55SextoReporteResponde429() throws Exception {
        String[] tipos = {"ACCIDENTE", "CONTROL_POLICIAL", "BLOQUEO", "OTRO", "ACCIDENTE"};
        for (int i = 0; i < 5; i++) {
            reportar(tipos[i], LAT + i * 0.004, LON, 0).andExpect(status().isCreated());
            reloj.avanzar(Duration.ofMinutes(1));
        }
        reportar("BLOQUEO", LAT - 0.004, LON, 0)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("LIMITE_DE_REPORTES"));
        assertEquals(5, eventosGuardados());

        reloj.avanzar(Duration.ofMinutes(6)); // el primero ya quedo fuera de la ventana
        reportar("BLOQUEO", LAT - 0.004, LON, 0).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("CP-56: un reporte de congestion responde 422; un tipo que no existe, tambien")
    void cp56CongestionResponde422() throws Exception {
        reportar("CONGESTION", LAT, LON, 0)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TIPO_NO_REPORTABLE"));
        reportar("INUNDACION", LAT, LON, 0).andExpect(status().isUnprocessableEntity());
        assertEquals(0, eventosGuardados());
    }

    @Test
    @DisplayName("RN-01.6: una ubicacion fuera de la mancha urbana de Tarija responde 422")
    void fueraDeTarijaResponde422() throws Exception {
        reportar("ACCIDENTE", -21.4181, -64.7494, 0) // San Lorenzo
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("UBICACION_FUERA_COBERTURA"));
        assertEquals(0, eventosGuardados());
    }

    @Test
    @DisplayName("RN-01.7: un reporte que llega cuando el evento ya vencio responde 422 con el aviso")
    void eventoVencidoResponde422() throws Exception {
        String cuerpo = "{\"tipo\":\"ACCIDENTE\",\"ubicacion\":{\"latitud\":" + LAT + ",\"longitud\":" + LON + "},"
                + "\"horaConfirmacion\":\"" + ahoraEnBolivia(Duration.ofMinutes(-55)) + "\",\"minutosElegidos\":10}";
        reportar(conductor, cuerpo)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("EVENTO_VENCIDO"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
        assertEquals(0, eventosGuardados());
    }

    @Test
    @DisplayName("RN-01.4: sin minutos elegidos vale «hace 5 minutos»; un tiempo que no es del catalogo responde 400")
    void minutosPorDefectoYTiemposValidos() throws Exception {
        String respuesta = reportar("ACCIDENTE", LAT, LON, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertEquals(INICIO.minus(Duration.ofMinutes(5)),
                OffsetDateTime.parse(json.readTree(respuesta).get("momentoOcurrencia").asText()).toInstant());
        reportar("BLOQUEO", LAT, LON, 7).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("RN-01.17: un conductor simulado deja eventos simulados")
    void conductorSimuladoDejaEventosSimulados() throws Exception {
        long fantasma = crearConductor("simulado-1", true);
        reportar(fantasma, cuerpo("ACCIDENTE", LAT, LON, 0)).andExpect(status().isCreated());
        assertEquals(Boolean.TRUE, jdbc.queryForObject("select simulado from evento_vial", Boolean.class));
    }

    @Test
    @DisplayName("RN-01.8 y RN-01.11: dos reportes iguales que llegan a la vez dejan un solo evento")
    void dosReportesALaVezDejanUnSoloEvento() throws Exception {
        String cuerpo = cuerpo("BLOQUEO", LAT, LON, 0);
        String autorizacion = "Bearer " + jwtServicio.emitir(conductor, reloj.instant());
        Callable<Integer> enviar = () -> mvc.perform(post("/eventos").header("Authorization", autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andReturn().getResponse().getStatus();
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> resultados = hilos.invokeAll(List.of(enviar, enviar));
            List<Integer> estados = new ArrayList<>();
            for (Future<Integer> resultado : resultados) {
                estados.add(resultado.get());
            }
            estados.sort(Integer::compareTo);
            assertEquals(List.of(201, 409), estados);
        } finally {
            hilos.shutdownNow();
        }
        assertEquals(1, eventosGuardados());
    }

    @Test
    @DisplayName("RN-01.1: con el JWT de un conductor que ya no existe, el reporte responde 401")
    void conductorInexistenteResponde401() throws Exception {
        reportar(999_999L, cuerpo("ACCIDENTE", LAT, LON, 0)).andExpect(status().isUnauthorized());
        assertEquals(0, eventosGuardados());
    }
}
