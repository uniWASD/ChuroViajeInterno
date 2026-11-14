package com.churoviaje.backend.infraestructura.pg;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Conversiones que repiten todos los repositorios. Las fechas viajan como {@code OffsetDateTime} en UTC, que el
 * driver manda como {@code timestamptz}: no dependen de la zona horaria de la maquina.
 */
final class ValoresSql {

    private ValoresSql() {
    }

    /** Pone los parametros {@code ?} de una sentencia preparada. */
    @FunctionalInterface
    interface Parametros {

        void poner(PreparedStatement sentencia) throws SQLException;
    }

    /** Convierte la fila actual de un resultado. */
    @FunctionalInterface
    interface LectorFila<T> {

        T leer(ResultSet fila) throws SQLException;
    }

    static void ponerInstante(PreparedStatement sentencia, int posicion, Instant instante) throws SQLException {
        sentencia.setObject(posicion, OffsetDateTime.ofInstant(instante, ZoneOffset.UTC));
    }

    static Instant leerInstante(ResultSet fila, String columna) throws SQLException {
        return fila.getObject(columna, OffsetDateTime.class).toInstant();
    }

    static void ponerLongONulo(PreparedStatement sentencia, int posicion, Long valor) throws SQLException {
        if (valor == null) {
            sentencia.setNull(posicion, Types.BIGINT);
        } else {
            sentencia.setLong(posicion, valor);
        }
    }

    static Long leerLongONulo(ResultSet fila, String columna) throws SQLException {
        long valor = fila.getLong(columna);
        return fila.wasNull() ? null : valor;
    }
}
