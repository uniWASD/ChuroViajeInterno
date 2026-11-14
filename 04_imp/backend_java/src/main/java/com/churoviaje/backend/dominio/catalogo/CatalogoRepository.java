package com.churoviaje.backend.dominio.catalogo;

import com.churoviaje.backend.dominio.catalogo.CatalogosRespuesta.TipoCombustibleDto;
import com.churoviaje.backend.dominio.catalogo.CatalogosRespuesta.TipoEventoDto;
import java.util.List;
import java.util.Optional;

/**
 * Catalogos que deja la migracion V1 (T-089): tipos de evento y tipos de combustible. Solo lectura. La
 * implementacion para PostgreSQL vive en {@code infraestructura.pg} (D-41).
 */
public interface CatalogoRepository {

    /** Para operar: el tipo de evento con ese codigo, o vacio si no existe (RN-01.3). */
    Optional<TipoEvento> buscarTipoDeEvento(String codigo);

    /** Para mostrar: todos los tipos de evento (D-42). */
    List<TipoEventoDto> tiposDeEventoParaMostrar();

    /** Para operar: todos los tipos de combustible, «Desconocido» incluido (RN-06.8). */
    List<TipoCombustible> tiposDeCombustible();

    /** Para mostrar: todos los tipos de combustible, con si el conductor puede elegirlo (RN-05.9, D-42). */
    List<TipoCombustibleDto> tiposDeCombustibleParaMostrar();
}
