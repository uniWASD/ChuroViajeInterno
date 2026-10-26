package bo.edu.ucb.churoviaje.infraestructura.cobertura;

import bo.edu.ucb.churoviaje.dominio.ubicacion.Anillo;
import bo.edu.ucb.churoviaje.dominio.ubicacion.Coordenada;
import bo.edu.ucb.churoviaje.dominio.ubicacion.CoberturaManchaUrbana;
import bo.edu.ucb.churoviaje.dominio.ubicacion.Poligono;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class LectorCoberturaGeoJson {
    public CoberturaManchaUrbana leer(JsonNode documento) {
        Objects.requireNonNull(documento, "Se requiere un GeoJSON");
        List<Poligono> poligonos = new ArrayList<>();
        String tipo = documento.path("type").asText();
        switch (tipo) {
            case "FeatureCollection" -> {
                JsonNode elementos = arregloNoVacio(documento.path("features"), "features");
                for (JsonNode elemento : elementos) {
                    if (!"Feature".equals(elemento.path("type").asText())) {
                        throw new IllegalArgumentException("La colección debe contener objetos Feature");
                    }
                    agregarGeometria(elemento.path("geometry"), poligonos);
                }
            }
            case "Feature" -> agregarGeometria(documento.path("geometry"), poligonos);
            case "Polygon", "MultiPolygon" -> agregarGeometria(documento, poligonos);
            default -> throw new IllegalArgumentException("Tipo GeoJSON no admitido: " + tipo);
        }
        return new CoberturaManchaUrbana(poligonos);
    }

    private void agregarGeometria(JsonNode geometria, List<Poligono> destino) {
        String tipo = geometria.path("type").asText();
        JsonNode coordenadas = arregloNoVacio(geometria.path("coordinates"), "coordinates");
        switch (tipo) {
            case "Polygon" -> destino.add(leerPoligono(coordenadas));
            case "MultiPolygon" -> {
                for (JsonNode poligono : coordenadas) {
                    destino.add(leerPoligono(arregloNoVacio(poligono, "polígono")));
                }
            }
            default -> throw new IllegalArgumentException("Geometría no admitida: " + tipo);
        }
    }

    private Poligono leerPoligono(JsonNode anillos) {
        arregloNoVacio(anillos, "anillos");
        Anillo exterior = leerAnillo(anillos.get(0));
        List<Anillo> huecos = new ArrayList<>();
        for (int i = 1; i < anillos.size(); i++) {
            huecos.add(leerAnillo(anillos.get(i)));
        }
        return new Poligono(exterior, huecos);
    }

    private Anillo leerAnillo(JsonNode anillo) {
        arregloNoVacio(anillo, "anillo");
        List<Coordenada> vertices = new ArrayList<>();
        for (JsonNode punto : anillo) {
            if (!punto.isArray() || punto.size() != 2
                    || !punto.get(0).isNumber() || !punto.get(1).isNumber()) {
                throw new IllegalArgumentException("Cada coordenada debe ser [longitud, latitud]");
            }
            // GeoJSON/RFC 7946 usa [longitud, latitud]; se invierte SOLO en el adaptador.
            vertices.add(new Coordenada(punto.get(1).asDouble(), punto.get(0).asDouble()));
        }
        return new Anillo(vertices);
    }

    private JsonNode arregloNoVacio(JsonNode nodo, String nombre) {
        if (nodo == null || !nodo.isArray() || nodo.isEmpty()) {
            throw new IllegalArgumentException("Estructura GeoJSON inválida: " + nombre);
        }
        return nodo;
    }
}
