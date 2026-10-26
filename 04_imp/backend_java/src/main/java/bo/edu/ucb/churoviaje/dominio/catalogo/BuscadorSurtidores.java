package bo.edu.ucb.churoviaje.dominio.catalogo;

import java.util.Optional;

/** Puerto de lectura. La implementación de persistencia no modifica la clave. */
public interface BuscadorSurtidores {
    Optional<Long> buscarPorAliasNormalizado(String clave);
}
