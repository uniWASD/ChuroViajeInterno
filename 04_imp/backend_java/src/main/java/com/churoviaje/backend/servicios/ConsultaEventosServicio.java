package com.churoviaje.backend.servicios;

import com.churoviaje.backend.dominio.EventoRepository;
import com.churoviaje.backend.dominio.EventosRespuesta;
import com.churoviaje.backend.errores.ExcepcionRechazo;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta de eventos vigentes y de sus cambios (CU-02, HU-03). */
@Service
public class ConsultaEventosServicio {

    /**
     * La marca que se devuelve queda un poco antes de la consulta: un evento que se estaba guardando justo en ese
     * momento sale en la consulta siguiente. Algun cambio puede llegar dos veces, y la app simplemente lo reemplaza.
     */
    static final Duration SOLAPAMIENTO = Duration.ofSeconds(2);

    private final EventoRepository eventos;
    private final Clock reloj;

    public ConsultaEventosServicio(EventoRepository eventos, Clock reloj) {
        this.eventos = eventos;
        this.reloj = reloj;
    }

    /**
     * RN-02.2 a RN-02.6. La vigencia se calcula en cada consulta, sin esperar a la tarea periodica (RN-02.4). El
     * repositorio entrega los eventos ya como DTO, sin datos de conductores (RN-02.5, D-42).
     *
     * @param marca la que devolvio la consulta anterior, o {@code null} para recibir todos los vigentes
     */
    @Transactional(readOnly = true)
    public EventosRespuesta consultar(String marca) {
        Instant ahora = reloj.instant();
        String marcaNueva = ahora.minus(SOLAPAMIENTO).toString();
        if (marca == null || marca.isBlank()) {
            return new EventosRespuesta(marcaNueva, true, eventos.vigentesParaMostrar(ahora), List.of());
        }
        Instant desde = leerMarca(marca);
        return new EventosRespuesta(marcaNueva, false, eventos.vigentesCambiadosDesdeParaMostrar(desde, ahora),
                eventos.quitadosEntre(desde, ahora));
    }

    private static Instant leerMarca(String marca) {
        try {
            return Instant.parse(marca);
        } catch (DateTimeParseException e) {
            throw new ExcepcionRechazo(HttpStatus.BAD_REQUEST, "PETICION_INVALIDA",
                    "La marca de la última consulta no es válida.");
        }
    }
}
