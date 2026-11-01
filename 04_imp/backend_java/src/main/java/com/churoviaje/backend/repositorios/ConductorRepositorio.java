package com.churoviaje.backend.repositorios;

import com.churoviaje.backend.dominio.Conductor;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConductorRepositorio extends JpaRepository<Conductor, Long> {
    Optional<Conductor> findByGoogleId(String googleId);
}
