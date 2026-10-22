package bo.edu.ucb.churoviaje.dominio.ubicacion;

import java.util.List;
import java.util.Objects;

public final class CoberturaManchaUrbana {
    private final List<Poligono> poligonos;

    public CoberturaManchaUrbana(List<Poligono> poligonos) {
        Objects.requireNonNull(poligonos, "La cobertura no puede ser nula");
        if (poligonos.isEmpty()) {
            throw new IllegalArgumentException("La cobertura debe incluir un polígono");
        }
        this.poligonos = List.copyOf(poligonos);
    }

    public boolean contiene(double latitud, double longitud) {
        Coordenada punto = new Coordenada(longitud, latitud);
        return poligonos.stream().anyMatch(poligono -> poligono.contiene(punto));
    }

    public int cantidadPoligonos() {
        return poligonos.size();
    }
}
