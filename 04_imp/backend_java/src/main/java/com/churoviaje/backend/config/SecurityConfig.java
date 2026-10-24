package com.churoviaje.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.churoviaje.backend.errores.RespuestaRechazo;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            // Permite renderizar la consola de H2 (mejora menor de la revisión)
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(manejador401())
                .accessDeniedHandler(manejador403())
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                // TODO HU-10 (#46): exigir JWT
                .anyRequest().permitAll()
            );
        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint manejador401() {
        return (request, response, authException) -> {
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            RespuestaRechazo rechazo = new RespuestaRechazo("NO_AUTENTICADO", "Tu sesión venció. Inicia sesión de nuevo.");
            response.getWriter().write(new ObjectMapper().writeValueAsString(rechazo));
        };
    }

    @Bean
    public AccessDeniedHandler manejador403() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(403);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            RespuestaRechazo rechazo = new RespuestaRechazo("ACCESO_DENEGADO", "No tienes permisos para realizar esta acción.");
            response.getWriter().write(new ObjectMapper().writeValueAsString(rechazo));
        };
    }
}