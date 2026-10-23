package com.churoviaje.backend.servicios;

import com.churoviaje.backend.dominio.EventoVial;
import com.churoviaje.backend.repositorios.EventoVialRepositorio;
import org.springframework.stereotype.Service;

@Service
public class EventoVialServicio {

    private final EventoVialRepositorio eventoVialRepositorio;

    public EventoVialServicio(EventoVialRepositorio eventoVialRepositorio) {
        this.eventoVialRepositorio = eventoVialRepositorio;
    }

    public EventoVial registrarEjemplo(EventoVial evento) {
        // Coordinación básica con el repositorio sin implementar lógica de negocio definitiva todavía
        return eventoVialRepositorio.save(evento);
    }
}