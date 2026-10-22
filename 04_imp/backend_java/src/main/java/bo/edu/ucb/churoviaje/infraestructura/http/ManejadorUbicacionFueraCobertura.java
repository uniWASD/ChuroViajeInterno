package bo.edu.ucb.churoviaje.infraestructura.http;

import bo.edu.ucb.churoviaje.dominio.ubicacion.UbicacionFueraCoberturaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ManejadorUbicacionFueraCobertura {
    public record RespuestaError(String codigo, String mensaje) {
    }

    @ExceptionHandler(UbicacionFueraCoberturaException.class)
    public ResponseEntity<RespuestaError> responder(UbicacionFueraCoberturaException excepcion) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new RespuestaError(excepcion.getCodigo(), excepcion.getMessage()));
    }
}
