package com.churoviaje.backend.servicios;

import com.churoviaje.backend.dominio.Ubicacion;
import com.churoviaje.backend.dto.RutaRespuesta.Punto;
import com.churoviaje.backend.dto.RutaRespuesta.TramoRuta;
import com.churoviaje.backend.motor.MotorNoDisponibleException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Lo que repiten los servicios que hablan con el motor: armar puntos y leer las respuestas del núcleo. */
final class RespuestasDelMotor {

    private static final Logger LOG = LoggerFactory.getLogger(RespuestasDelMotor.class);

    private RespuestasDelMotor() {
    }

    /** Un punto del pipe: {@code [latitud, longitud]}. */
    static ArrayNode punto(ObjectMapper json, Ubicacion u) {
        return json.createArrayNode().add(u.latitud()).add(u.longitud());
    }

    /**
     * Un {@code "ok":false} del núcleo: sin la red vial cargada el motor todavía no sirve (503); cualquier otro
     * código es un error del backend, porque el backend arma el mensaje.
     */
    static void exigirOk(JsonNode r, String operacion) {
        if (r.path("ok").asBoolean(false)) {
            return;
        }
        String codigo = r.path("error").path("codigo").asText("");
        if ("RED_NO_CARGADA".equals(codigo)) {
            throw new MotorNoDisponibleException();
        }
        LOG.error("El núcleo rechazó «{}»: {}", operacion, r);
        throw new IllegalStateException("El núcleo rechazó «" + operacion + "»: " + codigo);
    }

    static List<Punto> trazado(JsonNode r) {
        List<Punto> trazado = new ArrayList<>();
        for (JsonNode p : r.path("trazado")) {
            trazado.add(new Punto(p.path(0).asDouble(), p.path(1).asDouble()));
        }
        return trazado;
    }

    static List<TramoRuta> tramos(JsonNode r) {
        List<TramoRuta> tramos = new ArrayList<>();
        for (JsonNode t : r.path("tramos")) {
            tramos.add(new TramoRuta(t.path("id").asText(), t.path("calle").asText()));
        }
        return tramos;
    }
}
