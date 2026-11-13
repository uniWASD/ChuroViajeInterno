package com.churoviaje.backend.dominio;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.churoviaje.backend.dominio.catalogo.TipoEvento;
import com.churoviaje.backend.errores.ExcepcionRechazo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** HU-01 (#53): las reglas de creacion de un EventoVial y el limite de reportes del Conductor. */
class EventoVialTest {

    private static final TipoEvento ACCIDENTE = new TipoEvento("ACCIDENTE", "Accidente", "#E53935", 60, true);
    private static final TipoEvento CONGESTION = new TipoEvento("CONGESTION", "Congestión", "#FDD835", 30, false);
    private static final Ubicacion PLAZA = new Ubicacion(new BigDecimal("-21.5355"), new BigDecimal("-64.7296"));
    private static final Instant AHORA = Instant.parse("2026-11-01T12:00:00Z");
    private static final OffsetDateTime CONFIRMACION = OffsetDateTime.ofInstant(AHORA, ZoneOffset.ofHours(-4));

    private static EventoVial reportar(TipoEvento tipo, OffsetDateTime confirmacion, Integer minutos) {
        return EventoVial.reportar(tipo, PLAZA, confirmacion, minutos, 7L, false, AHORA);
    }

    private static HttpStatus rechazo(Runnable accion) {
        return assertThrows(ExcepcionRechazo.class, accion::run).getEstadoHttp();
    }

    @Test
    @DisplayName("RN-01.4 y RN-01.10: ocurrio los minutos elegidos antes de confirmar y nace Activo, de conductor y sin votos")
    void naceActivoConElMomentoRestado() {
        EventoVial evento = reportar(ACCIDENTE, CONFIRMACION, 10);
        assertAll(
                () -> assertEquals(AHORA.minusSeconds(600), evento.getOcurrioEn()),
                () -> assertEquals(AHORA, evento.getRegistradoEn()),
                () -> assertEquals(EstadoEvento.ACTIVO, evento.getEstado()),
                () -> assertEquals(OrigenEvento.CONDUCTOR, evento.getOrigen()),
                () -> assertEquals(0, evento.getVotosReal()),
                () -> assertEquals(0, evento.getVotosFalso()),
                () -> assertEquals(7L, evento.getReportanteId()),
                () -> assertNull(evento.getNivel()),
                () -> assertNull(evento.getId(), "el identificador lo da la base al guardarlo"));
    }

    @Test
    @DisplayName("RN-01.4: sin minutos elegidos, ocurrio hace 5 minutos")
    void sinMinutosValeCinco() {
        assertEquals(AHORA.minusSeconds(300), reportar(ACCIDENTE, CONFIRMACION, null).getOcurrioEn());
    }

    @Test
    @DisplayName("RN-01.4: con el reloj del telefono adelantado, el evento no queda en el futuro")
    void nuncaQuedaEnElFuturo() {
        assertEquals(AHORA, reportar(ACCIDENTE, CONFIRMACION.plusMinutes(8), 0).getOcurrioEn());
        assertEquals(AHORA, reportar(ACCIDENTE, CONFIRMACION.plusMinutes(8), 5).getOcurrioEn());
        assertEquals(AHORA.minusSeconds(120), reportar(ACCIDENTE, CONFIRMACION.plusMinutes(8), 10).getOcurrioEn());
    }

    @Test
    @DisplayName("RN-01.3: la congestion no se puede reportar (422)")
    void laCongestionNoSeReporta() {
        ExcepcionRechazo error = assertThrows(ExcepcionRechazo.class, () -> reportar(CONGESTION, CONFIRMACION, 0));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getEstadoHttp());
        assertEquals("TIPO_NO_REPORTABLE", error.getCodigo());
    }

    @Test
    @DisplayName("RN-01.4: solo valen los tiempos del catalogo (0, 5, 10 y 30 minutos)")
    void soloLosTiemposDelCatalogo() {
        for (int minutos : new int[] {0, 5, 10, 30}) {
            assertDoesNotThrow(() -> reportar(ACCIDENTE, CONFIRMACION, minutos));
        }
        assertEquals(HttpStatus.BAD_REQUEST, rechazo(() -> reportar(ACCIDENTE, CONFIRMACION, 7)));
        assertEquals(HttpStatus.BAD_REQUEST, rechazo(() -> reportar(ACCIDENTE, CONFIRMACION, -5)));
        assertEquals(HttpStatus.BAD_REQUEST, rechazo(() -> reportar(ACCIDENTE, CONFIRMACION, 60)));
    }

    @Test
    @DisplayName("RN-01.7: un evento que ya supero la vigencia de su tipo no se registra (422)")
    void unEventoVencidoNoSeRegistra() {
        // Confirmado hace 50 minutos «hace 10 minutos»: ocurrio hace 60, justo cuando vence.
        ExcepcionRechazo error = assertThrows(ExcepcionRechazo.class,
                () -> reportar(ACCIDENTE, CONFIRMACION.minusMinutes(50), 10));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getEstadoHttp());
        assertEquals("EVENTO_VENCIDO", error.getCodigo());
        // Un minuto antes todavia vale.
        assertDoesNotThrow(() -> reportar(ACCIDENTE, CONFIRMACION.minusMinutes(49), 10));
    }

    @Test
    @DisplayName("RN-02.3: vence a los 60 minutos de ocurrido y deja de estar vigente desde ese instante")
    void venceSegunSuTipo() {
        EventoVial evento = reportar(ACCIDENTE, CONFIRMACION, 0);
        assertEquals(AHORA.plusSeconds(3600), evento.venceEn());
        assertTrue(evento.estaVigente(AHORA.plusSeconds(3599)));
        assertFalse(evento.estaVigente(AHORA.plusSeconds(3600)));
    }

    @Test
    @DisplayName("RN-01.9: el quinto reporte en 10 minutos pasa y el sexto se rechaza con 429")
    void limiteDeReportesDelConductor() {
        assertDoesNotThrow(() -> Conductor.verificarLimiteDeReportes(4));
        ExcepcionRechazo error = assertThrows(ExcepcionRechazo.class, () -> Conductor.verificarLimiteDeReportes(5));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getEstadoHttp());
        assertEquals("LIMITE_DE_REPORTES", error.getCodigo());
    }

    // ------------------------------------------------------------------ votos (HU-11, #63)

    private static EventoVial activo() {
        return reportar(ACCIDENTE, CONFIRMACION, 0); // reportado por el conductor 7
    }

    private static EventoVial votar(EventoVial evento, ValorVoto valor, long... votantes) {
        EventoVial resultado = evento;
        for (long votante : votantes) {
            resultado = resultado.registrarVoto(valor, votante, AHORA);
        }
        return resultado;
    }

    @Test
    @DisplayName("RN-02.13: con 3 votos «falso» un evento Activo pasa a Retirado")
    void tresFalsosLoRetiran() {
        assertEquals(EstadoEvento.ACTIVO, votar(activo(), ValorVoto.FALSO, 1, 2).getEstado());
        EventoVial retirado = votar(activo(), ValorVoto.FALSO, 1, 2, 3);
        assertEquals(EstadoEvento.RETIRADO, retirado.getEstado());
        assertEquals(3, retirado.getVotosFalso());
    }

    @Test
    @DisplayName("RN-02.14: con 3 votos «real» pasa a Confirmado y despues ya no se retira por votos")
    void tresRealesLoConfirman() {
        EventoVial confirmado = votar(activo(), ValorVoto.REAL, 1, 2, 3);
        assertEquals(EstadoEvento.CONFIRMADO, confirmado.getEstado());
        EventoVial conFalsos = votar(confirmado, ValorVoto.FALSO, 4, 5, 6, 8);
        assertEquals(EstadoEvento.CONFIRMADO, conFalsos.getEstado());
        assertEquals(4, conFalsos.getVotosFalso());
    }

    @Test
    @DisplayName("RN-02.12: el reportante no puede votar su propio evento (422)")
    void elReportanteNoVota() {
        ExcepcionRechazo error = assertThrows(ExcepcionRechazo.class,
                () -> activo().registrarVoto(ValorVoto.REAL, 7, AHORA));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getEstadoHttp());
        assertEquals("VOTO_PROPIO", error.getCodigo());
    }

    @Test
    @DisplayName("RN-02.10: no se vota un evento retirado ni uno que ya vencio (422)")
    void soloSeVotanLosVigentes() {
        EventoVial retirado = votar(activo(), ValorVoto.FALSO, 1, 2, 3);
        assertEquals("EVENTO_NO_VIGENTE",
                assertThrows(ExcepcionRechazo.class, () -> retirado.registrarVoto(ValorVoto.REAL, 4, AHORA)).getCodigo());
        assertEquals("EVENTO_NO_VIGENTE", assertThrows(ExcepcionRechazo.class,
                () -> activo().registrarVoto(ValorVoto.REAL, 4, AHORA.plusSeconds(3600))).getCodigo());
    }

    @Test
    @DisplayName("D-20: la congestion que detecta el sistema no se vota (422)")
    void laCongestionDeTelemetriaNoSeVota() {
        EventoVial congestion = EventoVial.desdeRegistro(9, CONGESTION, PLAZA, AHORA, AHORA, null,
                OrigenEvento.TELEMETRIA, "CONGESTIONADO", EstadoEvento.ACTIVO, 0, 0, false);
        assertEquals("VOTO_NO_PERMITIDO", assertThrows(ExcepcionRechazo.class,
                () -> congestion.registrarVoto(ValorVoto.FALSO, 4, AHORA)).getCodigo());
    }

    @Test
    @DisplayName("El voto se lee sin importar mayusculas; otro valor responde 400")
    void valoresDeVoto() {
        assertEquals(ValorVoto.REAL, ValorVoto.desdeTexto("real"));
        assertEquals(ValorVoto.FALSO, ValorVoto.desdeTexto(" FALSO "));
        assertEquals(HttpStatus.BAD_REQUEST, rechazo(() -> ValorVoto.desdeTexto("quizas")));
    }
}
