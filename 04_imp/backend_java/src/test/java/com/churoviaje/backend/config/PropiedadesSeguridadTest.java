package com.churoviaje.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class PropiedadesSeguridadTest {

    private static final String CLAVE = "x".repeat(32);
    private static final String CREDENCIAL = "y".repeat(32);

    private static PropiedadesSeguridad con(String secreto, String clientId, String credencial) {
        return new PropiedadesSeguridad(
                new PropiedadesSeguridad.Jwt(secreto, Duration.ofHours(1)),
                new PropiedadesSeguridad.Renovacion(Duration.ofDays(30)),
                new PropiedadesSeguridad.Google(clientId),
                credencial);
    }

    @Test
    void aceptaUnaConfiguracionCompleta() {
        var propiedades = con(CLAVE, "cliente.apps.googleusercontent.com", CREDENCIAL);
        assertEquals(Duration.ofHours(1), propiedades.jwt().duracion());
        assertEquals(Duration.ofDays(30), propiedades.renovacion().duracion());
    }

    @Test
    void sinClaveDelJwtOConUnaCortaNoArranca() {
        assertThrows(IllegalStateException.class, () -> con(null, "id", CREDENCIAL));
        assertThrows(IllegalStateException.class, () -> con("", "id", CREDENCIAL));
        assertThrows(IllegalStateException.class, () -> con("x".repeat(31), "id", CREDENCIAL));
    }

    @Test
    void sinClientIdDeGoogleNoArranca() {
        assertThrows(IllegalStateException.class, () -> con(CLAVE, null, CREDENCIAL));
        assertThrows(IllegalStateException.class, () -> con(CLAVE, "  ", CREDENCIAL));
    }

    @Test
    void sinCredencialDeServicioOConUnaCortaNoArranca() {
        assertThrows(IllegalStateException.class, () -> con(CLAVE, "id", null));
        assertThrows(IllegalStateException.class, () -> con(CLAVE, "id", "corta"));
    }

    @Test
    void elMensajeDeErrorNoRepiteElSecreto() {
        var error = assertThrows(IllegalStateException.class, () -> con("secreto-corto", "id", CREDENCIAL));
        assertTrue(!error.getMessage().contains("secreto-corto"));
    }
}
