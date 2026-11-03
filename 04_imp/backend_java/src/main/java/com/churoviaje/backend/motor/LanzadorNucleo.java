package com.churoviaje.backend.motor;

import java.io.IOException;
import java.util.List;

/** Arranca el proceso del nucleo. Separado del wrapper para probarlo con un nucleo falso. */
@FunctionalInterface
public interface LanzadorNucleo {

    Process lanzar() throws IOException;

    /** El nucleo real: {@code <ejecutable> --red <archivo>} (05_contratos.md, «Archivo de la red vial»). */
    static LanzadorNucleo desde(PropiedadesMotor propiedades) {
        return () -> new ProcessBuilder(comando(propiedades)).start();
    }

    static List<String> comando(PropiedadesMotor propiedades) {
        return List.of(propiedades.ejecutable(), "--red", propiedades.redVial());
    }
}
