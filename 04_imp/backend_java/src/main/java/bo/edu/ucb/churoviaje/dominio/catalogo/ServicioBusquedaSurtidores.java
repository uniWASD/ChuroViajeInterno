package bo.edu.ucb.churoviaje.dominio.catalogo;

import java.util.Objects;
import java.util.Optional;

/**
 * Normaliza nombres y alias antes de pedir la consulta al repositorio.
 * El nombre oficial (fuente ANH) se guarda como clave normalizada en
 * surtidor_alias; no se generan apodos que la API no haya reportado.
 */
public final class ServicioBusquedaSurtidores {
    private final BuscadorSurtidores repositorio;

    public ServicioBusquedaSurtidores(BuscadorSurtidores repositorio) {
        this.repositorio = Objects.requireNonNull(repositorio);
    }

    public Optional<Long> buscarPorNombreOAlias(String texto) {
        return repositorio.buscarPorAliasNormalizado(ClaveAlias.normalizar(texto));
    }
}
