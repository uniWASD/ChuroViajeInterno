package com.churoviaje.backend.motor;

import com.churoviaje.backend.errores.ExcepcionRechazo;
import org.springframework.http.HttpStatus;

/** El nucleo no responde a tiempo, se esta reiniciando o esta apagado: el backend responde 503 (RN-03.15). */
public class MotorNoDisponibleException extends ExcepcionRechazo {

    public MotorNoDisponibleException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "MOTOR_NO_DISPONIBLE",
                "No pudimos calcular la ruta en este momento. Intenta de nuevo en unos segundos.");
    }
}
