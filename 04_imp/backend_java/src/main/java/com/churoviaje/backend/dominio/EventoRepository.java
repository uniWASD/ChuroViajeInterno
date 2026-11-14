package com.churoviaje.backend.dominio;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Guarda y busca eventos viales (CU-01, CU-02). Las consultas para mostrar devuelven {@link EventoVialRespuesta};
 * las busquedas para operar con las reglas devuelven {@link EventoVial} (D-42). La implementacion para PostgreSQL
 * vive en {@code infraestructura.pg} (D-41).
 */
public interface EventoRepository {

    /** RN-01.9: cuantos reportes registro el conductor desde {@code desde}. */
    long contarReportesDesde(long conductorId, Instant desde);

    /** RN-01.8: si el conductor ya tiene un evento del mismo tipo cerca y en el mismo rato que {@code nuevo}. */
    boolean existeDuplicado(EventoVial nuevo);

    /** Guarda un evento nuevo y devuelve el identificador que le dio la base. */
    long guardarNuevo(EventoVial evento);

    /** Para operar: los eventos vigentes, que el motor recibe al arrancar el nucleo (RN-02.2, RN-02.3, T-099). */
    List<EventoVial> vigentes(Instant ahora);

    /** Para mostrar: los eventos vigentes, sin datos de conductores (RN-02.2 a RN-02.5). */
    List<EventoVialRespuesta> vigentesParaMostrar(Instant ahora);

    /**
     * Para mostrar: los eventos vigentes que nacieron o cambiaron despues de {@code marca}, por ejemplo los que se
     * confirmaron (RN-02.6).
     */
    List<EventoVialRespuesta> vigentesCambiadosDesdeParaMostrar(Instant marca, Instant ahora);

    /**
     * RN-02.4, RN-02.13 y RN-04.11: los identificadores de los eventos que dejaron de mostrarse entre {@code marca}
     * y {@code ahora}. Un retirado cuenta desde que se retiro; uno vencido, desde que vencio, aunque la tarea
     * periodica todavia no lo haya marcado (RN-02.4).
     */
    List<Long> quitadosEntre(Instant marca, Instant ahora);

    /** RN-02.4: marca como Vencido todo evento que supero su vigencia y devuelve cuales fueron. */
    List<Long> vencerHasta(Instant ahora);

    /**
     * Para operar: trae el evento y lo bloquea hasta que termine la transaccion, asi dos votos que llegan juntos se
     * cuentan de a uno y ninguno se pierde (RN-02.13, RN-02.14). Hay que llamarlo dentro de una transaccion.
     */
    Optional<EventoVial> buscarParaVotar(long id);

    /**
     * Para operar: la congestión Activa o Confirmada del tramo, con la misma marca de simulado, bloqueada hasta que
     * termine la transacción (RN-04.11, RN-04.20). Puede estar ya vencida si la tarea periódica todavía no la marcó:
     * quien la pide lo decide con {@link EventoVial#estaVigente}.
     */
    Optional<EventoVial> buscarCongestionActivaEnTramo(String tramoId, boolean simulado);

    /** RN-04.11: guarda el momento y el nivel nuevos de una congestión que se extiende, y su marca de cambio. */
    void actualizarCongestion(EventoVial evento, Instant ahora);

    /** RN-02.4: marca un evento como Vencido aunque la tarea periódica todavía no lo haya hecho. */
    void marcarVencido(long id, Instant ahora);

    /**
     * Guarda los votos y el estado del evento. Si el estado cambio, tambien su marca de cambio, para que la consulta
     * por cambios lo entregue (RN-02.6).
     */
    void actualizarVotos(EventoVial evento, boolean cambioDeEstado, Instant ahora);
}
