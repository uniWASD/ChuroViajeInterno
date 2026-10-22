package com.churoviaje.backend.motor;

import com.churoviaje.backend.dominio.Ubicacion;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class MotorDeRutas {

    public Map<String, Object> calcularRuta(Ubicacion origen, Ubicacion destino, List<Ubicacion> paradas) {
        return Map.of("ok", true, "estado", "ENCONTRADA");
    }

    public Map<String, Object> revisarRuta(Map<String, Object> solicitud) {
        return Map.of("ok", true, "estado", "SIN_CAMBIOS");
    }

    public Map<String, Object> actualizarTrafico(Map<String, Object> evento) {
        return Map.of("ok", true);
    }

    public Map<String, Object> analizarTelemetria(Map<String, Object> lecturas) {
        return Map.of("ok", true);
    }

    public Map<String, Object> consultarEstado() {
        return Map.of("ok", true, "redCargada", true);
    }
}