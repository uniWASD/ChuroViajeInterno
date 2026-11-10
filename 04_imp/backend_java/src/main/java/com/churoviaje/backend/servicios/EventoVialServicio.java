package com.churoviaje.backend.servicios;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.churoviaje.backend.dominio.EventoVial;
import com.churoviaje.backend.dominio.TipoEventoVial;
import com.churoviaje.backend.repositorios.EventoVialRepositorio;

@Service
public class EventoVialServicio {

    private final EventoVialRepositorio repositorio;
    private final GeometryFactory geometryFactory;

    public EventoVialServicio(EventoVialRepositorio repositorio) {
        this.repositorio = repositorio;
        // SRID 4326 es el estándar mundial de GPS (WGS84) que usa PostGIS
        this.geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    }

    // RN-01.11: @Transactional asegura que si algo falla, no se guarda nada a medias.
    @Transactional
    public EventoVial registrarEvento(String tipoStr, Double lon, Double lat,
            OffsetDateTime horaConfirmacion, Integer minutosElegidos, Long conductorId) {

        // 1. Transformar String a Enum
        TipoEventoVial tipo;
        try {
            tipo = TipoEventoVial.valueOf(tipoStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Tipo de evento desconocido");
        }

        // 2. RN-01.6: Validar si la ubicación está dentro de Tarija (Responde 422)
        if (!estaDentroDeTarija(lon, lat)) {
            // Nota: Aquí podrías lanzar tu UbicacionFueraCoberturaException si tu equipo lo requiere
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "La ubicación está fuera de la mancha urbana de Tarija");
        }

        // 3. Crear el punto espacial para PostGIS
        Point ubicacionEspacial = geometryFactory.createPoint(new Coordinate(lon, lat));

        // 4. Instanciar la Entidad (Aquí saltan las validaciones RN-01.3 y RN-01.4 de la clase EventoVial)
        EventoVial nuevoEvento;
        try {
            nuevoEvento = new EventoVial(tipo, horaConfirmacion, minutosElegidos, ubicacionEspacial, conductorId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage()); // Responde 422 si es Congestión o futuro
        }

        // 5. RN-01.7: Validar que el evento no sea demasiado viejo para su tipo (Responde 422)
        if (eventoExpiro(nuevoEvento.getHoraOcurrencia(), tipo)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "El evento ya superó la vigencia de su tipo");
        }

        // 6. RN-01.9: El sexto reporte en 10 minutos responde 429 (Too Many Requests)
        OffsetDateTime hace10Minutos = OffsetDateTime.now().minusMinutes(10);
        int reportesRecientes = repositorio.countByConductorIdAndHoraConfirmacionAfter(conductorId, hace10Minutos);
        if (reportesRecientes >= 5) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Límite excedido: Máximo 5 reportes cada 10 minutos");
        }

        // 7. RN-01.8: Evitar duplicados (mismo conductor, mismo tipo, a <100m en los últimos 10 min) (Responde 409)
        boolean esDuplicado = repositorio.existeEventoDuplicadoCercano(conductorId, tipo.name(), hace10Minutos, lon, lat);
        if (esDuplicado) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya reportaste un " + tipo.name() + " muy cerca en los últimos 10 minutos");
        }

        // 8. RN-01.10: Todo es válido. ¡Guardar en la base de datos!
        return repositorio.save(nuevoEvento);
    }

    // --- MÉTODOS AUXILIARES ---

    private boolean estaDentroDeTarija(Double lon, Double lat) {
        // Validación rápida mediante "Bounding Box" de la mancha urbana de Tarija.
        // Si tu equipo usa un Polígono GeoJSON oficial en ValidadorPuntosRuta, puedes conectarlo aquí.
        return lat >= -21.65 && lat <= -21.45 && lon >= -64.85 && lon <= -64.60;
    }

    private boolean eventoExpiro(OffsetDateTime horaOcurrencia, TipoEventoVial tipo) {
        long minutosDesdeOcurrencia = Duration.between(horaOcurrencia, OffsetDateTime.now()).toMinutes();
        
        // Asignamos una vigencia en minutos según el tipo de evento
        int vigenciaMinutos = switch (tipo) {
            case ACCIDENTE -> 120;       // Dura 2 horas
            case CONTROL_POLICIAL -> 60; // Dura 1 hora
            case BLOQUEO -> 240;         // Dura 4 horas
            default -> 60;
        };
        
        return minutosDesdeOcurrencia > vigenciaMinutos;
    }
}