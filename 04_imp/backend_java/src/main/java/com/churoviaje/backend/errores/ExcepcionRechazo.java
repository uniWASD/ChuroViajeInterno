package com.churoviaje.backend.errores;

import org.springframework.http.HttpStatus;

public class ExcepcionRechazo extends RuntimeException {
    private final HttpStatus estadoHttp;
    private final String codigo;

    public ExcepcionRechazo(HttpStatus estadoHttp, String codigo, String mensaje) {
        super(mensaje);
        this.estadoHttp = estadoHttp;
        this.codigo = codigo;
    }

    public HttpStatus getEstadoHttp() {
        return estadoHttp;
    }

    public String getCodigo() {
        return codigo;
    }
}