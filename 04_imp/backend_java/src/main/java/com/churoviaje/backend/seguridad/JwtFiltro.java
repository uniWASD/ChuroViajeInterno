package com.churoviaje.backend.seguridad;

import com.churoviaje.backend.config.PropiedadesSeguridad;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lee {@code Authorization: Bearer ...} y decide quien llama (RN-08.11, D-13):
 * <ul>
 *   <li>si coincide con la credencial de servicio, es el servicio de WhatsApp/LLM (rol SERVICIO);</li>
 *   <li>si es un JWT de conductor vigente, es un conductor (rol CONDUCTOR);</li>
 *   <li>en cualquier otro caso no autentica, y las reglas de {@code SecurityConfig} responden 401.</li>
 * </ul>
 * El filtro nunca rechaza por su cuenta: solo identifica. Quien no tiene el rol que pide la ruta recibe
 * 403 si estaba autenticado (un conductor en {@code /monitoreo/*}) y 401 si no lo estaba.
 */
public class JwtFiltro extends OncePerRequestFilter {

    private static final String ESQUEMA = "bearer ";

    static final String ROL_CONDUCTOR = "ROLE_CONDUCTOR";
    static final String ROL_SERVICIO = "ROLE_SERVICIO";

    private final JwtServicio jwtServicio;
    private final byte[] credencialServicio;

    public JwtFiltro(JwtServicio jwtServicio, PropiedadesSeguridad propiedades) {
        this.jwtServicio = jwtServicio;
        this.credencialServicio = propiedades.credencialServicio().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        String token = extraerToken(peticion.getHeader(HttpHeaders.AUTHORIZATION));
        if (token != null) {
            if (esCredencialDeServicio(token)) {
                autenticar("servicio", ROL_SERVICIO);
            } else {
                jwtServicio.conductorDe(token)
                        .ifPresent(id -> autenticar(new ConductorAutenticado(id), ROL_CONDUCTOR));
            }
        }
        cadena.doFilter(peticion, respuesta);
    }

    private static String extraerToken(String cabecera) {
        if (cabecera == null || cabecera.length() <= ESQUEMA.length()
                || !cabecera.regionMatches(true, 0, ESQUEMA, 0, ESQUEMA.length())) {
            return null;
        }
        String token = cabecera.substring(ESQUEMA.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /** Comparacion en tiempo constante: no revela cuantos caracteres coinciden. */
    private boolean esCredencialDeServicio(String token) {
        return MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), credencialServicio);
    }

    private static void autenticar(Object principal, String rol) {
        var autenticacion = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(rol)));
        SecurityContextHolder.getContext().setAuthentication(autenticacion);
    }
}
