package com.churoviaje.backend.motor;

import com.churoviaje.backend.repositorios.EventoVialRepositorio;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * D-15: cada vez que el wrapper arranca o reinicia el nucleo, le envia todos los eventos vigentes antes de
 * atender rutas, para que nunca empiece con el trafico en blanco (T-099).
 */
@Component
public class EventosVigentesDelMotor implements EventosVigentesParaMotor {

    private final EventoVialRepositorio eventos;
    private final ObjectMapper json;
    private final Clock reloj;

    public EventosVigentesDelMotor(EventoVialRepositorio eventos, ObjectMapper json, Clock reloj) {
        this.eventos = eventos;
        this.json = json;
        this.reloj = reloj;
    }

    @Override
    public List<ObjectNode> eventosVigentes() {
        return eventos.vigentes(reloj.instant()).stream()
                .map(AvisoTrafico::nace)
                .map(aviso -> aviso.aMensaje(json))
                .toList();
    }
}
