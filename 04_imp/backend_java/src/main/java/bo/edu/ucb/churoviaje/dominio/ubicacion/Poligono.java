package bo.edu.ucb.churoviaje.dominio.ubicacion;

import java.util.List;
import java.util.Objects;

public final class Poligono {
    private final Anillo exterior;
    private final List<Anillo> huecos;

    public Poligono(Anillo exterior, List<Anillo> huecos) {
        this.exterior = Objects.requireNonNull(exterior, "El contorno es obligatorio");
        this.huecos = List.copyOf(Objects.requireNonNull(huecos, "Los huecos no pueden ser nulos"));
        for (Anillo hueco : this.huecos) {
            if (exterior.cruza(hueco)) {
                throw new IllegalArgumentException("Un hueco cruza el contorno exterior");
            }
            for (Coordenada punto : hueco.vertices()) {
                if (exterior.posicion(punto) != Anillo.Posicion.DENTRO) {
                    throw new IllegalArgumentException("Un hueco cae fuera del contorno");
                }
            }
        }
        for (int i = 0; i < this.huecos.size(); i++) {
            for (int j = i + 1; j < this.huecos.size(); j++) {
                Anillo primero = this.huecos.get(i);
                Anillo segundo = this.huecos.get(j);
                if (primero.cruza(segundo)
                        || primero.posicion(segundo.vertices().getFirst()) != Anillo.Posicion.FUERA
                        || segundo.posicion(primero.vertices().getFirst()) != Anillo.Posicion.FUERA) {
                    throw new IllegalArgumentException("Los huecos se superponen");
                }
            }
        }
    }

    public boolean contiene(Coordenada punto) {
        Anillo.Posicion posicionExterior = exterior.posicion(punto);
        if (posicionExterior == Anillo.Posicion.FUERA) {
            return false;
        }
        if (posicionExterior == Anillo.Posicion.BORDE) {
            return true;
        }
        for (Anillo hueco : huecos) {
            Anillo.Posicion posicionHueco = hueco.posicion(punto);
            if (posicionHueco == Anillo.Posicion.BORDE) {
                return true;
            }
            if (posicionHueco == Anillo.Posicion.DENTRO) {
                return false;
            }
        }
        return true;
    }
}
