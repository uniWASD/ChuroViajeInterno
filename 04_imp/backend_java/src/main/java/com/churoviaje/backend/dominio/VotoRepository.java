package com.churoviaje.backend.dominio;

/** Guarda los votos de los eventos (CU-02, HU-11). La implementacion para PostgreSQL vive en {@code infraestructura.pg} (D-41). */
public interface VotoRepository {

    /** RN-02.11: si el conductor ya voto este evento. */
    boolean yaVoto(long eventoId, long conductorId);

    /**
     * Guarda el voto si el conductor todavia no voto el evento. Devuelve falso, sin guardar nada, si ya habia un
     * voto suyo: la base es la segunda barrera de RN-02.11.
     */
    boolean guardarSiNoVoto(Voto voto);
}
