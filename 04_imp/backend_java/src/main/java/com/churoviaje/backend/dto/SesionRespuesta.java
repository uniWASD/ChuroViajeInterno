package com.churoviaje.backend.dto;

public class SesionRespuesta {
    private String jwt;
    private String refreshToken;
    private long expiracion; // En milisegundos o segundos, para que la app sepa cuándo expira

    public SesionRespuesta(String jwt, String refreshToken, long expiracion) {
        this.jwt = jwt;
        this.refreshToken = refreshToken;
        this.expiracion = expiracion;
    }

    public String getJwt() {
        return jwt;
    }

    public void setJwt(String jwt) {
        this.jwt = jwt;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public long getExpiracion() {
        return expiracion;
    }

    public void setExpiracion(long expiracion) {
        this.expiracion = expiracion;
    }
}