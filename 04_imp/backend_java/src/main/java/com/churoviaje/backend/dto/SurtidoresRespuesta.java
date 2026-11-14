package com.churoviaje.backend.dto;

import com.churoviaje.backend.dominio.EstadoCombustible;
import com.churoviaje.backend.dominio.EventoVialRespuesta;
import com.churoviaje.backend.dominio.OrigenCombustible;
import com.churoviaje.backend.dominio.Surtidor;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * {@code GET /surtidores} (CU-05): los surtidores del más cercano al más lejano, con un dato por tipo de combustible
 * (RN-05.2 a RN-05.6). Nunca dice quién aportó un dato (RN-05.7). Un surtidor sin datos vigentes trae
 * {@code informacionReciente} falso y la lista de combustibles vacía.
 */
public record SurtidoresRespuesta(List<SurtidorConEstado> surtidores) {

    public record SurtidorConEstado(long id, String nombre, EventoVialRespuesta.Coordenadas ubicacion, long distanciaM,
            boolean informacionReciente, List<Combustible> combustibles) {

        public static SurtidorConEstado de(Surtidor s, double distanciaM, List<Combustible> combustibles) {
            return new SurtidorConEstado(s.id(), s.nombre(),
                    new EventoVialRespuesta.Coordenadas(s.ubicacion().latitud(), s.ubicacion().longitud()),
                    Math.round(distanciaM), !combustibles.isEmpty(), combustibles);
        }
    }

    /**
     * RN-05.4: la fila solo si se conoce; los litros, el estado de ANH y el momento de la lectura solo si el dato es
     * de ANH (RNF-07).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Combustible(String tipo, String nombre, boolean disponible, String origen, OffsetDateTime fecha,
            String fila, BigDecimal litros, String estadoAnh, OffsetDateTime consultadoEn) {

        public static Combustible de(EstadoCombustible e, Map<String, String> nombres) {
            return new Combustible(e.tipoCodigo(), nombres.getOrDefault(e.tipoCodigo(), e.tipoCodigo()),
                    e.disponible(), e.origen().name(), hora(e.fecha()), e.fila() == null ? null : e.fila().name(),
                    e.origen() == OrigenCombustible.ANH ? e.litros() : null, e.estadoAnh(), hora(e.consultadoEn()));
        }

        private static OffsetDateTime hora(Instant instante) {
            return instante == null ? null : OffsetDateTime.ofInstant(instante, EventoVialRespuesta.ZONA);
        }
    }
}
