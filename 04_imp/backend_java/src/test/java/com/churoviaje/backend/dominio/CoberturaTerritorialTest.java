package com.churoviaje.backend.dominio;

import com.churoviaje.backend.config.LectorCoberturaGeoJson;
import com.churoviaje.backend.dominio.cobertura.CoberturaManchaUrbana;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoberturaTerritorialTest {
    private static final Path ARCHIVO = Path.of("../datos/mancha_urbana_tarija.geojson");
    // Aproximadamente cinco metros: comprueba tambien el recorrido entre vertices OSM.
    private static final double PASO_GRADOS = 0.00005;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void incluyeTomatitasBosquecilloYHotel() throws Exception {
        verificarGrupo("tomatitas");
    }

    @Test
    void mantieneContinuidadVialDesdeTomatitasHastaElRancho() throws Exception {
        verificarGrupo("corredor_rancho");
    }

    @Test
    void incluyeCompuertaYAreaResidencialDelSureste() throws Exception {
        verificarGrupo("sureste_compuerta");
    }

    @Test
    void mantieneFueraSanLorenzoYCentrosRuralesNoIncluidos() throws Exception {
        verificarGrupo("exclusiones");
    }

    private void verificarGrupo(String grupo) throws Exception {
        CoberturaManchaUrbana cobertura = new LectorCoberturaGeoJson()
                .leer(mapper.readTree(ARCHIVO.toFile()));
        try (var recurso = getClass().getResourceAsStream("/cobertura/controles_tarija.geojson")) {
            assertNotNull(recurso, "Faltan los controles territoriales independientes");
            JsonNode documento = mapper.readTree(recurso);
            int controles = 0;
            for (JsonNode feature : documento.path("features")) {
                JsonNode propiedades = feature.path("properties");
                if (!grupo.equals(propiedades.path("grupo").asText())) {
                    continue;
                }
                controles++;
                assertTrue(propiedades.has("esperado_dentro"));
                assertTrue(!propiedades.path("fuente").asText().isBlank());
                boolean esperado = propiedades.path("esperado_dentro").asBoolean();
                String nombre = propiedades.path("nombre").asText();
                JsonNode geometria = feature.path("geometry");
                JsonNode coordenadas = geometria.path("coordinates");
                switch (geometria.path("type").asText()) {
                    case "Point" -> verificarPunto(cobertura, coordenadas.get(0).asDouble(),
                            coordenadas.get(1).asDouble(), esperado, nombre);
                    case "LineString" -> verificarRecorrido(cobertura, coordenadas, esperado, nombre);
                    case "Polygon" -> {
                        for (JsonNode anillo : coordenadas) {
                            verificarRecorrido(cobertura, anillo, esperado, nombre);
                        }
                    }
                    default -> throw new IllegalArgumentException("Control geografico no soportado: " + nombre);
                }
            }
            assertTrue(controles > 0, "El grupo no puede quedar sin controles: " + grupo);
        }
    }

    private void verificarRecorrido(CoberturaManchaUrbana cobertura, JsonNode puntos,
                                    boolean esperado, String nombre) {
        assertTrue(puntos.size() >= 2, "Recorrido vacio: " + nombre);
        for (int i = 1; i < puntos.size(); i++) {
            JsonNode a = puntos.get(i - 1);
            JsonNode b = puntos.get(i);
            double lon = a.get(0).asDouble();
            double lat = a.get(1).asDouble();
            double deltaLon = b.get(0).asDouble() - lon;
            double deltaLat = b.get(1).asDouble() - lat;
            int pasos = Math.max(1, (int) Math.ceil(
                    Math.max(Math.abs(deltaLon), Math.abs(deltaLat)) / PASO_GRADOS));
            for (int j = 0; j <= pasos; j++) {
                double fraccion = (double) j / pasos;
                verificarPunto(cobertura, lon + deltaLon * fraccion,
                        lat + deltaLat * fraccion, esperado, nombre);
            }
        }
    }

    private void verificarPunto(CoberturaManchaUrbana cobertura, double longitud,
                               double latitud, boolean esperado, String nombre) {
        assertEquals(esperado, cobertura.contiene(latitud, longitud),
                () -> nombre + ": latitud=" + latitud + ", longitud=" + longitud);
    }
}
