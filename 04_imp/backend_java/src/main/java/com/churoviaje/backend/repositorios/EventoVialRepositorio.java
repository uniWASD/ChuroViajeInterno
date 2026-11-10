package com.churoviaje.backend.repositorios;

import java.time.OffsetDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.churoviaje.backend.dominio.EventoVial;

@Repository
public interface EventoVialRepositorio extends JpaRepository<EventoVial, Long> {

    // RN-01.9 (P-04): Cuenta cuántos reportes ha hecho el conductor desde una fecha/hora específica.
    // Spring Boot escribe la consulta SQL automáticamente solo con leer el nombre del método.
    int countByConductorIdAndHoraConfirmacionAfter(Long conductorId, OffsetDateTime desde);

    // RN-01.8 (P-03): Verifica si existe un evento idéntico a menos de 100 metros en los últimos 10 minutos.
    // Usamos SQL Nativo y CAST a "geography" para que PostGIS mida la distancia en METROS reales y no en grados.
    @Query(value = "SELECT COUNT(*) > 0 FROM evento_vial " +
                   "WHERE conductor_id = :conductorId " +
                   "AND tipo = :tipo " +
                   "AND hora_ocurrencia >= :desde " +
                   "AND ST_DWithin(CAST(ubicacion AS geography), CAST(ST_SetSRID(ST_MakePoint(:lon, :lat), 4326) AS geography), 100)", 
           nativeQuery = true)
    boolean existeEventoDuplicadoCercano(
            @Param("conductorId") Long conductorId,
            @Param("tipo") String tipo,
            @Param("desde") OffsetDateTime desde,
            @Param("lon") double lon,
            @Param("lat") double lat
    );
}