package com.churoviaje.backend.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PropiedadesSeguridad.class)
public class ConfiguracionBasica {

    /** Reloj unico: las pruebas lo reemplazan para no esperar una hora ni 30 dias. */
    @Bean
    public Clock reloj() {
        return Clock.systemUTC();
    }
}
