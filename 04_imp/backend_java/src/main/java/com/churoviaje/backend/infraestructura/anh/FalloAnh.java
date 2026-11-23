package com.churoviaje.backend.infraestructura.anh;

import java.time.Instant;

/**
 * Una petición a ANH que no dio una respuesta utilizable, después de los reintentos que correspondían (RN-09.4). Lleva
 * el motivo para el registro del ciclo (RN-09.19); nunca se traduce en litros (RN-09.15).
 */
public class FalloAnh extends Exception {

    /** Por qué falló la petición. */
    public enum Motivo {
        SIN_CONEXION, TIEMPO_AGOTADO, CERTIFICADO, HTTP, REDIRECCION_NO_PERMITIDA, DEMASIADO_GRANDE, NO_ES_JSON,
        EN_PAUSA
    }

    private final String ruta;
    private final Motivo motivo;
    private final Integer estadoHttp;
    private final int intentos;
    private final Instant pausarHasta;

    public FalloAnh(String ruta, Motivo motivo, Integer estadoHttp, int intentos, Instant pausarHasta,
            String detalle) {
        super(detalle);
        this.ruta = ruta;
        this.motivo = motivo;
        this.estadoHttp = estadoHttp;
        this.intentos = intentos;
        this.pausarHasta = pausarHasta;
    }

    public String ruta() {
        return ruta;
    }

    public Motivo motivo() {
        return motivo;
    }

    /** El último estado HTTP recibido, o {@code null} si no llegó ninguna respuesta. */
    public Integer estadoHttp() {
        return estadoHttp;
    }

    public int intentos() {
        return intentos;
    }

    /** RN-09.4: si ANH pidió esperar más de 30 segundos, hasta cuándo no se le vuelve a consultar; si no, null. */
    public Instant pausarHasta() {
        return pausarHasta;
    }
}
