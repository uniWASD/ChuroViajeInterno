package com.churoviaje.backend.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SesionTest {

    private static final Instant AHORA = Instant.parse("2026-11-01T12:00:00Z");
    private static final String HUELLA = "a".repeat(64);

    private final Conductor conductor = Conductor.deGoogle("google-123", AHORA);

    @Test
    void laSesionVale30DiasDesdeQueSeAbre() {
        Sesion sesion = Sesion.iniciar(conductor, HUELLA, AHORA, Duration.ofDays(30));
        assertEquals(AHORA.plus(Duration.ofDays(30)), sesion.getExpiraEn());
        assertTrue(sesion.estaVigente(AHORA.plus(Duration.ofDays(30)).minusSeconds(1)));
        assertFalse(sesion.estaVigente(AHORA.plus(Duration.ofDays(30))));
        assertFalse(sesion.estaVigente(AHORA.plus(Duration.ofDays(31))));
    }

    @Test
    void laDuracionDebeSerPositiva() {
        assertThrows(IllegalArgumentException.class, () -> Sesion.iniciar(conductor, HUELLA, AHORA, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> Sesion.iniciar(conductor, HUELLA, AHORA, Duration.ofDays(-1)));
    }

    @Test
    void elConductorGuardaSoloElIdentificadorDeGoogle() {
        assertEquals("google-123", conductor.getGoogleId());
        assertFalse(conductor.isSimulado());
        assertThrows(IllegalArgumentException.class, () -> Conductor.deGoogle("  ", AHORA));
    }
}
