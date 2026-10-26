package bo.edu.ucb.churoviaje.dominio.ubicacion;

import bo.edu.ucb.churoviaje.infraestructura.cobertura.LectorCoberturaGeoJson;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoberturaGeoJsonTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final LectorCoberturaGeoJson lector = new LectorCoberturaGeoJson();

    @Test
    void cp22_plazaDentroSanLorenzoFuera() throws Exception {
        // CP-22 · CU-01/CU-03 · RN-01.6 / RN-03.3. Geometría provisional de prueba, NO definitiva.
        var documento = mapper.readTree(getClass().getResourceAsStream(
                "/fixtures/mancha_urbana_tarija_provisional.geojson"));
        CoberturaManchaUrbana cobertura = lector.leer(documento);
        Ubicacion centroTarija = new Ubicacion(-21.5355, -64.7296, OrigenUbicacion.GPS);
        Ubicacion sanLorenzo = new Ubicacion(-21.418100, -64.749400, OrigenUbicacion.AJUSTE_MANUAL);
        assertTrue(centroTarija.estaDentroDeManchaUrbana(cobertura));
        assertFalse(sanLorenzo.estaDentroDeManchaUrbana(cobertura));
        assertDoesNotThrow(() -> centroTarija.validarParaReporte(cobertura));
        assertEquals("UBICACION_FUERA_COBERTURA", assertThrows(
                UbicacionFueraCoberturaException.class,
                () -> sanLorenzo.validarParaReporte(cobertura)).getCodigo());
        assertEquals(ResultadoUbicacionRuta.VALIDA, centroTarija.validarParaRuta(cobertura));
        assertEquals(ResultadoUbicacionRuta.PUNTO_INVALIDO, sanLorenzo.validarParaRuta(cobertura));
        assertEquals("Origen o destino inválido",
                sanLorenzo.validarParaRuta(cobertura).mensajeContrato());
        ValidadorPuntosRuta validador = new ValidadorPuntosRuta(cobertura);
        assertEquals(ResultadoUbicacionRuta.PUNTO_INVALIDO,
                validador.validar(centroTarija, centroTarija, java.util.List.of(sanLorenzo)));
    }

    @Test
    void reconoceMultiplesPoligonosYHuecos() throws Exception {
        var geometria = mapper.readTree("""
                {"type":"MultiPolygon","coordinates":[
                   [[[0,0],[4,0],[4,4],[0,4],[0,0]],[[1,1],[3,1],[3,3],[1,3],[1,1]]],
                   [[[10,10],[12,10],[12,12],[10,12],[10,10]]]
                ]}
                """);
        CoberturaManchaUrbana cobertura = lector.leer(geometria);
        assertEquals(2, cobertura.cantidadPoligonos());
        assertTrue(cobertura.contiene(0.5, 0.5));
        assertFalse(cobertura.contiene(2, 2));
        assertTrue(cobertura.contiene(2, 1));
        assertTrue(cobertura.contiene(11, 11));
        assertFalse(cobertura.contiene(5, 5));
    }

    @Test
    void incluyeTodasLasGeometriasDeUnaColeccion() throws Exception {
        var documento = mapper.readTree("""
                {"type":"FeatureCollection","features":[
                    {"type":"Feature","geometry":{"type":"Polygon","coordinates":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]}},
                    {"type":"Feature","geometry":{"type":"Polygon","coordinates":[[[10,10],[11,10],[11,11],[10,11],[10,10]]]}}
                ]}
                """);
        var cobertura = lector.leer(documento);
        assertEquals(2, cobertura.cantidadPoligonos());
        assertTrue(cobertura.contiene(10.5, 10.5));
    }

    @Test
    void rechazaGeometriasInvalidasYCoordenadasIncorrectas() throws Exception {
        String[] invalidos = {
                """
                {"type":"FeatureCollection","features":[]}
                """,
                """
                {"type":"Polygon","coordinates":[[[0,0],[1,0],[1,1]]]}
                """,
                """
                {"type":"Polygon","coordinates":[[[0,0],[1,0],[1,1],[0,0,7],[0,0]]]}
                """,
                """
                {"type":"Polygon","coordinates":[[[0,0],[2,2],[0,2],[2,0],[0,0]]]}
                """,
                """
                {"type":"Polygon","coordinates":[[[181,0],[182,0],[182,1],[181,1],[181,0]]]}
                """,
                """
                {"type":"LineString","coordinates":[[0,0],[1,1]]}
                """
        };
        for (String invalido : invalidos) {
            assertThrows(IllegalArgumentException.class, () -> lector.leer(mapper.readTree(invalido)));
        }
    }
}
