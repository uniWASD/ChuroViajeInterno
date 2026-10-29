package com.churoviaje.backend.dominio;
import com.churoviaje.backend.dominio.cobertura.CoberturaManchaUrbana;
import com.churoviaje.backend.config.LectorCoberturaGeoJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CoberturaGeoJsonTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final LectorCoberturaGeoJson lector = new LectorCoberturaGeoJson();
    private final Path archivo = Path.of("../datos/mancha_urbana_tarija.geojson");
    private Ubicacion punto(String lat, String lon) { return new Ubicacion(new BigDecimal(lat), new BigDecimal(lon)); }
    @Test void cp22UsaElArchivoRealNoUnFixture() throws Exception {
        assertTrue(Files.isRegularFile(archivo));
        // CP-22: punto -21.5355/-64.7296 del contrato, no localización exacta de la Plaza Luis de Fuentes.
        var documento=mapper.readTree(archivo.toFile());
        // La entrega final debe contener la aprobacion registrada en todas sus geometrias.
        assertEquals("APROBADO", documento.path("estado_validacion").asText());
        for (var feature : documento.path("features")) {
            assertEquals("APROBADO", feature.path("properties").path("revision_equipo").asText());
        }
        // La integridad territorial se comprueba con controles reales en CoberturaTerritorialTest.
        CoberturaManchaUrbana c=lector.leer(documento);
        Ubicacion centro=punto("-21.5355", "-64.7296");
        Ubicacion sanLorenzo=punto("-21.418100", "-64.749400");
        assertTrue(centro.estaDentroDeManchaUrbana(c));
        assertFalse(sanLorenzo.estaDentroDeManchaUrbana(c));
        assertDoesNotThrow(() -> centro.validarParaReporte(c));
        assertEquals("UBICACION_FUERA_COBERTURA", assertThrows(
            UbicacionFueraCoberturaException.class,
            () -> sanLorenzo.validarParaReporte(c)).getCodigo());
        assertEquals(ResultadoUbicacionRuta.VALIDA, centro.validarParaRuta(c));
        assertEquals(ResultadoUbicacionRuta.PUNTO_INVALIDO, sanLorenzo.validarParaRuta(c));
        assertEquals(ResultadoUbicacionRuta.PUNTO_INVALIDO, new ValidadorPuntosRuta(c)
            .validar(centro, centro, java.util.List.of(sanLorenzo)));
    }
    @Test void aceptaMultipoligonosHuecosYBordes() throws Exception {
        var geo=mapper.readTree("""
            {"type":"MultiPolygon","coordinates":[
              [[[0,0],[4,0],[4,4],[0,4],[0,0]],[[1,1],[3,1],[3,3],[1,3],[1,1]]],
              [[[10,10],[12,10],[12,12],[10,12],[10,10]]] ]}
            """);
        var c=lector.leer(geo);
        assertEquals(2,c.cantidadPoligonos());
        assertTrue(c.contiene(.5,.5));
        assertFalse(c.contiene(2,2));
        assertTrue(c.contiene(2,1));
        assertTrue(c.contiene(11,11));
    }
    @Test void rechazaGeometriaAutocruzada() throws Exception {
        var geo=mapper.readTree("""
            {"type":"Polygon","coordinates":[[[0,0],[2,2],[0,2],[2,0],[0,0]]]}
            """);
        assertThrows(IllegalArgumentException.class, () -> lector.leer(geo));
    }
    // La comprobacion de TODOS los surtidores de V2 esta en MigracionesPostgisTest:
    // consulta las filas de ambas bases y no depende del formato de los INSERT.
    @Test void rechazaAnilloSinCierre() throws Exception {
        var geo=mapper.readTree("""
            {"type":"Polygon","coordinates":[[[0,0],[2,0],[2,2],[0,2]]]}
            """);
        assertThrows(IllegalArgumentException.class, () -> lector.leer(geo));
    }
    @Test void rechazaHuecoFueraDelContorno() throws Exception {
        var geo=mapper.readTree("""
            {"type":"Polygon","coordinates":[
              [[0,0],[4,0],[4,4],[0,4],[0,0]],
              [[5,5],[6,5],[6,6],[5,6],[5,5]]]}
            """);
        assertThrows(IllegalArgumentException.class, () -> lector.leer(geo));
    }
    @Test void rechazaHuecosSuperpuestos() throws Exception {
        var geo=mapper.readTree("""
            {"type":"Polygon","coordinates":[
              [[0,0],[6,0],[6,6],[0,6],[0,0]],
              [[1,1],[4,1],[4,4],[1,4],[1,1]],
              [[3,3],[5,3],[5,5],[3,5],[3,3]]]}
            """);
        assertThrows(IllegalArgumentException.class, () -> lector.leer(geo));
    }
    @Test void rechazaCoordenadasQueNoSonNumeros() throws Exception {
        var geo=mapper.readTree("""
            {"type":"Polygon","coordinates":[[["0",0],[2,0],[2,2],[0,0]]]}
            """);
        assertThrows(IllegalArgumentException.class, () -> lector.leer(geo));
    }
}
