package com.churoviaje.backend.motor;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuracion del wrapper del motor core (T-095, D-15).
 *
 * @param habilitado      si el backend arranca el nucleo C++; apagado, las operaciones responden 503
 * @param ejecutable      ruta del ejecutable del nucleo ({@code MOTOR_PATH})
 * @param redVial         ruta del archivo de la red vial que carga el nucleo ({@code RED_VIAL_PATH})
 * @param tiempoMaximo    cuanto puede tardar el nucleo en responder una peticion, y cuanto puede esperar
 *                        una peticion en la fila (RN-03.15: 3 segundos)
 * @param esperaArranque  cuanto se espera a que el nucleo cargue la red vial antes de volver a intentarlo
 * @param esperaReinicio  pausa entre un fallo del nucleo y el siguiente arranque
 */
@ConfigurationProperties(prefix = "motor")
public record PropiedadesMotor(
        @DefaultValue("false") boolean habilitado,
        String ejecutable,
        String redVial,
        @DefaultValue("3s") Duration tiempoMaximo,
        @DefaultValue("60s") Duration esperaArranque,
        @DefaultValue("2s") Duration esperaReinicio) {

    public PropiedadesMotor {
        if (habilitado && (vacio(ejecutable) || vacio(redVial))) {
            throw new IllegalStateException(
                    "El motor esta habilitado pero faltan MOTOR_PATH (ejecutable del nucleo) o RED_VIAL_PATH (red vial)");
        }
        if (tiempoMaximo.isZero() || tiempoMaximo.isNegative() || esperaArranque.isNegative()
                || esperaReinicio.isNegative()) {
            throw new IllegalStateException("Los tiempos del motor deben ser positivos");
        }
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
