package com.churoviaje.backend.errores;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ManejadorGlobalErrores {

    // 1. Cuerpo mal formado (JSON roto) o validaciones fallidas -> 400 (Requisito clave de CP-21)
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<RespuestaRechazo> manejarCuerpoMalFormado(Exception ex) {
        RespuestaRechazo respuesta = new RespuestaRechazo(
            "FORMATO_INVALIDO", 
            "La petición no tiene el formato esperado."
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(respuesta);
    }

    // 2. Ruta inexistente -> 404
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<RespuestaRechazo> manejarRutaNoEncontrada(NoResourceFoundException ex) {
        RespuestaRechazo respuesta = new RespuestaRechazo(
            "RECURSO_NO_ENCONTRADO", 
            "La ruta solicitada no existe."
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(respuesta);
    }

    // 3. Excepciones personalizadas de rechazo
    @ExceptionHandler(ExcepcionRechazo.class)
    public ResponseEntity<RespuestaRechazo> manejarExcepcionRechazo(ExcepcionRechazo ex) {
        RespuestaRechazo respuesta = new RespuestaRechazo(ex.getCodigo(), ex.getMessage());
        return ResponseEntity.status(ex.getEstadoHttp()).body(respuesta);
    }

    // 4. Cualquier otro error inesperado -> 500 genérico (sin exponer stack traces internos)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespuestaRechazo> manejarErrorInesperado(Exception ex) {
        RespuestaRechazo respuesta = new RespuestaRechazo(
            "ERROR_INTERNO", 
            "Ocurrió un error inesperado en el servidor."
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(respuesta);
    }
}