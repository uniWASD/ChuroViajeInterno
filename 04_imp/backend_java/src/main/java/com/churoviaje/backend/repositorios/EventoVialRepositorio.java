package com.churoviaje.backend.repositorios;

import com.churoviaje.backend.dominio.EventoVial;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Guarda y busca eventos viales con sentencias preparadas (D-37). La ubicacion se guarda como punto PostGIS:
 * {@code ST_MakePoint} recibe primero la longitud y despues la latitud.
 */
@Repository
public class EventoVialRepositorio {

    private final JdbcTemplate jdbc;

    public EventoVialRepositorio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Bloquea al conductor hasta que termine la transaccion, para que dos reportes suyos que llegan juntos se
     * revisen de a uno (RN-01.8, RN-01.9). Devuelve si es simulado, o vacio si el conductor ya no existe.
     */
    public Optional<Boolean> bloquearConductor(long conductorId) {
        return jdbc.query("select simulado from conductor where id = ? for no key update",
                (fila, n) -> fila.getBoolean("simulado"), conductorId).stream().findFirst();
    }

    /** RN-01.9: cuantos reportes registro el conductor desde {@code desde}. */
    public long contarReportesDesde(long conductorId, Instant desde) {
        Long cantidad = jdbc.queryForObject(
                "select count(*) from evento_vial where reportante_id = ? and registrado_en >= ?",
                Long.class, conductorId, Timestamp.from(desde));
        return cantidad == null ? 0 : cantidad;
    }

    /** RN-01.8: si el conductor ya tiene un evento del mismo tipo cerca y en el mismo rato que {@code nuevo}. */
    public boolean existeDuplicado(EventoVial nuevo) {
        Boolean existe = jdbc.queryForObject("""
                select exists (
                    select 1 from evento_vial
                    where reportante_id = ? and tipo_codigo = ?
                      and ocurrio_en > ? and ocurrio_en < ?
                      and ST_DWithin(ubicacion, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?))
                """, Boolean.class,
                nuevo.getReportanteId(), nuevo.getTipo().codigo(),
                Timestamp.from(nuevo.getOcurrioEn().minus(EventoVial.VENTANA_DUPLICADO)),
                Timestamp.from(nuevo.getOcurrioEn().plus(EventoVial.VENTANA_DUPLICADO)),
                nuevo.getUbicacion().longitud(), nuevo.getUbicacion().latitud(),
                EventoVial.DISTANCIA_DUPLICADO_METROS);
        return Boolean.TRUE.equals(existe);
    }

    /** Guarda el evento y devuelve su identificador. */
    public long insertar(EventoVial evento) {
        Long id = jdbc.queryForObject("""
                insert into evento_vial (tipo_codigo, ubicacion, ocurrio_en, registrado_en, reportante_id, origen,
                                         nivel, estado, votos_real, votos_falso, simulado)
                values (?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                returning id
                """, Long.class,
                evento.getTipo().codigo(), evento.getUbicacion().longitud(), evento.getUbicacion().latitud(),
                Timestamp.from(evento.getOcurrioEn()), Timestamp.from(evento.getRegistradoEn()),
                evento.getReportanteId(), evento.getOrigen().name(), evento.getNivel(), evento.getEstado().name(),
                evento.getVotosReal(), evento.getVotosFalso(), evento.isSimulado());
        if (id == null) {
            throw new IllegalStateException("La base no devolvio el identificador del evento");
        }
        return id;
    }
}
