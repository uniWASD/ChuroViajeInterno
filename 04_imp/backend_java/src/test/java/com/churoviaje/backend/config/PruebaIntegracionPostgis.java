package com.churoviaje.backend.config;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Misma configuracion de contexto y contenedor para CP-21, CP-22 y CP-23. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("pruebas")
@Import(TestcontainersPostgisConfig.class)
public abstract class PruebaIntegracionPostgis {
}
