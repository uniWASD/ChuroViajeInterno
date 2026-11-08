package com.churoviaje.backend.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** HU-03 (#55): {@code GET /eventos}, la consulta por cambios y el vencimiento. CP-61 a CP-64. */
@Import(ConfiguracionRelojDePrueba.class)
class ConsultaEventosIntegracionTest extends PruebaIntegracionPostgis {

    private static final Instant INICIO = Instant.parse("2026-11-01T12:00:00Z");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtServicio jwtServicio;
    @Autowired RelojDePrueba reloj;
    @Autowired VencimientoEventos vencimiento;

    private long reportante;
    private long lector;

    @BeforeEach
    void preparar() {
        jdbc.update("delete from evento_vial");
        jdbc.update("delete from sesion");
        jdbc.update("delete from conductor");
        reloj.fijar(INICIO);
        reportante = crearConductor("google-reportante");
        lector = crearConductor("google-lector");
    }

    private long crearConductor(String googleId) {
        return jdbc.queryForObject(
                "insert into conductor (google_id, fecha_alta) values (?, now()) returning id", Long.class, googleId);
    }

    /** Guarda un evento de conductor que ocurrio hace {@code minutos} y se registro en ese momento. */
    private long evento(String tipo, long minutos, String estado) {
        Instant ocurrio = reloj.instant().minus(Duration.ofMinutes(minutos));
        return jdbc.queryForObject("""
                insert into evento_vial (tipo_codigo, ubicacion, ocurrio_en, registrado_en, actualizado_en,
                                         reportante_id, origen, estado)
                values (?, ST_SetSRID(ST_MakePoint(-64.7296, -21.5355), 4326)::geography, ?, ?, ?, ?, 'CONDUCTOR', ?)
                returning id
                """, Long.class, tipo, Timestamp.from(ocurrio), Timestamp.from(ocurrio), Timestamp.from(ocurrio),
                reportante, estado);
    }

    private long congestion(long minutos, String nivel) {
        Instant ocurrio = reloj.instant().minus(Duration.ofMinutes(minutos));
        return jdbc.queryForObject("""
                insert into evento_vial (tipo_codigo, ubicacion, ocurrio_en, registrado_en, actualizado_en,
                                         origen, nivel, estado)
                values ('CONGESTION', ST_SetSRID(ST_MakePoint(-64.7296, -21.5355), 4326)::geography, ?, ?, ?,
                        'TELEMETRIA', ?, 'ACTIVO')
                returning id
                """, Long.class, Timestamp.from(ocurrio), Timestamp.from(ocurrio), Timestamp.from(ocurrio), nivel);
    }

    private void cambiarEstado(long id, String estado) {
        jdbc.update("update evento_vial set estado = ?, actualizado_en = ? where id = ?",
                estado, Timestamp.from(reloj.instant()), id);
    }

    private ResultActions consultar(String marca) throws Exception {
        var peticion = get("/eventos").header("Authorization", "Bearer " + jwtServicio.emitir(lector, reloj.instant()));
        if (marca != null) {
            peticion = peticion.param("desde", marca);
        }
        return mvc.perform(peticion);
    }

    private JsonNode cuerpo(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static List<Long> ids(JsonNode lista) {
        List<Long> ids = new ArrayList<>();
        lista.forEach(nodo -> ids.add(nodo.has("id") ? nodo.get("id").asLong() : nodo.asLong()));
        return ids;
    }

    @Test
    @DisplayName("RN-02.2 y RN-02.3: salen los Activos y Confirmados vigentes; nunca los Retirados ni los Vencidos")
    void soloSalenLosVigentes() throws Exception {
        long activo = evento("ACCIDENTE", 10, "ACTIVO");
        long confirmado = evento("BLOQUEO", 59, "CONFIRMADO");
        evento("OTRO", 5, "RETIRADO");
        evento("ACCIDENTE", 5, "VENCIDO");
        evento("CONTROL_POLICIAL", 60, "ACTIVO"); // vence justo ahora

        JsonNode respuesta = cuerpo(consultar(null));
        assertTrue(respuesta.get("completa").asBoolean());
        assertEquals(List.of(activo, confirmado), ids(respuesta.get("eventos")));
        assertEquals(0, respuesta.get("quitados").size());
        assertTrue(respuesta.get("eventos").get(1).get("confirmado").asBoolean());
    }

    @Test
    @DisplayName("CP-61: un control policial de hace 61 minutos ya no sale y la tarea periodica lo deja Vencido")
    void cp61ControlDeHace61MinutosQuedaVencido() throws Exception {
        long control = evento("CONTROL_POLICIAL", 61, "ACTIVO");
        assertEquals(0, cuerpo(consultar(null)).get("eventos").size());

        assertEquals(List.of(control), vencimiento.vencer());
        assertEquals("VENCIDO", jdbc.queryForObject("select estado from evento_vial where id = ?", String.class, control));
        assertEquals(List.of(), vencimiento.vencer(), "un evento ya vencido no se vuelve a vencer");
    }

    @Test
    @DisplayName("RN-02.3: la congestion vale 30 minutos y sale con su nivel y su origen")
    void laCongestionValeTreintaMinutos() throws Exception {
        long reciente = congestion(29, "MUY_CONGESTIONADO");
        congestion(31, "CONGESTIONADO");

        JsonNode eventos = cuerpo(consultar(null)).get("eventos");
        assertEquals(List.of(reciente), ids(eventos));
        assertEquals("TELEMETRIA", eventos.get(0).get("origen").asText());
        assertEquals("MUY_CONGESTIONADO", eventos.get(0).get("nivel").asText());
    }

    @Test
    @DisplayName("CP-62: con la marca anterior llegan solo los nuevos, los que se confirmaron y los que dejaron de estar vigentes")
    void cp62ConLaMarcaLleganSoloLosCambios() throws Exception {
        long quieto = evento("ACCIDENTE", 10, "ACTIVO");
        long seConfirma = evento("BLOQUEO", 10, "ACTIVO");
        long seRetira = evento("OTRO", 10, "ACTIVO");
        String marca = cuerpo(consultar(null)).get("marca").asText();

        reloj.avanzar(Duration.ofSeconds(5));
        long nuevo = evento("CONTROL_POLICIAL", 0, "ACTIVO");
        cambiarEstado(seConfirma, "CONFIRMADO");
        cambiarEstado(seRetira, "RETIRADO");
        reloj.avanzar(Duration.ofSeconds(5));

        JsonNode cambios = cuerpo(consultar(marca));
        assertFalse(cambios.get("completa").asBoolean());
        assertEquals(List.of(seConfirma, nuevo), ids(cambios.get("eventos")));
        assertEquals(List.of(seRetira), ids(cambios.get("quitados")));
        assertFalse(ids(cambios.get("eventos")).contains(quieto));

        // Con la marca nueva, y sin cambios, no llega nada.
        reloj.avanzar(Duration.ofSeconds(5));
        JsonNode sinCambios = cuerpo(consultar(cambios.get("marca").asText()));
        assertEquals(0, sinCambios.get("eventos").size());
        assertEquals(0, sinCambios.get("quitados").size());
    }

    @Test
    @DisplayName("CP-63: un evento que vence entre dos consultas llega como quitado aunque la tarea periodica no haya corrido")
    void cp63ElQueVenceLlegaComoQuitadoSinLaTarea() throws Exception {
        long porVencer = evento("ACCIDENTE", 59, "ACTIVO");
        JsonNode primera = cuerpo(consultar(null));
        assertEquals(List.of(porVencer), ids(primera.get("eventos")));

        reloj.avanzar(Duration.ofSeconds(65)); // vencio hace 5 segundos
        JsonNode segunda = cuerpo(consultar(primera.get("marca").asText()));
        assertEquals(List.of(porVencer), ids(segunda.get("quitados")));
        assertEquals("ACTIVO", jdbc.queryForObject("select estado from evento_vial where id = ?", String.class, porVencer),
                "la tarea periodica todavia no corrio");

        // Cuando la tarea lo marca, no vuelve a llegar como quitado.
        reloj.avanzar(Duration.ofSeconds(30));
        vencimiento.vencer();
        reloj.avanzar(Duration.ofSeconds(5));
        assertEquals(0, cuerpo(consultar(segunda.get("marca").asText())).get("quitados").size());
    }

    @Test
    @DisplayName("CP-64: la respuesta no contiene ningun identificador de conductor")
    void cp64SinIdentificadoresDeConductor() throws Exception {
        evento("ACCIDENTE", 5, "ACTIVO");
        String respuesta = consultar(null).andExpect(status().isOk())
                .andExpect(jsonPath("$.eventos[0].tipo").value("ACCIDENTE"))
                .andExpect(jsonPath("$.eventos[0].ubicacion.latitud").value(-21.5355))
                .andExpect(jsonPath("$.eventos[0].ubicacion.longitud").value(-64.7296))
                .andExpect(jsonPath("$.eventos[0].momentoOcurrencia").isNotEmpty())
                .andExpect(jsonPath("$.eventos[0].origen").value("CONDUCTOR"))
                .andReturn().getResponse().getContentAsString();
        JsonNode evento = json.readTree(respuesta).get("eventos").get(0);
        List<String> campos = new ArrayList<>();
        evento.fieldNames().forEachRemaining(campos::add);
        assertEquals(List.of("id", "tipo", "ubicacion", "momentoOcurrencia", "estado", "confirmado", "origen"), campos);
        assertFalse(respuesta.contains("reportante") || respuesta.contains("conductor") || respuesta.contains("voto"));
    }

    @Test
    @DisplayName("RN-02.6: una marca que no es una fecha responde 400")
    void marcaInvalidaResponde400() throws Exception {
        consultar("ayer").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PETICION_INVALIDA"));
    }

    @Test
    @DisplayName("RNF-01: con 500 eventos vigentes la consulta responde en menos de 1 segundo")
    void quinientosEventosEnMenosDeUnSegundo() throws Exception {
        jdbc.update("""
                insert into evento_vial (tipo_codigo, ubicacion, ocurrio_en, registrado_en, actualizado_en,
                                         reportante_id, origen, estado)
                select 'ACCIDENTE',
                       ST_SetSRID(ST_MakePoint(-64.73 + (n % 25) * 0.001, -21.54 + (n / 25) * 0.001), 4326)::geography,
                       ?, ?, ?, ?, 'CONDUCTOR', 'ACTIVO'
                from generate_series(1, 500) as n
                """, Timestamp.from(INICIO.minusSeconds(60)), Timestamp.from(INICIO.minusSeconds(60)),
                Timestamp.from(INICIO.minusSeconds(60)), reportante);
        consultar(null).andExpect(status().isOk()); // calienta la consulta

        long inicio = System.nanoTime();
        JsonNode respuesta = cuerpo(consultar(null));
        long milisegundos = (System.nanoTime() - inicio) / 1_000_000;

        assertEquals(500, respuesta.get("eventos").size());
        assertTrue(milisegundos < 1000, "la consulta de 500 eventos tardo " + milisegundos + " ms");
        System.out.println("RNF-01: 500 eventos vigentes en " + milisegundos + " ms");
    }
}
