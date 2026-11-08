package com.churoviaje.backend.motor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.churoviaje.backend.config.ConfiguracionRelojDePrueba;
import com.churoviaje.backend.config.PruebaIntegracionPostgis;
import com.churoviaje.backend.config.RelojDePrueba;
import com.churoviaje.backend.seguridad.JwtServicio;
import com.churoviaje.backend.servicios.VencimientoEventos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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

/**
 * T-099 (#67): los cambios de los eventos le llegan al motor. El motor de este contexto es un doble que registra
 * los mensajes; CP-100 usa ademas un wrapper real con el nucleo falso.
 */
@Import(ConfiguracionRelojDePrueba.class)
@TestPropertySource(properties = {"motor.habilitado=true", "motor.ejecutable=no-se-usa", "motor.red-vial=no-se-usa"})
class TraficoMotorIntegracionTest extends PruebaIntegracionPostgis {

    private static final Instant INICIO = Instant.parse("2026-11-01T12:00:00Z");
    private static final Path NUCLEO_FALSO = Path.of("src/test/resources/nucleo_falso/NucleoFalso.java");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtServicio jwtServicio;
    @Autowired RelojDePrueba reloj;
    @Autowired VencimientoEventos vencimiento;
    @Autowired TraficoDelMotor trafico;
    @Autowired EventosVigentesDelMotor eventosVigentes;
    @MockitoBean MotorDeRutas motor;

    private long reportante;

    @BeforeEach
    void preparar() throws Exception {
        jdbc.update("delete from voto");
        jdbc.update("delete from evento_vial");
        jdbc.update("delete from sesion");
        jdbc.update("delete from conductor");
        reloj.fijar(INICIO);
        reportante = crearConductor("google-reportante");
        reset(motor);
        when(motor.actualizarTrafico(any())).thenReturn(json.readTree("{\"ok\":true,\"tramos\":[]}"));
        trafico.enviarPendientes(); // lo que haya quedado de otra prueba
        clearInvocations(motor);
    }

    private long crearConductor(String googleId) {
        return jdbc.queryForObject(
                "insert into conductor (google_id, fecha_alta) values (?, now()) returning id", Long.class, googleId);
    }

    private long reportarBloqueo() throws Exception {
        String cuerpo = "{\"tipo\":\"BLOQUEO\",\"ubicacion\":{\"latitud\":-21.5295,\"longitud\":-64.724},"
                + "\"horaConfirmacion\":\"" + OffsetDateTime.ofInstant(reloj.instant(), ZoneOffset.ofHours(-4))
                + "\",\"minutosElegidos\":0}";
        String respuesta = mvc.perform(post("/eventos")
                        .header("Authorization", "Bearer " + jwtServicio.emitir(reportante, reloj.instant()))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("id").asLong();
    }

    private List<String> mensajesAlMotor() {
        ArgumentCaptor<ObjectNode> mensajes = ArgumentCaptor.forClass(ObjectNode.class);
        verify(motor, atLeastOnce()).actualizarTrafico(mensajes.capture());
        List<String> textos = new ArrayList<>();
        mensajes.getAllValues().forEach(m -> textos.add(m.toString()));
        return textos;
    }

    @Test
    @DisplayName("Al registrarse un evento, el motor recibe «nace» con su identificador, tipo, ubicacion y vencimiento")
    void alNacerElMotorRecibeElEvento() throws Exception {
        long id = reportarBloqueo();
        verify(motor, timeout(2000)).actualizarTrafico(any());
        assertThat(mensajesAlMotor()).containsExactly("{\"evento\":\"" + id + "\",\"accion\":\"nace\",\"tipo\":\"BLOQUEO\","
                + "\"ubicacion\":[-21.5295,-64.724],\"vence\":\"2026-11-01T09:00:00-04:00\"}");
    }

    @Test
    @DisplayName("RN-02.15: al retirarse por votos, el motor recibe «retira»; al vencer, «vence»")
    void alRetirarseOVencerElMotorRecibeElAviso() throws Exception {
        long bloqueo = reportarBloqueo();
        verify(motor, timeout(2000)).actualizarTrafico(any());
        clearInvocations(motor);

        for (int i = 1; i <= 3; i++) {
            mvc.perform(post("/eventos/" + bloqueo + "/votos")
                            .header("Authorization", "Bearer " + jwtServicio.emitir(crearConductor("v" + i), reloj.instant()))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"valor\":\"falso\"}"))
                    .andExpect(status().isCreated());
        }
        verify(motor, timeout(2000)).actualizarTrafico(any());
        assertThat(mensajesAlMotor()).containsExactly("{\"evento\":\"" + bloqueo + "\",\"accion\":\"retira\"}");

        clearInvocations(motor);
        long otro = jdbc.queryForObject("""
                insert into evento_vial (tipo_codigo, ubicacion, ocurrio_en, registrado_en, actualizado_en,
                                         reportante_id, origen, estado)
                values ('ACCIDENTE', ST_SetSRID(ST_MakePoint(-64.7296, -21.5355), 4326)::geography, ?, ?, ?, ?,
                        'CONDUCTOR', 'ACTIVO')
                returning id
                """, Long.class, Timestamp.from(INICIO.minusSeconds(3660)), Timestamp.from(INICIO.minusSeconds(3660)),
                Timestamp.from(INICIO.minusSeconds(3660)), reportante);
        vencimiento.vencer();
        verify(motor, timeout(2000)).actualizarTrafico(any());
        assertThat(mensajesAlMotor()).containsExactly("{\"evento\":\"" + otro + "\",\"accion\":\"vence\"}");
    }

