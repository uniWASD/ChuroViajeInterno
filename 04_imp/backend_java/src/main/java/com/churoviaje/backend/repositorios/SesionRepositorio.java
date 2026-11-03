package com.churoviaje.backend.repositorios;

import com.churoviaje.backend.dominio.Sesion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SesionRepositorio extends JpaRepository<Sesion, Long> {

    @Query("select s from Sesion s join fetch s.conductor where s.tokenRenovacionHash = :hash")
    Optional<Sesion> buscarPorHash(@Param("hash") String hash);

    /** Invalida el token de renovacion (RN-08.14). Cerrar dos veces no es un error. */
    @Modifying
    @Query("delete from Sesion s where s.tokenRenovacionHash = :hash")
    int borrarPorHash(@Param("hash") String hash);
}
