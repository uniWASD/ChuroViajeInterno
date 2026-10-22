package bo.edu.ucb.churoviaje.infraestructura.http;

import bo.edu.ucb.churoviaje.dominio.ubicacion.UbicacionFueraCoberturaException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;

class ManejadorCoberturaTest {
    private final ManejadorUbicacionFueraCobertura manejador = new ManejadorUbicacionFueraCobertura();

    @Test
    void retorna422ConCodigoYMensajeEnEspanol() {
        var respuesta = manejador.responder(UbicacionFueraCoberturaException.paraReporte());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, respuesta.getStatusCode());
        assertNotNull(respuesta.getBody());
        assertEquals("UBICACION_FUERA_COBERTURA", respuesta.getBody().codigo());
        assertTrue(respuesta.getBody().mensaje().contains("Tarija"));
    }
}
