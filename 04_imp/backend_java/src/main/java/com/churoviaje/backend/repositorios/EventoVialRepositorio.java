package com.churoviaje.backend.repositorios;

import com.churoviaje.backend.dominio.EventoVial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventoVialRepositorio extends JpaRepository<EventoVial, Long> {
}