package com.churoviaje.backend.repositorios;

import com.churoviaje.backend.dominio.Conductor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ConductorRepositorio extends JpaRepository<Conductor, Long> {
    Optional<Conductor> findByGoogleId(String googleId);
}