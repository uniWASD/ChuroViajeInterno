package com.churoviaje.backend.infraestructura.pg;

import java.sql.SQLException;

/**
 * Una operacion con PostgreSQL fallo por algo que no es una regla de negocio (la base no responde, un error en el
 * SQL). Es una excepcion no comprobada, asi la transaccion se deshace y el manejador global responde 500.
 */
public class ErrorDeBaseDeDatos extends RuntimeException {

    public ErrorDeBaseDeDatos(String operacion, SQLException causa) {
        super("No se pudo " + operacion + " (SQLState " + causa.getSQLState() + ")", causa);
    }
}
