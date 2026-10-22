package bo.edu.ucb.churoviaje.dominio.ubicacion;

import java.util.List;
import java.util.Objects;

public final class Anillo {
    public enum Posicion { FUERA, DENTRO, BORDE }

    private static final double TOLERANCIA = 1e-12;
    private final List<Coordenada> vertices;

    public Anillo(List<Coordenada> vertices) {
        Objects.requireNonNull(vertices, "Los vértices son obligatorios");
        if (vertices.size() < 4 || !vertices.getFirst().equals(vertices.getLast())) {
            throw new IllegalArgumentException("El anillo debe tener tres vértices y estar cerrado");
        }
        this.vertices = List.copyOf(vertices);
        double areaDoble = 0;
        for (int i = 0; i < vertices.size() - 1; i++) {
            Coordenada actual = vertices.get(i);
            Coordenada siguiente = vertices.get(i + 1);
            if (actual.equals(siguiente)) {
                throw new IllegalArgumentException("El anillo tiene una arista vacía");
            }
            areaDoble += actual.longitud() * siguiente.latitud() - siguiente.longitud() * actual.latitud();
        }
        if (Math.abs(areaDoble) <= TOLERANCIA) {
            throw new IllegalArgumentException("El anillo no encierra superficie");
        }
        for (int i = 0; i < vertices.size() - 1; i++) {
            for (int j = i + 2; j < vertices.size() - 1; j++) {
                if (i == 0 && j == vertices.size() - 2) {
                    continue;
                }
                if (seIntersectan(vertices.get(i), vertices.get(i + 1),
                        vertices.get(j), vertices.get(j + 1))) {
                    throw new IllegalArgumentException("El anillo se cruza consigo mismo");
                }
            }
        }
    }

    public List<Coordenada> vertices() {
        return vertices;
    }

    public Posicion posicion(Coordenada punto) {
        boolean dentro = false;
        for (int i = 0, j = vertices.size() - 2; i < vertices.size() - 1; j = i++) {
            Coordenada a = vertices.get(j);
            Coordenada b = vertices.get(i);
            if (enSegmento(a, b, punto)) {
                return Posicion.BORDE;
            }
            if ((a.latitud() > punto.latitud()) != (b.latitud() > punto.latitud())) {
                double interseccion = a.longitud() + (b.longitud() - a.longitud())
                        * (punto.latitud() - a.latitud()) / (b.latitud() - a.latitud());
                if (punto.longitud() < interseccion) {
                    dentro = !dentro;
                }
            }
        }
        return dentro ? Posicion.DENTRO : Posicion.FUERA;
    }

    public boolean cruza(Anillo otro) {
        for (int i = 0; i < vertices.size() - 1; i++) {
            for (int j = 0; j < otro.vertices.size() - 1; j++) {
                if (seIntersectan(vertices.get(i), vertices.get(i + 1),
                        otro.vertices.get(j), otro.vertices.get(j + 1))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double producto(Coordenada a, Coordenada b, Coordenada c) {
        return (b.longitud() - a.longitud()) * (c.latitud() - a.latitud())
                - (b.latitud() - a.latitud()) * (c.longitud() - a.longitud());
    }

    private static boolean enSegmento(Coordenada a, Coordenada b, Coordenada p) {
        return Math.abs(producto(a, b, p)) <= TOLERANCIA
                && p.longitud() >= Math.min(a.longitud(), b.longitud()) - TOLERANCIA
                && p.longitud() <= Math.max(a.longitud(), b.longitud()) + TOLERANCIA
                && p.latitud() >= Math.min(a.latitud(), b.latitud()) - TOLERANCIA
                && p.latitud() <= Math.max(a.latitud(), b.latitud()) + TOLERANCIA;
    }

    private static boolean seIntersectan(Coordenada a, Coordenada b, Coordenada c, Coordenada d) {
        double p1 = producto(a, b, c);
        double p2 = producto(a, b, d);
        double p3 = producto(c, d, a);
        double p4 = producto(c, d, b);
        if (enSegmento(a, b, c) || enSegmento(a, b, d)
                || enSegmento(c, d, a) || enSegmento(c, d, b)) {
            return true;
        }
        return ((p1 > TOLERANCIA && p2 < -TOLERANCIA) || (p1 < -TOLERANCIA && p2 > TOLERANCIA))
                && ((p3 > TOLERANCIA && p4 < -TOLERANCIA) || (p3 < -TOLERANCIA && p4 > TOLERANCIA));
    }
}
