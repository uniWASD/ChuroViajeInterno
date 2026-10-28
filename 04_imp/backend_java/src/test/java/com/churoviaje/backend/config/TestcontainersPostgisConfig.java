package com.churoviaje.backend.config;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersPostgisConfig {
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:16-3.6-alpine")
            .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("churoviaje_pruebas")
            .withUsername("churoviaje_test")
            .withPassword("solo_test_no_productivo");
    }
}
