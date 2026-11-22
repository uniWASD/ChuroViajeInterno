package com.churoviaje.backend.dominio;

import com.churoviaje.backend.dominio.catalogo.TipoCombustible;
import com.churoviaje.backend.errores.ExcepcionRechazo;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.http.HttpStatus;

/**
 * Un conductor dice que acaba de recargar un combustible en un surtidor (CU-05, HU-12). Solo puede decir que hay: una
 * confirmación nunca marca un surtidor como agotado (RN-05.12). La guarda un {@link ConfirmacionRecargaRepository}.
 *
 * @param fecha cuándo la aceptó el backend; es la fecha del dato de origen Conductores (RN-05.11)
 */
public record ConfirmacionRecarga(long conductorId, long surtidorId, String tipoCodigo, Instant fecha) {

    /** RN-05.13 y P-09: la parada tiene que estar a menos de 50 metros del surtidor. */
    public static final double DISTANCIA_MAXIMA_M = 50;
    /** RN-05.14 y P-14: el mismo conductor no repite el mismo surtidor y tipo antes de 60 minutos. */
    public static final Duration SILENCIO = Duration.ofMinutes(60);

    public ConfirmacionRecarga {
        Objects.requireNonNull(tipoCodigo, "tipoCodigo");
        Objects.requireNonNull(fecha, "fecha");
        if (TipoCombustible.DESCONOCIDO.equals(tipoCodigo)) {
            throw tipoNoPermitido();
        }
    }

    /**
     * RN-05.9 y RN-05.13: arma la confirmación si el tipo es uno de los cuatro conocidos y la parada está junto al
     * surtidor. La repetición (RN-05.14) la comprueba el servicio con la base, con el conductor bloqueado.
     *
     * @param distanciaM la distancia entre el surtidor y la ubicación donde la app detectó la parada, medida en PostGIS
     */
    public static ConfirmacionRecarga confirmar(long conductorId, Surtidor surtidor, TipoCombustible tipo,
            double distanciaM, Instant ahora) {
        if (TipoCombustible.DESCONOCIDO.equals(tipo.codigo())) {
            throw tipoNoPermitido();
        }
        if (!(distanciaM < DISTANCIA_MAXIMA_M)) {
            throw new ExcepcionRechazo(HttpStatus.UNPROCESSABLE_ENTITY, "LEJOS_DEL_SURTIDOR",
                    "Solo puedes confirmar una recarga junto al surtidor.");
        }
        return new ConfirmacionRecarga(conductorId, surtidor.id(), tipo.codigo(), ahora);
    }

    /** RN-05.14: desde cuándo una confirmación anterior la vuelve repetida. */
    public Instant repetidaDesde() {
        return fecha.minus(SILENCIO);
    }

    /** RN-05.11 y RN-05.12: el dato que deja: disponible, con origen Conductores, la fecha de la confirmación y sin fila. */
    public EstadoCombustible comoEstado() {
        return new EstadoCombustible(surtidorId, tipoCodigo, OrigenCombustible.CONDUCTORES, true, null, fecha,
                null, null, null);
    }

    public static ExcepcionRechazo repetida() {
        return new ExcepcionRechazo(HttpStatus.CONFLICT, "CONFIRMACION_REPETIDA",
                "Ya confirmaste este combustible en este surtidor hace poco.");
    }

    public static ExcepcionRechazo tipoNoPermitido() {
        return new ExcepcionRechazo(HttpStatus.UNPROCESSABLE_ENTITY, "TIPO_NO_PERMITIDO",
                "Elige qué combustible recargaste.");
    }
}
