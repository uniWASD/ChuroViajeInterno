package com.churoviaje.backend.config;

import com.churoviaje.backend.servicios.LugarServicio;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Al arrancar, carga los lugares de búsqueda (D-47) si hay un archivo configurado ({@code LUGARES_PATH}). Sin archivo
 * el backend arranca igual y la búsqueda solo encuentra surtidores. Un archivo que no se puede leer detiene el
 * arranque, como la cobertura: es mejor enterarse en el despliegue que con una búsqueda vacía.
 */
@Component
public class CargaLugares implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(CargaLugares.class);

    private final LugarServicio lugares;
    private final String ruta;

    public CargaLugares(LugarServicio lugares, @Value("${churoviaje.lugares.ruta:}") String ruta) {
        this.lugares = lugares;
        this.ruta = ruta;
    }

    @Override
    public void run(ApplicationArguments argumentos) throws Exception {
        if (ruta == null || ruta.isBlank()) {
            LOG.warn("Sin LUGARES_PATH: la búsqueda solo encontrará surtidores.");
            return;
        }
        LugarServicio.Carga carga = lugares.cargar(Path.of(ruta).toAbsolutePath().normalize());
        if (!carga.cargado()) {
            LOG.info("Lugares de búsqueda al día: {} del extracto {}.", carga.guardados(), carga.fechaExtracto());
        }
    }
}
