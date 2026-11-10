package com.churoviaje.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.churoviaje.backend.errores.RespuestaRechazo;
import com.churoviaje.backend.seguridad.JwtFiltro;
import com.churoviaje.backend.seguridad.JwtServicio;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * RN-08.11 y D-13: todas las rutas piden el JWT de un conductor, salvo {@code /sesion/*}, que no
     * necesitan nada, y {@code /monitoreo/*}, que piden la credencial de servicio. El JwtFiltro no es un
     * bean a proposito: asi vive solo dentro de esta cadena y no se registra dos veces.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtServicio jwtServicio,
            PropiedadesSeguridad propiedades) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(manejador401())
                .accessDeniedHandler(manejador403())
            )
            .addFilterBefore(new JwtFiltro(jwtServicio, propiedades), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // /sesion/prueba (CU-07) se habilita con su propio issue.
                .requestMatchers("/sesion/google", "/sesion/renovar", "/sesion/cerrar").permitAll()
                .requestMatchers("/monitoreo/**").hasRole("SERVICIO")
                .anyRequest().hasRole("CONDUCTOR")
            );
        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint manejador401() {
        return (request, response, authException) -> {
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            RespuestaRechazo rechazo = new RespuestaRechazo("NO_AUTENTICADO", "Inicia sesión para continuar.");
            response.getWriter().write(objectMapper.writeValueAsString(rechazo));
        };
    }

    @Bean
    public AccessDeniedHandler manejador403() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(403);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            RespuestaRechazo rechazo = new RespuestaRechazo("ACCESO_DENEGADO", "No tienes permisos para realizar esta acción.");
            response.getWriter().write(objectMapper.writeValueAsString(rechazo));
        };
    }
}