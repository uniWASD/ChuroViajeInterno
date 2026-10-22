package bo.edu.ucb.churoviaje.dominio.catalogo;

import java.util.Objects;

public record TipoCombustible(String codigo, String nombre) {
    public TipoCombustible {
        Objects.requireNonNull(codigo);
        Objects.requireNonNull(nombre);
    }

    public boolean disponibleParaConductor() {
        return !"DESCONOCIDO".equals(codigo);
    }
}
