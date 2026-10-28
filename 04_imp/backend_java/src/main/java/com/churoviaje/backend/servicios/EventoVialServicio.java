package com.churoviaje.backend.servicios;

import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import com.churoviaje.backend.dominio.EventoVial;
import com.churoviaje.backend.dominio.Ubicacion;

@Service
public class EventoVialServicio {
    public EventoVial registrarEvento(String tipo, Ubicacion ubicacion,
            OffsetDateTime horaConfirmacion, Integer minutosElegidos) {
        // TODO HU-01 (#53): reemplazar por persistencia cuando exista la migración.
        return new EventoVial(tipo, horaConfirmacion, minutosElegidos, ubicacion);
    }
}
