package com.churoviaje.backend.dominio;

import com.churoviaje.backend.config.LectorCoberturaGeoJson;
import com.churoviaje.backend.dominio.cobertura.CoberturaManchaUrbana;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Protege la cobertura aprobada con controles independientes, sin llamadas de red.
 * Comprueba el area residencial y el Bosquecillo de Tomatitas, los puentes y las
 * vias F1 hacia El Rancho, Compuerta y el sector sureste. Cadillar, Obrajes y San
 * Lorenzo permanecen fuera, junto con puntos rurales en los cuatro bordes.
 * Poneloya protege un punto interior con poco margen. Las fuentes y los margenes
 * medidos en UTM 20S se conservan en el recurso GeoJSON.
 *
 * <p>Los recorridos y perimetros se interpolan con pasos de hasta 0.00005 grados
 * por eje (aproximadamente cinco metros por eje en Tarija), ademas de los puntos
 * interiores. Los controles no se generan a partir del contorno que se valida.</p>
 */
class CoberturaTerritorialTest {
    private static final Path ARCHIVO = Path.of("../datos/mancha_urbana_tarija.geojson");
    private static final double PASO_GRADOS = 0.00005;
    private static CoberturaManchaUrbana cobertura;
    private static JsonNode documento;

    @BeforeAll
    static void cargarGeometrias() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        cobertura = new LectorCoberturaGeoJson().leer(mapper.readTree(ARCHIVO.toFile()));
        try (var recurso = CoberturaTerritorialTest.class
                .getResourceAsStream("/cobertura/controles_tarija.geojson")) {
            assertNotNull(recurso, "Faltan los controles territoriales independientes");
            documento = mapper.readTree(recurso);
        }
    }

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

    @Test
    void mantieneFueraUnPuntoRuralPorCadaBorde() throws Exception {
        verificarGrupo("exclusiones_rurales");
        Set<String> bordes = new HashSet<>();
        for (JsonNode feature : documento.path("features")) {
            JsonNode propiedades = feature.path("properties");
            if ("exclusiones_rurales".equals(propiedades.path("grupo").asText())) {
                assertFalse(propiedades.path("esperado_dentro").asBoolean());
                assertEquals("Point", feature.path("geometry").path("type").asText());
                bordes.add(propiedades.path("borde").asText());
            }
        }
        assertEquals(Set.of("norte", "este", "sur", "oeste"), bordes);
    }

    @Test
    void mantienePoneloyaDentroDelContornoAprobado() throws Exception {
        verificarGrupo("poneloya");
        for (JsonNode feature : documento.path("features")) {
            JsonNode propiedades = feature.path("properties");
            if ("poneloya".equals(propiedades.path("grupo").asText())) {
                assertTrue(propiedades.path("esperado_dentro").asBoolean());
                assertEquals("Point", feature.path("geometry").path("type").asText());
            }
        }
    }

    private void verificarGrupo(String grupo) throws Exception {
        int controles = 0;
        for (JsonNode feature : documento.path("features")) {
            JsonNode propiedades = feature.path("properties");
            if (!grupo.equals(propiedades.path("grupo").asText())) {
                continue;
            }
            controles++;
            assertTrue(propiedades.path("esperado_dentro").isBoolean());
            assertFalse(propiedades.path("fuente").asText().isBlank());
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
