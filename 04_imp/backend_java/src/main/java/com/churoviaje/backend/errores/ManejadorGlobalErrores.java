package com.churoviaje.backend.errores;

import com.churoviaje.backend.dominio.UbicacionFueraCoberturaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ManejadorGlobalErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalErrores.class);

    // Errores de negocio controlados (ej: 409, 422, 429)
    @ExceptionHandler(ExcepcionRechazo.class)
    public ResponseEntity<RespuestaRechazo> manejarExcepcionRechazo(ExcepcionRechazo ex) {
        RespuestaRechazo respuesta = new RespuestaRechazo(ex.getCodigo(), ex.getMessage());
        return ResponseEntity.status(ex.getEstadoHttp()).body(respuesta);
    }

   // Errores de validación de campos (@Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RespuestaRechazo> manejarValidacion(MethodArgumentNotValidException ex) {
        String mensaje = "Los datos enviados no son válidos";

        if (!ex.getBindingResult().getFieldErrors().isEmpty()) {
            mensaje = ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        }

        RespuestaRechazo respuesta = new RespuestaRechazo("PETICION_INVALIDA", mensaje);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(respuesta);
    }

    // Rutas no encontradas (404)
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<RespuestaRechazo> manejarNoEncontrado(NoResourceFoundException ex) {
        RespuestaRechazo respuesta = new RespuestaRechazo("NO_ENCONTRADO", "El recurso solicitado no existe");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(respuesta);
    }

    // JSON mal formados en el cuerpo de la petición
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RespuestaRechazo> manejarCuerpoMalFormado(HttpMessageNotReadableException ex) {
        RespuestaRechazo respuesta = new RespuestaRechazo("PETICION_INVALIDA", "La petición no tiene el formato esperado.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(respuesta);
    }

    // Errores del cliente atrapados por Spring (faltan parámetros, método no soportado, etc.)
    @ExceptionHandler({
        HttpMediaTypeNotSupportedException.class,
        HttpRequestMethodNotSupportedException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class
    })
    public ResponseEntity<RespuestaRechazo> manejarErroresSpring(Exception ex) {
        RespuestaRechazo respuesta = new RespuestaRechazo("PETICION_INVALIDA", "Formato o parámetros incorrectos");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(respuesta);
    }

    // Cualquier otro error inesperado (500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespuestaRechazo> manejarErrorInesperado(Exception ex) {
        log.error("Error inesperado en el servidor", ex);
        RespuestaRechazo respuesta = new RespuestaRechazo("ERROR_INTERNO", "Ocurrió un error inesperado en el servidor");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(respuesta);
    }

    @ExceptionHandler(UbicacionFueraCoberturaException.class)
    public ResponseEntity<RespuestaRechazo> manejarUbicacionFueraDeCobertura(
            UbicacionFueraCoberturaException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new RespuestaRechazo(ex.getCodigo(), ex.getMessage()));
    }
}
