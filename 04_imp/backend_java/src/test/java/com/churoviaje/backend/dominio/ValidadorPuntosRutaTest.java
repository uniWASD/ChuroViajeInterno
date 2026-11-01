package com.churoviaje.backend.dominio;

import com.churoviaje.backend.config.LectorCoberturaGeoJson;
import com.churoviaje.backend.dominio.cobertura.CoberturaManchaUrbana;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidadorPuntosRutaTest {
    private ValidadorPuntosRuta validador;
    private CoberturaManchaUrbana cobertura;
    private final Ubicacion centro = punto("-21.5355", "-64.7296");
    private final Ubicacion sanLorenzo = punto("-21.418100", "-64.749400");

    private static Ubicacion punto(String latitud, String longitud) {
        return new Ubicacion(new BigDecimal(latitud), new BigDecimal(longitud));
    }

    @BeforeEach void cargarArchivoEntregado() throws Exception {
        cobertura = new LectorCoberturaGeoJson().leer(new ObjectMapper()
                .readTree(Path.of("../datos/mancha_urbana_tarija.geojson").toFile()));
        validador = new ValidadorPuntosRuta(cobertura);
    }

    @Test void aceptaRutaConPuntosDentro() {
        assertEquals(ResultadoUbicacionRuta.VALIDA,
                validador.validar(centro, centro, List.of(centro, centro)));
    }

    @Test void origenExteriorEsResultadoYNoExcepcionDeReporte() {
        assertEquals(ResultadoUbicacionRuta.PUNTO_INVALIDO,
                assertDoesNotThrow(() -> validador.validar(sanLorenzo, centro, List.of())));
    }

    @Test void destinoExteriorEsResultado() {
        assertEquals(ResultadoUbicacionRuta.PUNTO_INVALIDO,
                validador.validar(centro, sanLorenzo, List.of()));
    }

    @Test void revisaTambienLaUltimaParada() {
        assertEquals(ResultadoUbicacionRuta.PUNTO_INVALIDO,
                validador.validar(centro, centro, List.of(centro, sanLorenzo)));
    }

    @Test void reporteExteriorConservaSuExcepcionDeDominio() {
        var rechazo = assertThrows(UbicacionFueraCoberturaException.class,
                () -> sanLorenzo.validarParaReporte(cobertura));
        assertEquals("UBICACION_FUERA_COBERTURA", rechazo.getCodigo());
    }
}
