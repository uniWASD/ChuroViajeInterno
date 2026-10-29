package com.churoviaje.backend.repositorios;

import com.churoviaje.backend.dominio.Sesion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SesionRepositorio extends JpaRepository<Sesion, Long> {
    Optional<Sesion> findByRefreshToken(String refreshToken);
}