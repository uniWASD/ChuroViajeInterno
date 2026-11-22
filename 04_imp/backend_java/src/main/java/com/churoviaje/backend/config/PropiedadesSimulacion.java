package com.churoviaje.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion del simulador en el backend (CU-07, P-20). Solo el perfil {@code pruebas} la enciende; en
 * {@code prod} se ignora siempre ({@link AmbienteSimulacion}).
 *
 * @param credencial   credencial de prueba que presenta el simulador en {@code POST /sesion/prueba}. Vacia: no hay
 *                     sesiones de prueba (RN-07.5)
 * @param demostracion si un conductor real ve los datos simulados (RN-07.13)
 * @param registro     archivo donde se anota cada prueba de carga, ademas del registro del backend (RN-07.11)
 */
@ConfigurationProperties(prefix = "churoviaje.simulacion")
public record PropiedadesSimulacion(String credencial, boolean demostracion, String registro) {

    /** Igual que la credencial de servicio: una credencial corta se adivina. */
    static final int MINIMO_CARACTERES_CREDENCIAL = 32;

    public PropiedadesSimulacion {
        credencial = credencial == null ? "" : credencial.trim();
        registro = registro == null ? "" : registro.trim();
        if (!credencial.isEmpty() && credencial.length() < MINIMO_CARACTERES_CREDENCIAL) {
            throw new IllegalStateException("SIMULACION_CREDENCIAL tiene menos de " + MINIMO_CARACTERES_CREDENCIAL
                    + " caracteres: la credencial de prueba debe ser larga y aleatoria");
        }
    }
}
