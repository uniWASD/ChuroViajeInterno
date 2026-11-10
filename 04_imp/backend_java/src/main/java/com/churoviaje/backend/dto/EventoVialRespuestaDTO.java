package com.churoviaje.backend.dto;

import java.time.OffsetDateTime;

// RN-01.12: La respuesta NUNCA incluye quién lo reportó (no ponemos conductorId aquí)
public record EventoVialRespuestaDTO(
    Long id,
    String tipo,
    String estado,
    OffsetDateTime horaOcurrencia,
    Integer votos
) {}