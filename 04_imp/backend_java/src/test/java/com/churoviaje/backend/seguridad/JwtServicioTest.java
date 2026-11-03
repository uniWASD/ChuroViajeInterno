package com.churoviaje.backend.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.churoviaje.backend.config.PropiedadesSeguridad;
import com.churoviaje.backend.config.RelojDePrueba;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServicioTest {

    private static final String CLAVE = "clave-de-prueba-que-tiene-mas-de-32-bytes-ok";

    private RelojDePrueba reloj;
    private JwtServicio jwt;

    private static PropiedadesSeguridad propiedades(String clave) {
        return new PropiedadesSeguridad(
                new PropiedadesSeguridad.Jwt(clave, Duration.ofHours(1)),
                new PropiedadesSeguridad.Renovacion(Duration.ofDays(30)),
                new PropiedadesSeguridad.Google("id"),
                "c".repeat(32));
    }

    @BeforeEach
    void preparar() {
        reloj = new RelojDePrueba();
        jwt = new JwtServicio(propiedades(CLAVE), reloj);
    }

    @Test
    void unJwtRecienEmitidoIdentificaAlConductor() {
        String token = jwt.emitir(42L, reloj.instant());
        assertEquals(Optional.of(42L), jwt.conductorDe(token));
    }

    @Test
    void elJwtDuraUnaHora() {
        Instant emision = reloj.instant();
        String token = jwt.emitir(1L, emision);
        assertEquals(emision.plus(Duration.ofHours(1)), jwt.vencimientoDesde(emision));

        reloj.avanzar(Duration.ofMinutes(59));
        assertTrue(jwt.conductorDe(token).isPresent());
        reloj.avanzar(Duration.ofMinutes(2));
        assertTrue(jwt.conductorDe(token).isEmpty());
    }

    @Test
    void unJwtFirmadoConOtraClaveNoSirve() {
        var otro = new JwtServicio(propiedades("otra-clave-distinta-que-tambien-pasa-los-32-bytes"), reloj);
        assertTrue(jwt.conductorDe(otro.emitir(1L, reloj.instant())).isEmpty());
    }

    @Test
    void unJwtDeOtroEmisorNoSirve() {
        String ajeno = Jwts.builder()
                .issuer("otro-sistema")
                .subject("1")
                .expiration(Date.from(reloj.instant().plus(Duration.ofHours(1))))
                .signWith(Keys.hmacShaKeyFor(CLAVE.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
        assertTrue(jwt.conductorDe(ajeno).isEmpty());
    }

    @Test
    void unJwtSinFirmaNoSirve() {
        String sinFirma = Jwts.builder()
                .issuer("churoviaje")
                .subject("1")
                .expiration(Date.from(reloj.instant().plus(Duration.ofHours(1))))
                .compact();
        assertTrue(jwt.conductorDe(sinFirma).isEmpty());
    }

    @Test
    void textoQueNoEsUnJwtNoSirve() {
        assertTrue(jwt.conductorDe("basura").isEmpty());
        assertTrue(jwt.conductorDe("a.b.c").isEmpty());
        assertTrue(jwt.conductorDe("").isEmpty());
    }

    @Test
    void unJwtConLaFirmaAlteradaNoSirve() {
        String token = jwt.emitir(1L, reloj.instant());
        String alterado = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");
        assertTrue(jwt.conductorDe(alterado).isEmpty());
    }
}
