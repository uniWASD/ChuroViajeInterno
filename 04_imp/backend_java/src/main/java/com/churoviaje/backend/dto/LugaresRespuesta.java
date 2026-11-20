package com.churoviaje.backend.dto;

import com.churoviaje.backend.dominio.EventoVialRespuesta;
import com.churoviaje.backend.dominio.Ubicacion;
import com.churoviaje.backend.dominio.busqueda.CategoriaLugar;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** {@code GET /lugares} (D-47): hasta 10 destinos para el texto buscado. */
public record LugaresRespuesta(List<LugarEncontrado> lugares) {

    /** {@code distanciaM} solo viene si la app mandó su ubicación. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LugarEncontrado(String nombre, String categoria, EventoVialRespuesta.Coordenadas ubicacion,
            Long distanciaM) {

        public static LugarEncontrado de(String nombre, CategoriaLugar categoria, Ubicacion ubicacion,
                Double distanciaM) {
            return new LugarEncontrado(nombre, categoria.name(),
                    new EventoVialRespuesta.Coordenadas(ubicacion.latitud(), ubicacion.longitud()),
                    distanciaM == null ? null : Math.round(distanciaM));
        }
    }
}