    @Test
    @DisplayName("Si el aviso al motor falla, el evento igual queda registrado y el aviso se reintenta hasta que llega")
    void elAvisoFallidoNoSePierde() throws Exception {
        when(motor.actualizarTrafico(any())).thenThrow(new MotorNoDisponibleException());
        long id = reportarBloqueo();
        verify(motor, timeout(2000)).actualizarTrafico(any());
        assertThat(jdbc.queryForObject("select count(*) from evento_vial where id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(trafico.cantidadPendiente()).isEqualTo(1);

        reset(motor);
        when(motor.actualizarTrafico(any())).thenReturn(json.readTree("{\"ok\":true,\"tramos\":[]}"));
        trafico.enviarPendientes();
        assertThat(trafico.cantidadPendiente()).isZero();
        assertThat(mensajesAlMotor()).hasSize(1).first().asString().contains("\"evento\":\"" + id + "\"", "\"nace\"");
    }

    @Test
    @DisplayName("D-15: los eventos vigentes para el arranque del nucleo son los Activos y Confirmados que no vencieron")
    void eventosVigentesParaElArranque() throws Exception {
        long vigente = reportarBloqueo();
        jdbc.update("""
                insert into evento_vial (tipo_codigo, ubicacion, ocurrio_en, registrado_en, actualizado_en,
                                         reportante_id, origen, estado)
                values ('OTRO', ST_SetSRID(ST_MakePoint(-64.7296, -21.5355), 4326)::geography, ?, ?, ?, ?,
                        'CONDUCTOR', 'RETIRADO')
                """, Timestamp.from(INICIO), Timestamp.from(INICIO), Timestamp.from(INICIO), reportante);

        List<ObjectNode> mensajes = eventosVigentes.eventosVigentes();
        assertThat(mensajes).hasSize(1);
        assertThat(mensajes.get(0).get("evento").asText()).isEqualTo(String.valueOf(vigente));
        assertThat(mensajes.get(0).get("accion").asText()).isEqualTo("nace");
    }

    @Test
    @DisplayName("CP-100: se reinicia el nucleo con 3 eventos vigentes y, antes de la primera ruta, ya recibio los 3")
    void cp100AlReiniciarElNucleoRecibeLosVigentes() throws Exception {
        for (int i = 0; i < 3; i++) {
            jdbc.update("""
                    insert into evento_vial (tipo_codigo, ubicacion, ocurrio_en, registrado_en, actualizado_en,
                                             reportante_id, origen, estado)
                    values ('ACCIDENTE', ST_SetSRID(ST_MakePoint(?, -21.5355), 4326)::geography, ?, ?, ?, ?,
                            'CONDUCTOR', 'ACTIVO')
                    """, -64.7296 + i * 0.002, Timestamp.from(INICIO), Timestamp.from(INICIO), Timestamp.from(INICIO),
                    reportante);
        }
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        LanzadorNucleo nucleoFalso = () -> new ProcessBuilder(java, NUCLEO_FALSO.toString()).start();
        PropiedadesMotor propiedades = new PropiedadesMotor(true, "nucleo", "red.json", Duration.ofSeconds(3),
                Duration.ofSeconds(30), Duration.ofMillis(200));
        MotorDeRutas real = new MotorDeRutas(propiedades, nucleoFalso, eventosVigentes, json);
        try {
            real.iniciar();
            esperarListo(real);
            assertThat(real.calcularRuta(json.createObjectNode()).path("eventos_recibidos").asInt()).isEqualTo(3);

            // El nucleo muere y el wrapper lo vuelve a arrancar solo.
            ObjectNode morir = json.createObjectNode().put("morir", true);
            try {
                real.calcularRuta(morir);
            } catch (MotorNoDisponibleException esperado) {
                // la peticion que lo mato no tiene respuesta
            }
            long limite = System.currentTimeMillis() + 30_000;
            while (real.vecesArrancado() < 2 && System.currentTimeMillis() < limite) {
                Thread.sleep(50);
            }
            esperarListo(real);
            JsonNode primeraRuta = real.calcularRuta(json.createObjectNode());
            assertThat(real.vecesArrancado()).isEqualTo(2);
            assertThat(primeraRuta.path("eventos_recibidos").asInt())
                    .as("el nucleo nuevo recibio los 3 vigentes antes de la primera ruta").isEqualTo(3);
        } finally {
            real.detener();
        }
    }

    private static void esperarListo(MotorDeRutas motor) throws InterruptedException {
        long limite = System.currentTimeMillis() + 30_000;
        while (!motor.estaListo() && System.currentTimeMillis() < limite) {
            Thread.sleep(50);
        }
        assertThat(motor.estaListo()).isTrue();
    }
}
