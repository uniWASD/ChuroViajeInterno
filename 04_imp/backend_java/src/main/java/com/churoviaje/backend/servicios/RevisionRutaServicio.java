package com.churoviaje.backend.servicios;

import com.churoviaje.backend.dto.RevisionRutaPeticion;
import com.churoviaje.backend.dto.RevisionRutaRespuesta;
import com.churoviaje.backend.motor.MotorDeRutas;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Caso de uso «Recalcular ruta» (CU-04, HU-06). Le pide a {@link MotorDeRutas} que revise lo que falta de la ruta
 * Activa: si un evento vigente nuevo cae sobre ella, el motor busca una alternativa y la propone solo si cuesta
 * menos que seguir (RN-04.13 a RN-04.16, RN-04.19). El servidor no guarda la ruta (D-05).
 */
@Service
public class RevisionRutaServicio {

    private static final Logger LOG = LoggerFactory.getLogger(RevisionRutaServicio.class);

    private final MotorDeRutas motor;
    private final ObjectMapper json;

    public RevisionRutaServicio(MotorDeRutas motor, ObjectMapper json) {
        this.motor = motor;
        this.json = json;
    }

    public RevisionRutaRespuesta revisar(RevisionRutaPeticion peticion) {
        ObjectNode mensaje = json.createObjectNode();
        mensaje.set("posicion", RespuestasDelMotor.punto(json, peticion.posicion()));
        ArrayNode tramos = mensaje.putArray("tramos");
        peticion.tramos().forEach(tramos::add);
        mensaje.set("destino", RespuestasDelMotor.punto(json, peticion.destino()));
        ArrayNode paradas = mensaje.putArray("paradas");
        peticion.paradasOVacio().forEach(p -> paradas.add(RespuestasDelMotor.punto(json, p)));
        // El núcleo guarda los ids de los eventos como texto, igual que en actualizar_trafico.
        ArrayNode avisados = mensaje.putArray("eventos_avisados");
        peticion.eventosAvisadosOVacio().forEach(id -> avisados.add(String.valueOf(id)));

        // Si el núcleo no responde dentro de los 3 segundos, se reinicia o está apagado, el wrapper lanza
        // MotorNoDisponibleException y la respuesta es 503 (RN-03.15).
        return traducir(motor.revisarRuta(mensaje));
    }

    private RevisionRutaRespuesta traducir(JsonNode r) {
        RespuestasDelMotor.exigirOk(r, "revisar_ruta");
        String estado = r.path("estado").asText("");
        switch (estado) {
            case RevisionRutaRespuesta.SIN_CAMBIOS:
                return RevisionRutaRespuesta.sinCambios();
            case RevisionRutaRespuesta.RUTA_DESACTUALIZADA:
                return RevisionRutaRespuesta.rutaDesactualizada();
            case RevisionRutaRespuesta.SIN_ALTERNATIVA:
                return RevisionRutaRespuesta.sinAlternativa(evento(r));
            case RevisionRutaRespuesta.ALTERNATIVA:
                JsonNode actual = r.path("costo_actual");
                return RevisionRutaRespuesta.alternativa(evento(r), r.path("costo").asLong(),
                        actual.isNumber() ? actual.asLong() : null, r.path("distancia_m").asLong(),
                        r.path("tiempo_s").asLong(), RespuestasDelMotor.trazado(r), RespuestasDelMotor.tramos(r));
            default:
                LOG.error("Respuesta de revisión sin un estado conocido: {}", r);
                throw new IllegalStateException("Respuesta de revisión sin un estado conocido: " + estado);
        }
    }

    /** El backend le pasa al motor los ids de sus eventos, así que siempre vuelve uno de ellos. */
    private static long evento(JsonNode r) {
        String texto = r.path("evento").asText("");
        try {
            return Long.parseLong(texto);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("El motor devolvió un evento que no es del backend: " + texto, e);
        }
    }
}
