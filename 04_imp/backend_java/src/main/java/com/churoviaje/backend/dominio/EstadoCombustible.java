package com.churoviaje.backend.dominio;

import com.churoviaje.backend.dominio.catalogo.ValorFila;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lo que una fuente dice de un combustible en un surtidor: disponible o agotado, la fila si se conoce y la fecha del
 * dato (RN-05.4). Hay uno por surtidor, tipo y origen; el más reciente de un origen reemplaza al anterior del mismo
 * origen (RN-06.17, RN-09.14) y cuál de los tres orígenes se muestra lo decide la consulta (RN-05.3).
 *
 * @param fecha        la fecha del dato: la del mensaje, la de la confirmación o el {@code updated_at} de ANH
 * @param litros       solo ANH (RN-09.12)
 * @param estadoAnh    solo ANH: {@code ALTO}, {@code MEDIO} o {@code BAJO}, tal como llega (D-31)
 * @param consultadoEn solo ANH: cuándo lo leyó el backend; de ahí se cuenta su vigencia (RN-09.17)
 */
public record EstadoCombustible(long surtidorId, String tipoCodigo, OrigenCombustible origen, boolean disponible,
        ValorFila fila, Instant fecha, BigDecimal litros, String estadoAnh, Instant consultadoEn) {

    /** RN-05.5 y P-30: un dato con más de 6 horas deja de tomarse en cuenta. */
    public static final Duration VIGENCIA = Duration.ofHours(6);

    public EstadoCombustible {
        Objects.requireNonNull(tipoCodigo, "tipoCodigo");
        Objects.requireNonNull(origen, "origen");
        Objects.requireNonNull(fecha, "fecha");
        if (origen == OrigenCombustible.ANH) {
            Objects.requireNonNull(consultadoEn, "Un dato de ANH sabe cuándo se leyó");
        } else if (litros != null || estadoAnh != null || consultadoEn != null) {
            throw new IllegalArgumentException("Los litros y el estado de ANH son solo de ANH");
        }
        if (litros != null && litros.signum() < 0) {
            throw new IllegalArgumentException("Los litros no pueden ser negativos (RN-09.7)");
        }
    }

    /** RN-06.16: un aviso de WhatsApp, con la fecha del mensaje y no la del análisis. */
    public static EstadoCombustible deWhatsapp(long surtidorId, String tipoCodigo, boolean disponible, ValorFila fila,
            Instant fechaDelMensaje) {
        return new EstadoCombustible(surtidorId, tipoCodigo, OrigenCombustible.WHATSAPP, disponible, fila,
                fechaDelMensaje, null, null, null);
    }

    /** Dos datos del mismo surtidor, tipo y origen se reemplazan entre sí. */
    public boolean mismaClave(EstadoCombustible otro) {
        return surtidorId == otro.surtidorId && tipoCodigo.equals(otro.tipoCodigo) && origen == otro.origen;
    }

    /** RN-06.17: solo un dato más reciente del mismo origen reemplaza al que hay. */
    public boolean esMasRecienteQue(EstadoCombustible otro) {
        return fecha.isAfter(otro.fecha);
    }

    /** RN-05.5, RN-09.17: desde cuándo se cuenta la vigencia. */
    public Instant vigenteDesde() {
        return origen == OrigenCombustible.ANH ? consultadoEn : fecha;
    }

    public boolean estaVigente(Instant ahora) {
        return ahora.isBefore(vigenteDesde().plus(VIGENCIA));
    }

    /**
     * Con la misma fecha, gana ANH y después WhatsApp: una confirmación se ve solo si es más reciente que las otras
     * dos fuentes (RN-05.15).
     */
    private static final Comparator<EstadoCombustible> PARA_MOSTRAR = Comparator
            .comparing(EstadoCombustible::fecha)
            .thenComparing(e -> switch (e.origen()) {
                case ANH -> 2;
                case WHATSAPP -> 1;
                case CONDUCTORES -> 0;
            });

    /**
     * RN-05.3 y RN-05.5: de los datos de un surtidor, uno por tipo de combustible, el más reciente de los vigentes,
     * venga del origen que venga. Un tipo sin datos vigentes no sale.
     */
    public static List<EstadoCombustible> paraMostrar(List<EstadoCombustible> deUnSurtidor, Instant ahora) {
        Map<String, EstadoCombustible> elegidos = new LinkedHashMap<>();
        for (EstadoCombustible e : deUnSurtidor) {
            if (e.estaVigente(ahora)) {
                elegidos.merge(e.tipoCodigo(), e, (a, b) -> PARA_MOSTRAR.compare(b, a) > 0 ? b : a);
            }
        }
        return new ArrayList<>(elegidos.values());
    }
}
