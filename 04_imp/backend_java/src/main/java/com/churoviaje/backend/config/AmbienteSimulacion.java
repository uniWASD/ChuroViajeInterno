package com.churoviaje.backend.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Lo que el ambiente permite al simulador (RN-07.5, RN-07.13, P-20). En el perfil {@code prod} las sesiones de prueba
 * y el modo demostracion quedan apagados aunque alguien defina la credencial por error: produccion nunca recibe
 * usuarios fantasma.
 */
@Component
public class AmbienteSimulacion {

    private static final Logger LOG = LoggerFactory.getLogger(AmbienteSimulacion.class);

    private final byte[] credencial;
    private final boolean habilitada;
    private final boolean demostracion;

    public AmbienteSimulacion(PropiedadesSimulacion propiedades, Environment entorno) {
        boolean produccion = entorno.acceptsProfiles(Profiles.of("prod"));
        boolean conCredencial = !propiedades.credencial().isEmpty();
        if (produccion && (conCredencial || propiedades.demostracion())) {
            LOG.error("El perfil prod ignora la credencial de prueba y el modo demostración: aquí no hay simulador");
        }
        this.habilitada = conCredencial && !produccion;
        this.demostracion = propiedades.demostracion() && !produccion;
        this.credencial = propiedades.credencial().getBytes(StandardCharsets.UTF_8);
        if (habilitada) {
            LOG.info("Sesiones de prueba habilitadas en este ambiente (CU-07); modo demostración: {}",
                    demostracion ? "sí" : "no");
        }
    }

    /** RN-07.5: si este ambiente entrega sesiones de prueba. */
    public boolean sesionesDePruebaHabilitadas() {
        return habilitada;
    }

    /** RN-07.13: si un conductor real ve los datos simulados. */
    public boolean modoDemostracion() {
        return demostracion;
    }

    /** Comparacion en tiempo constante: no revela cuantos caracteres coinciden. */
    public boolean credencialCorrecta(String recibida) {
        if (!habilitada || recibida == null) {
            return false;
        }
        return MessageDigest.isEqual(recibida.getBytes(StandardCharsets.UTF_8), credencial);
    }
}
