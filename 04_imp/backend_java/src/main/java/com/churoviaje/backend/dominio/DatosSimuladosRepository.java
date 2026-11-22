package com.churoviaje.backend.dominio;

import java.util.List;

/**
 * Borra los datos que dejó el simulador (RN-07.8): las filas con la marca de simulado en {@code voto},
 * {@code evento_vial} y {@code conductor}, y nada más. La implementación para PostgreSQL vive en
 * {@code infraestructura.pg} (D-41).
 */
public interface DatosSimuladosRepository {

    /** Cuántas filas se borraron de cada tabla, y qué eventos simulados seguían en el mapa (para avisar al motor). */
    record Borrado(int votos, int eventos, int conductores, List<Long> eventosQueSeMostraban) {}

    /** Hay que llamarlo dentro de una transacción: o se borra todo lo simulado o no se borra nada. */
    Borrado borrarTodo();

    /** Cuántas filas simuladas quedan en las tres tablas (después de borrar debe ser 0). */
    long contarFilasSimuladas();
}
