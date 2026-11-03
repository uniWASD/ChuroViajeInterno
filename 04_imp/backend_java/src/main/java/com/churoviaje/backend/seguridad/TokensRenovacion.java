package com.churoviaje.backend.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/** Genera el token de renovacion (aleatorio, 256 bits) y calcula la huella que se guarda en la base. */
@Component
public class TokensRenovacion {

    private static final int BYTES = 32;

    private final SecureRandom azar = new SecureRandom();

    public String generar() {
        byte[] bytes = new byte[BYTES];
        azar.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 en hexadecimal minuscula (64 caracteres). Es lo unico que se guarda del token. */
    public String huella(String token) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no esta disponible en esta JVM", e);
        }
    }
}
