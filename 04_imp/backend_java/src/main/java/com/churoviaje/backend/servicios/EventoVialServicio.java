package com.churoviaje.backend.servicios;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;

import com.churoviaje.backend.dominio.EventoVial;
import com.churoviaje.backend.dominio.Ubicacion;
import com.churoviaje.backend.repositorios.EventoVialRepositorio;

@Service
public class EventoVialServicio {

    private final EventoVialRepositorio repositorio;

    public EventoVialServicio(EventoVialRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    public EventoVial registrarEvento(String tipo, Ubicacion ubicacion, OffsetDateTime horaConfirmacion, Integer minutosElegidos) {
        // Ahora pasamos la ubicación al constructor de EventoVial
        EventoVial nuevoEvento = new EventoVial(tipo, horaConfirmacion, minutosElegidos, ubicacion);
        return repositorio.save(nuevoEvento);
    }
}