package com.churoviaje.backend.seguridad;

import com.churoviaje.backend.config.PropiedadesSeguridad;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

/** Emite y valida el JWT propio del sistema (RN-08.5): firmado con HS256, de 1 hora. */
@Component
public class JwtServicio {

    static final String EMISOR = "churoviaje";
    /** Claim del JWT de un usuario fantasma: el identificador de su prueba de carga (CU-07, RN-07.11). */
    static final String CLAIM_PRUEBA = "prueba";

    private final SecretKey clave;
    private final PropiedadesSeguridad propiedades;
    private final Clock reloj;

    public JwtServicio(PropiedadesSeguridad propiedades, Clock reloj) {
        this.propiedades = propiedades;
        this.reloj = reloj;
        this.clave = Keys.hmacShaKeyFor(propiedades.jwt().secreto().getBytes(StandardCharsets.UTF_8));
    }

    /** Cuando vence un JWT emitido en {@code ahora}. */
    public Instant vencimientoDesde(Instant ahora) {
        return ahora.plus(propiedades.jwt().duracion());
    }

    public String emitir(long conductorId, Instant ahora) {
        return emitir(conductorId, null, ahora);
    }

    /** RN-07.4: el JWT de un usuario fantasma lleva el identificador de su prueba de carga. */
    public String emitirDePrueba(long conductorId, String pruebaCarga, Instant ahora) {
        return emitir(conductorId, java.util.Objects.requireNonNull(pruebaCarga, "pruebaCarga"), ahora);
    }

    private String emitir(long conductorId, String pruebaCarga, Instant ahora) {
        var constructor = Jwts.builder()
                .issuer(EMISOR)
                .subject(Long.toString(conductorId))
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(vencimientoDesde(ahora)));
        if (pruebaCarga != null) {
            constructor.claim(CLAIM_PRUEBA, pruebaCarga);
        }
        return constructor.signWith(clave, Jwts.SIG.HS256).compact();
    }

    /**
     * Devuelve el conductor del JWT si la firma es buena y no ha vencido; en cualquier otro caso
     * (mal formado, otra firma, otro emisor, vencido) devuelve vacio, y el filtro responde 401 (RN-08.11).
     */
    public Optional<Long> conductorDe(String jwt) {
        return autenticar(jwt).map(ConductorAutenticado::id);
    }

    /** Como {@link #conductorDe}, pero dice ademas si es un usuario fantasma y de que prueba de carga. */
    public Optional<ConductorAutenticado> autenticar(String jwt) {
        try {
            var cuerpo = Jwts.parser()
                    .verifyWith(clave)
                    .requireIssuer(EMISOR)
                    .clock(() -> Date.from(reloj.instant()))
                    .build()
                    .parseSignedClaims(jwt)
                    .getPayload();
            Object prueba = cuerpo.get(CLAIM_PRUEBA);
            return Optional.of(new ConductorAutenticado(Long.parseLong(cuerpo.getSubject()),
                    prueba instanceof String texto ? texto : null));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
