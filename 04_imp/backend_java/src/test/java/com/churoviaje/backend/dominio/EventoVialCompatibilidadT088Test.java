package com.churoviaje.backend.dominio;

import com.churoviaje.backend.servicios.EventoVialServicio;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventoVialCompatibilidadT088Test {
    @Test void conservaContratoYCalculaOcurrenciaSinPersistencia() {
        var ubicacion = new Ubicacion(new BigDecimal("-21.5355"), new BigDecimal("-64.7296"));
        var confirmacion = OffsetDateTime.parse("2026-10-06T15:02:00-04:00");
        var evento = new EventoVialServicio().registrarEvento("ACCIDENTE", ubicacion, confirmacion, 5);
        assertAll(
            () -> assertNull(evento.getId(), "La persistencia corresponde a HU-01 (#53)"),
            () -> assertEquals("ACCIDENTE", evento.getTipo()),
            () -> assertEquals(confirmacion.minusMinutes(5), evento.getFechaOcurrencia()),
            () -> assertEquals(ubicacion, evento.getUbicacion()),
            () -> assertFalse(EventoVial.class.isAnnotationPresent(jakarta.persistence.Entity.class))
        );
    }
}
