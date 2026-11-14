package com.churoviaje.backend.servicios;

import com.churoviaje.backend.dominio.ResultadoUbicacionRuta;
import com.churoviaje.backend.dominio.Ubicacion;
import com.churoviaje.backend.dominio.ValidadorPuntosRuta;
import com.churoviaje.backend.dominio.cobertura.CoberturaManchaUrbana;
import com.churoviaje.backend.dto.RutaPeticion;
import com.churoviaje.backend.dto.RutaRespuesta;
import com.churoviaje.backend.motor.MotorDeRutas;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Caso de uso «Calcular ruta» (CU-03). Valida que los puntos caigan en la mancha urbana sin llamar al motor
 * (RN-03.3) y le pide la ruta a {@link MotorDeRutas}. No guarda la ruta ni usa datos de otros conductores
 * (RN-03.16, RN-03.17, D-05).
 */
@Service
public class RutaServicio {

    private static final Logger LOG = LoggerFactory.getLogger(RutaServicio.class);

    private final ValidadorPuntosRuta validador;
    private final MotorDeRutas motor;
    private final ObjectMapper json;

    public RutaServicio(CoberturaManchaUrbana cobertura, MotorDeRutas motor, ObjectMapper json) {
        this.validador = new ValidadorPuntosRuta(cobertura);
        this.motor = motor;
        this.json = json;
    }

    public RutaRespuesta calcular(RutaPeticion peticion) {
        List<Ubicacion> paradas = peticion.paradasOVacio();
        if (validador.validar(peticion.origen(), peticion.destino(), paradas) == ResultadoUbicacionRuta.PUNTO_INVALIDO) {
            return RutaRespuesta.puntoInvalido();
        }

        ObjectNode mensaje = json.createObjectNode();
        mensaje.set("origen", RespuestasDelMotor.punto(json, peticion.origen()));
        mensaje.set("destino", RespuestasDelMotor.punto(json, peticion.destino()));
        ArrayNode lista = mensaje.putArray("paradas");
        paradas.forEach(p -> lista.add(RespuestasDelMotor.punto(json, p)));

        // Si el núcleo no responde a tiempo, se reinicia o está apagado, el wrapper lanza
        // MotorNoDisponibleException y la respuesta es 503 (RN-03.15).
        return traducir(motor.calcularRuta(mensaje));
    }

    private RutaRespuesta traducir(JsonNode r) {
        RespuestasDelMotor.exigirOk(r, "calcular_ruta");
        String estado = r.path("estado").asText("");
        switch (estado) {
            case RutaRespuesta.SIN_RUTA:
                return RutaRespuesta.sinRuta();
            case RutaRespuesta.PUNTO_INVALIDO:
                // RN-03.4: el punto está en la mancha urbana, pero lejos de cualquier calle vehicular.
                return RutaRespuesta.puntoInvalido();
            case RutaRespuesta.ENCONTRADA:
                break;
            default:
                LOG.error("Respuesta de ruta sin un estado conocido: {}", r);
                throw new IllegalStateException("Respuesta de ruta sin un estado conocido: " + estado);
        }
        return RutaRespuesta.encontrada(r.path("distancia_m").asLong(), r.path("tiempo_s").asLong(),
                RespuestasDelMotor.trazado(r), RespuestasDelMotor.tramos(r));
    }
}
