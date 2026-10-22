package bo.edu.ucb.churoviaje.dominio.catalogo;

import bo.edu.ucb.churoviaje.dominio.ubicacion.Coordenada;
import java.util.List;
import java.util.Objects;

public record Surtidor(long id, String nombre, Coordenada posicion, List<String> aliases) {
    public Surtidor {
        if (id <= 0) {
            throw new IllegalArgumentException("El identificador del surtidor debe ser positivo");
        }
        Objects.requireNonNull(nombre);
        Objects.requireNonNull(posicion);
        aliases = List.copyOf(aliases);
    }
}
