package com.churoviaje.backend.dto;

import com.churoviaje.backend.dominio.monitoreo.ResultadoAnalisis;
import java.util.List;

/** Respuesta de {@code POST /monitoreo/resultados}: cuántos avisos se aceptaron y cuáles se descartaron. */
public record ResultadoMonitoreo(int aceptados, List<ResultadoAnalisis.Descartado> descartados) {

    public static ResultadoMonitoreo de(ResultadoAnalisis resultado) {
        return new ResultadoMonitoreo(resultado.aceptados(), resultado.descartados());
    }
}
