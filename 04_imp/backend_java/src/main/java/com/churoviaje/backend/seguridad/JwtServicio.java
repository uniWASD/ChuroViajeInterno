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
        return Jwts.builder()
                .issuer(EMISOR)
                .subject(Long.toString(conductorId))
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(vencimientoDesde(ahora)))
                .signWith(clave, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Devuelve el conductor del JWT si la firma es buena y no ha vencido; en cualquier otro caso
     * (mal formado, otra firma, otro emisor, vencido) devuelve vacio, y el filtro responde 401 (RN-08.11).
     */
    public Optional<Long> conductorDe(String jwt) {
        try {
            var cuerpo = Jwts.parser()
                    .verifyWith(clave)
                    .requireIssuer(EMISOR)
                    .clock(() -> Date.from(reloj.instant()))
                    .build()
                    .parseSignedClaims(jwt)
                    .getPayload();
            return Optional.of(Long.parseLong(cuerpo.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
