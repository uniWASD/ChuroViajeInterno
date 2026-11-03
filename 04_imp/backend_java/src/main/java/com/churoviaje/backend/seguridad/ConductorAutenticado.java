package com.churoviaje.backend.seguridad;

/** Quien hace la peticion cuando entro con el JWT de un conductor. Los controladores lo reciben con {@code @AuthenticationPrincipal}. */
public record ConductorAutenticado(long id) {}
