package com.churoviaje.backend.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class ConfiguracionRelojDePrueba {

    @Bean
    @Primary
    RelojDePrueba relojDePrueba() {
        return new RelojDePrueba();
    }
}
