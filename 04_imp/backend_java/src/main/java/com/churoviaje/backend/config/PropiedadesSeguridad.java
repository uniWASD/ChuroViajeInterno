package com.churoviaje.backend.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuracion de seguridad (CU-08, D-13). Los tres secretos vienen de variables de entorno y
 * no tienen valor por defecto: si falta alguno, el backend no arranca. Nunca van al repositorio.
 *
 * @param jwt                 clave que firma el JWT y su duracion (RN-08.12: 1 hora)
 * @param renovacion          cuanto vale el token de renovacion (RN-08.12: 30 dias)
 * @param google              Client ID de la app, contra el que se valida el token de Google
 * @param credencialServicio  credencial del servicio de WhatsApp/LLM (D-13)
 */
@ConfigurationProperties(prefix = "churoviaje.seguridad")
public record PropiedadesSeguridad(
        @DefaultValue Jwt jwt,
        @DefaultValue Renovacion renovacion,
        @DefaultValue Google google,
        String credencialServicio) {

    /** HS256 exige una clave de al menos 256 bits. */
    static final int MINIMO_BYTES_CLAVE_JWT = 32;
    static final int MINIMO_CARACTERES_CREDENCIAL = 32;

    public record Jwt(String secreto, @DefaultValue("1h") Duration duracion) {}

    public record Renovacion(@DefaultValue("30d") Duration duracion) {}

    public record Google(String clientId) {}

    public PropiedadesSeguridad {
        if (jwt.secreto() == null || jwt.secreto().getBytes(StandardCharsets.UTF_8).length < MINIMO_BYTES_CLAVE_JWT) {
            throw new IllegalStateException("JWT_SECRET falta o tiene menos de " + MINIMO_BYTES_CLAVE_JWT
                    + " caracteres: la clave que firma el JWT debe ser larga y aleatoria");
        }
        if (google.clientId() == null || google.clientId().isBlank()) {
            throw new IllegalStateException("GOOGLE_CLIENT_ID falta: es el Client ID de la app en Google");
        }
        if (credencialServicio == null || credencialServicio.length() < MINIMO_CARACTERES_CREDENCIAL) {
            throw new IllegalStateException("SERVICE_CREDENTIAL falta o tiene menos de "
                    + MINIMO_CARACTERES_CREDENCIAL + " caracteres: es la credencial del servicio de WhatsApp/LLM");
        }
        if (jwt.duracion().isZero() || jwt.duracion().isNegative()
                || renovacion.duracion().isZero() || renovacion.duracion().isNegative()) {
            throw new IllegalStateException("Las duraciones del JWT y de la renovacion deben ser positivas");
        }
    }
}
