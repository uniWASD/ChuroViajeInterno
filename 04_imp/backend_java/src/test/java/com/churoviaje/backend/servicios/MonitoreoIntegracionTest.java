package com.churoviaje.backend.servicios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.churoviaje.backend.config.ConfiguracionRelojDePrueba;
import com.churoviaje.backend.config.PruebaIntegracionPostgis;
import com.churoviaje.backend.config.RelojDePrueba;
import com.churoviaje.backend.seguridad.JwtServicio;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** HU-08 (#57): {@code /monitoreo/*} con la base real y el catálogo de surtidores de V2. */
@Import(ConfiguracionRelojDePrueba.class)
class MonitoreoIntegracionTest extends PruebaIntegracionPostgis {

    private static final String CREDENCIAL = "Bearer credencial-de-servicio-solo-para-pruebas-0123456789";
    /** 15:20 en Tarija. */
    private static final Instant AHORA = Instant.parse("2026-11-14T19:20:00Z");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtServicio jwtServicio;
    @Autowired RelojDePrueba reloj;

    @BeforeEach
    void preparar() {
        jdbc.update("delete from estado_combustible");
        reloj.fijar(AHORA);
    }

    private ResultActions enviar(String autorizacion, String avisos) throws Exception {
        return mvc.perform(post("/monitoreo/resultados").header("Authorization", autorizacion)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lote\":\"2026-11-14T15:20:00-04:00\",\"avisos\":[" + avisos + "]}"));
    }

    private static String aviso(String surtidor, String combustible, String estado, String fila, String hora) {
        return "{\"surtidor\":\"" + surtidor + "\",\"combustible\":\"" + combustible + "\",\"estado\":\"" + estado + "\""
                + (fila == null ? "" : ",\"fila\":\"" + fila + "\"") + ",\"fecha\":\"2026-11-14T" + hora + ":00-04:00\"}";
    }

    private List<Map<String, Object>> estados() {
        return jdbc.queryForList("""
                select s.nombre, e.tipo_codigo, e.origen, e.disponible, e.fila, e.fecha_dato, e.litros, e.consultado_en
                from estado_combustible e join surtidor s on s.id = e.surtidor_id order by s.nombre, e.tipo_codigo""");
    }

    private static Instant instante(Object valor) {
        return ((java.sql.Timestamp) valor).toInstant();
    }

    @Test
    @DisplayName("CP-68: un aviso válido de «Las Vegas» y otro de un surtidor que no existe: solo se guarda el primero")
    void cp68LoteMezclado() throws Exception {
        enviar(CREDENCIAL, aviso("Las Vegas", "Gasolina", "DISPONIBLE", "CORTA", "15:02") + ","
                + aviso("Surtidor Fantasma", "Diesel", "AGOTADO", null, "15:03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aceptados").value(1))
                .andExpect(jsonPath("$.descartados.length()").value(1))
                .andExpect(jsonPath("$.descartados[0].indice").value(1))
                .andExpect(jsonPath("$.descartados[0].motivo").value("SURTIDOR_DESCONOCIDO"));

        List<Map<String, Object>> filas = estados();
        assertThat(filas).hasSize(1);
        Map<String, Object> e = filas.get(0);
        assertThat(e.get("nombre")).isEqualTo("Las Vegas");
        assertThat(e.get("tipo_codigo")).isEqualTo("GASOLINA");
        assertThat(e.get("origen")).isEqualTo("WHATSAPP");
        assertThat(e.get("disponible")).isEqualTo(true);
        assertThat(e.get("fila")).isEqualTo("CORTA");
        // RN-06.16: la fecha del mensaje, no la del análisis.
        assertThat(instante(e.get("fecha_dato"))).isEqualTo(Instant.parse("2026-11-14T19:02:00Z"));
        assertThat(e.get("litros")).isNull();
        assertThat(e.get("consultado_en")).isNull();
    }

    @Test
    @DisplayName("CP-69: un aviso de las 15:00 no reemplaza el dato de WhatsApp de las 15:10, y cuenta como aceptado")
    void cp69NoReemplazaUnDatoMasReciente() throws Exception {
        enviar(CREDENCIAL, aviso("Las Vegas", "Gasolina", "AGOTADO", null, "15:10")).andExpect(status().isOk());
        enviar(CREDENCIAL, aviso("Las Vegas", "Gasolina", "DISPONIBLE", "LARGA", "15:00"))
                .andExpect(jsonPath("$.aceptados").value(1))
                .andExpect(jsonPath("$.descartados.length()").value(0));

        Map<String, Object> e = estados().get(0);
        assertThat(e.get("disponible")).isEqualTo(false);
        assertThat(e.get("fila")).isNull();
        assertThat(instante(e.get("fecha_dato"))).isEqualTo(Instant.parse("2026-11-14T19:10:00Z"));

        // Uno más nuevo sí lo reemplaza.
        enviar(CREDENCIAL, aviso("Las Vegas", "Gasolina", "DISPONIBLE", "MEDIA", "15:15")).andExpect(status().isOk());
        assertThat(estados().get(0).get("disponible")).isEqualTo(true);
        assertThat(estados()).hasSize(1);
    }

    @Test
    @DisplayName("RN-06.17: un dato de Conductores o de ANH más nuevo no impide guardar el de WhatsApp")
    void otrosOrigenesNoImpiden() throws Exception {
        jdbc.update("""
                insert into estado_combustible (surtidor_id, tipo_codigo, origen, disponible, fecha_dato, actualizado_en)
                select id, 'GASOLINA', 'CONDUCTORES', true, ?, ? from surtidor where nombre = 'Las Vegas'""",
                java.sql.Timestamp.from(AHORA), java.sql.Timestamp.from(AHORA));
        jdbc.update("""
                insert into estado_combustible (surtidor_id, tipo_codigo, origen, disponible, fecha_dato, litros,
                                                estado_anh, consultado_en, actualizado_en)
                select id, 'GASOLINA', 'ANH', true, ?, 1200, 'ALTO', ?, ? from surtidor where nombre = 'Las Vegas'""",
                java.sql.Timestamp.from(AHORA), java.sql.Timestamp.from(AHORA), java.sql.Timestamp.from(AHORA));

        enviar(CREDENCIAL, aviso("Las Vegas", "Gasolina", "AGOTADO", null, "15:00")).andExpect(status().isOk());

        assertThat(estados()).extracting(f -> f.get("origen")).containsExactlyInAnyOrder("CONDUCTORES", "ANH", "WHATSAPP");
    }

    @Test
    @DisplayName("RN-06.17: si el lote repite surtidor y tipo vale el más reciente; por alias y sin tildes también se reconoce")
    void loteRepetidoYAlias() throws Exception {
        enviar(CREDENCIAL, aviso("Las Vegas", "Diesel", "DISPONIBLE", null, "15:12") + ","
                + aviso("las  VEGAS", "diésel", "AGOTADO", "sin fila", "15:14") + ","
                + aviso("Vegas", "DIESEL", "DISPONIBLE", null, "15:05") + ","
                + aviso("Portillo", "Desconocido", "AGOTADO", "MUCHA", "15:03"))
                .andExpect(jsonPath("$.aceptados").value(4))
                .andExpect(jsonPath("$.descartados.length()").value(0));

        List<Map<String, Object>> filas = estados();
        assertThat(filas).hasSize(2);
        Map<String, Object> portillo = filas.get(0);
        assertThat(portillo.get("nombre")).isEqualTo("El Portillo");
        assertThat(portillo.get("tipo_codigo")).as("RN-06.8").isEqualTo("DESCONOCIDO");
        assertThat(portillo.get("fila")).as("una fila desconocida se guarda sin fila").isNull();
        Map<String, Object> vegas = filas.get(1);
        assertThat(vegas.get("disponible")).isEqualTo(false);
        assertThat(vegas.get("fila")).isEqualTo("SIN_FILA");
        assertThat(instante(vegas.get("fecha_dato"))).isEqualTo(Instant.parse("2026-11-14T19:14:00Z"));
    }

    @Test
    @DisplayName("RN-06.15: cada aviso inválido se descarta con su posición y su motivo, y los demás siguen")
    void motivosDeDescarte() throws Exception {
        enviar(CREDENCIAL, String.join(",",
                aviso("Las Vegas", "Kerosene", "DISPONIBLE", null, "15:00"),
                aviso("Las Vegas", "Gasolina", "QUIZAS", null, "15:00"),
                "{\"surtidor\":\"Las Vegas\",\"combustible\":\"Gasolina\",\"estado\":\"AGOTADO\",\"fecha\":\"ayer\"}",
                "{\"surtidor\":\"Las Vegas\",\"combustible\":\"Gasolina\",\"estado\":\"AGOTADO\",\"fecha\":\"2026-11-14T15:00:00\"}",
                aviso("Las Vegas", "Gasolina", "AGOTADO", null, "16:00"),
                "{\"surtidor\":\"Las Vegas\",\"estado\":\"AGOTADO\",\"fecha\":\"2026-11-14T15:00:00-04:00\"}",
                "\"texto suelto\"",
                "{\"surtidor\":7,\"combustible\":\"Gasolina\",\"estado\":\"AGOTADO\",\"fecha\":\"2026-11-14T15:00:00-04:00\"}",
                aviso("Don Daniel", "Gasolina Premium", "disponible", null, "15:01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aceptados").value(1))
                .andExpect(jsonPath("$.descartados[*].indice").value(org.hamcrest.Matchers.contains(0, 1, 2, 3, 4, 5, 6, 7)))
                .andExpect(jsonPath("$.descartados[*].motivo").value(org.hamcrest.Matchers.contains(
                        "COMBUSTIBLE_INVALIDO", "ESTADO_INVALIDO", "FECHA_INVALIDA", "FECHA_INVALIDA",
                        "FECHA_INVALIDA", "FORMATO_INVALIDO", "FORMATO_INVALIDO", "FORMATO_INVALIDO")));
        assertThat(estados()).singleElement().satisfies(e -> assertThat(e.get("tipo_codigo")).isEqualTo("GASOLINA_PREMIUM"));
    }

    @Test
    @DisplayName("Un lote de más de 1000 avisos, o sin lista de avisos, responde 400 y no guarda nada")
    void loteInvalido() throws Exception {
        String uno = aviso("Las Vegas", "Gasolina", "DISPONIBLE", null, "15:00");
        enviar(CREDENCIAL, String.join(",", java.util.Collections.nCopies(1001, uno)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PETICION_INVALIDA"));
        enviar(CREDENCIAL, String.join(",", java.util.Collections.nCopies(1000, uno))).andExpect(status().isOk())
                .andExpect(jsonPath("$.aceptados").value(1000));
        jdbc.update("delete from estado_combustible");
        mvc.perform(post("/monitoreo/resultados").header("Authorization", CREDENCIAL)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lote\":\"x\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/monitoreo/resultados").header("Authorization", CREDENCIAL)
                        .contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest());
        assertThat(estados()).isEmpty();
    }

    @Test
    @DisplayName("CP-70: con el JWT de un conductor las dos rutas responden 403; sin credencial, 401")
    void cp70ConductorRecibe403() throws Exception {
        long conductor = jdbc.queryForObject(
                "insert into conductor (google_id, fecha_alta) values ('google-monitoreo', now()) returning id", Long.class);
        String jwt = "Bearer " + jwtServicio.emitir(conductor, reloj.instant());
        enviar(jwt, aviso("Las Vegas", "Gasolina", "DISPONIBLE", null, "15:00"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        mvc.perform(get("/monitoreo/surtidores").header("Authorization", jwt)).andExpect(status().isForbidden());
        mvc.perform(post("/monitoreo/resultados").contentType(MediaType.APPLICATION_JSON).content("{\"avisos\":[]}"))
                .andExpect(status().isUnauthorized());
        assertThat(estados()).isEmpty();
        jdbc.update("delete from sesion");
        jdbc.update("delete from conductor where id = ?", conductor);
    }

    @Test
    @DisplayName("GET /monitoreo/surtidores trae los surtidores con sus alias, los cuatro combustibles y las filas")
    void catalogo() throws Exception {
        mvc.perform(get("/monitoreo/surtidores").header("Authorization", CREDENCIAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.surtidores[?(@.nombre == 'Las Vegas')].alias[*]").value(
                        org.hamcrest.Matchers.containsInAnyOrder("la vegas", "las vegas", "mas vegas", "vegas")))
                .andExpect(jsonPath("$.combustibles").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "Gasolina", "Diesel", "Gasolina Premium", "Diesel ULS")))
                .andExpect(jsonPath("$.filas").value(org.hamcrest.Matchers.contains("SIN_FILA", "CORTA", "MEDIA", "LARGA")));
        long surtidores = jdbc.queryForObject("select count(*) from surtidor", Long.class);
        mvc.perform(get("/monitoreo/surtidores").header("Authorization", CREDENCIAL))
                .andExpect(jsonPath("$.surtidores.length()").value((int) surtidores));
    }
}
