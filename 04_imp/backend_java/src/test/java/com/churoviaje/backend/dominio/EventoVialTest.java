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
}
