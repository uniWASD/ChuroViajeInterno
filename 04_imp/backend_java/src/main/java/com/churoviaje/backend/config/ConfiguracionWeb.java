package com.churoviaje.backend.config;

import com.churoviaje.backend.seguridad.ConductorAutenticado;
import com.churoviaje.backend.servicios.RegistroPruebasCarga;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Anota en {@link RegistroPruebasCarga} cada petición de un usuario fantasma, con su estado y su duración (RN-07.11). */
@Configuration
public class ConfiguracionWeb implements WebMvcConfigurer {

    private static final String INICIO = ConfiguracionWeb.class.getName() + ".inicio";

    private final RegistroPruebasCarga registro;

    public ConfiguracionWeb(RegistroPruebasCarga registro) {
        this.registro = registro;
    }

    @Override
    public void addInterceptors(InterceptorRegistry interceptores) {
        interceptores.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest peticion, HttpServletResponse respuesta, Object manejador) {
                peticion.setAttribute(INICIO, System.nanoTime());
                return true;
            }

            @Override
            public void afterCompletion(HttpServletRequest peticion, HttpServletResponse respuesta, Object manejador,
                    Exception error) {
                var autenticacion = SecurityContextHolder.getContext().getAuthentication();
                if (autenticacion != null && autenticacion.getPrincipal() instanceof ConductorAutenticado conductor
                        && conductor.simulado() && peticion.getAttribute(INICIO) instanceof Long inicio) {
                    long ms = (System.nanoTime() - inicio) / 1_000_000;
                    registro.peticion(conductor.pruebaCarga(), respuesta.getStatus(), ms);
                }
            }
        });
    }
}
