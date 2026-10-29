package com.churoviaje.backend.servicios;

import com.churoviaje.backend.dominio.Conductor;
import com.churoviaje.backend.dominio.Sesion;
import com.churoviaje.backend.dto.LoginPeticion;
import com.churoviaje.backend.dto.SesionRespuesta;
import com.churoviaje.backend.repositorios.ConductorRepositorio;
import com.churoviaje.backend.repositorios.SesionRepositorio;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;

@Service
public class SesionServicio {

    private final ConductorRepositorio conductorRepositorio;
    private final SesionRepositorio sesionRepositorio;

    @Value("${churoviaje.seguridad.google.client-id}")
    private String googleClientId;

    @Value("${churoviaje.seguridad.jwt.secreto}")
    private String jwtSecret;

    @Value("${churoviaje.seguridad.jwt.expiracion-ms}")
    private long jwtExpiracionMs;

    @Value("${churoviaje.seguridad.jwt.refresh-expiracion-dias}")
    private int refreshExpiracionDias;

    public SesionServicio(ConductorRepositorio conductorRepositorio, SesionRepositorio sesionRepositorio) {
        this.conductorRepositorio = conductorRepositorio;
        this.sesionRepositorio = sesionRepositorio;
    }

    public SesionRespuesta iniciarSesion(LoginPeticion peticion) {
        GoogleIdToken.Payload payload = validarTokenGoogle(peticion.getTokenGoogle());

        String googleId = payload.getSubject();
        String email = payload.getEmail();
        String nombre = (String) payload.get("name");

        Conductor conductor = conductorRepositorio.findByGoogleId(googleId)
                .orElseGet(() -> conductorRepositorio.save(new Conductor(googleId, email, nombre)));

        String jwt = generarJwt(conductor);

        String refreshToken = UUID.randomUUID().toString();
        LocalDateTime expiracionRefresh = LocalDateTime.now().plusDays(refreshExpiracionDias);

        Sesion sesion = new Sesion(conductor, refreshToken, expiracionRefresh);
        sesionRepositorio.save(sesion);

        return new SesionRespuesta(jwt, refreshToken, jwtExpiracionMs);
    }

    public SesionRespuesta renovarToken(String refreshToken) {
        Sesion sesion = sesionRepositorio.findByRefreshToken(refreshToken)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token de renovación inválido"));

        if (!sesion.isActiva() || sesion.getFechaExpiracion().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "El token de renovación ha expirado o la sesión está cerrada");
        }

        Conductor conductor = sesion.getConductor();
        String nuevoJwt = generarJwt(conductor);

        return new SesionRespuesta(nuevoJwt, refreshToken, jwtExpiracionMs);
    }

    public void cerrarSesion(String refreshToken) {
        Sesion sesion = sesionRepositorio.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión no encontrada"));

        sesion.setActiva(false);
        sesionRepositorio.save(sesion);
    }

    private GoogleIdToken.Payload validarTokenGoogle(String tokenString) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(),
                    new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(tokenString);
            if (idToken != null) {
                return idToken.getPayload();
            } else {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token de Google inválido o expirado");
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Error al validar token de Google: " + e.getMessage());
        }
    }

    private String generarJwt(Conductor conductor) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Date ahora = new Date();
        Date expiracion = new Date(ahora.getTime() + jwtExpiracionMs);

        return Jwts.builder()
                .setSubject(conductor.getId().toString())
                .claim("email", conductor.getEmail())
                .setIssuedAt(ahora)
                .setExpiration(expiracion)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}