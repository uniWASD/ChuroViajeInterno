package com.churoviaje.backend.dominio.monitoreo;

import com.churoviaje.backend.dominio.EstadoCombustible;
import com.churoviaje.backend.dominio.catalogo.ValorFila;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;

/**
 * El resultado de un lote de WhatsApp ya validado (CU-06 pasos 5 y 6). Cada aviso se valida por separado: el que
 * falla se descarta con su posición y su motivo, y los demás siguen (RN-06.15). Si el lote trae varios avisos del
 * mismo surtidor y tipo, solo queda el más reciente (RN-06.17).
 */
public final class ResultadoAnalisis {

    /** Los motivos de descarte son una lista fija (RN-06.15, 05_contratos). */
    public enum Motivo {
        SURTIDOR_DESCONOCIDO,
        COMBUSTIBLE_INVALIDO,
        ESTADO_INVALIDO,
        FECHA_INVALIDA,
        FORMATO_INVALIDO
    }

    public record Descartado(int indice, Motivo motivo) {}

    /** Máximo de avisos por lote (05_contratos); uno más grande responde 400. */
    public static final int MAXIMO_AVISOS = 1000;
    /**
     * Un mensaje no puede ser del futuro; se tolera una diferencia pequeña entre el reloj del servicio y el del
     * backend.
     */
    static final Duration TOLERANCIA_RELOJ = Duration.ofMinutes(5);

    private final List<EstadoCombustible> estados;
    private final int aceptados;
    private final List<Descartado> descartados;

    private ResultadoAnalisis(List<EstadoCombustible> estados, int aceptados, List<Descartado> descartados) {
        this.estados = List.copyOf(estados);
        this.aceptados = aceptados;
        this.descartados = List.copyOf(descartados);
    }

    public static ResultadoAnalisis validar(List<AvisoRecibido> avisos, CatalogoDeAvisos catalogo, Instant ahora) {
        if (avisos.size() > MAXIMO_AVISOS) {
            throw new IllegalArgumentException("Un lote tiene como máximo " + MAXIMO_AVISOS + " avisos");
        }
        Map<String, EstadoCombustible> masRecientes = new LinkedHashMap<>();
        List<Descartado> descartados = new ArrayList<>();
        int aceptados = 0;
        for (int i = 0; i < avisos.size(); i++) {
            AvisoRecibido aviso = avisos.get(i);
            Motivo[] motivo = new Motivo[1];
            EstadoCombustible estado = convertir(aviso, catalogo, ahora, motivo);
            if (estado == null) {
                descartados.add(new Descartado(i, motivo[0]));
                continue;
            }
            aceptados++;
            masRecientes.merge(estado.surtidorId() + "|" + estado.tipoCodigo(), estado,
                    (actual, nuevo) -> nuevo.esMasRecienteQue(actual) ? nuevo : actual);
        }
        return new ResultadoAnalisis(new ArrayList<>(masRecientes.values()), aceptados, descartados);
    }

    private static EstadoCombustible convertir(AvisoRecibido aviso, CatalogoDeAvisos catalogo, Instant ahora,
            Motivo[] motivo) {
        if (aviso.surtidor() == null || aviso.combustible() == null || aviso.estado() == null
                || aviso.fecha() == null) {
            motivo[0] = Motivo.FORMATO_INVALIDO;
            return null;
        }
        OptionalLong surtidor = catalogo.surtidor(aviso.surtidor());
        if (surtidor.isEmpty()) {
            motivo[0] = Motivo.SURTIDOR_DESCONOCIDO;
            return null;
        }
        String tipo = catalogo.combustible(aviso.combustible()).orElse(null);
        if (tipo == null) {
            motivo[0] = Motivo.COMBUSTIBLE_INVALIDO;
            return null;
        }
        Boolean disponible = switch (aviso.estado().trim().toUpperCase(Locale.ROOT)) {
            case "DISPONIBLE" -> Boolean.TRUE;
            case "AGOTADO" -> Boolean.FALSE;
            default -> null;
        };
        if (disponible == null) {
            motivo[0] = Motivo.ESTADO_INVALIDO;
            return null;
        }
        Instant fecha = fecha(aviso.fecha());
        if (fecha == null || fecha.isAfter(ahora.plus(TOLERANCIA_RELOJ))) {
            motivo[0] = Motivo.FECHA_INVALIDA;
            return null;
        }
        return EstadoCombustible.deWhatsapp(surtidor.getAsLong(), tipo, disponible, fila(aviso.fila()), fecha);
    }

    /** La fecha del mensaje con su zona, como en el contrato. Sin zona no se sabe cuándo fue. */
    private static Instant fecha(String texto) {
        try {
            return OffsetDateTime.parse(texto.trim()).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** P-15: un valor de fila desconocido se acepta, pero el aviso se guarda sin fila (05_contratos). */
    static ValorFila fila(String texto) {
        if (texto == null) {
            return null;
        }
        try {
            return ValorFila.valueOf(texto.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Los datos que hay que guardar: uno por surtidor y tipo, el más reciente del lote. */
    public List<EstadoCombustible> estados() {
        return estados;
    }

    /** Los avisos válidos, aunque no cambien nada por ser más viejos que el dato actual (05_contratos). */
    public int aceptados() {
        return aceptados;
    }

    public List<Descartado> descartados() {
        return descartados;
    }
}
