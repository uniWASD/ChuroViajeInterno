package com.churoviaje.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa las tareas periodicas (por ejemplo, el vencimiento de eventos). Las pruebas las apagan con
 * {@code churoviaje.tareas.habilitadas=false} y llaman a cada tarea a mano, con el reloj de prueba.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "churoviaje.tareas.habilitadas", havingValue = "true", matchIfMissing = true)
public class ConfiguracionTareas {
}
