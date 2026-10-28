package com.churoviaje.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class ConfiguracionCoberturaTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @TempDir Path temporal;

    // La geometria procede siempre del archivo entregado. Solo se cambian
    // metadatos en una copia temporal, sin aprobar ni modificar el archivo real.
    private ObjectNode documento(String estado, String revision) throws Exception {
        var doc = (ObjectNode) mapper.readTree(Path.of("../datos/mancha_urbana_tarija.geojson").toFile());
        doc.put("estado_validacion", estado);
        for (var feature : doc.path("features")) {
            ((ObjectNode) feature.path("properties")).put("revision_equipo", revision);
        }
        return doc;
    }

    private String guardar(ObjectNode doc) throws Exception {
        Path archivo = temporal.resolve("cobertura.geojson");
        mapper.writeValue(archivo.toFile(), doc);
        return archivo.toString();
    }

    @Test void borradorSoloConPermisoExplicito() throws Exception {
        String ruta = guardar(documento("PROVISIONAL", "PENDIENTE"));
        assertTrue(ConfiguracionCobertura.cargarCobertura(mapper, ruta, true, false)
                .contiene(-21.5355, -64.7296));
        assertThrows(IllegalStateException.class,
                () -> ConfiguracionCobertura.cargarCobertura(mapper, ruta, false, false));
    }

    @Test void produccionNoPermiteHabilitarBorradores() throws Exception {
        String ruta = guardar(documento("PROVISIONAL", "PENDIENTE"));
        assertAll(
            () -> assertThrows(IllegalStateException.class,
                () -> ConfiguracionCobertura.cargarCobertura(mapper, ruta, true, true)),
            () -> assertThrows(IllegalStateException.class,
                () -> ConfiguracionCobertura.cargarCobertura(mapper, ruta, false, true))
        );
    }

    @Test void produccionAceptaLaAprobacionCompleta() throws Exception {
        String ruta = Path.of("../datos/mancha_urbana_tarija.geojson").toString();
        assertTrue(ConfiguracionCobertura.cargarCobertura(mapper, ruta, false, true)
                .contiene(-21.5355, -64.7296));
    }

    @Test void aprobacionDeFeaturesNoSustituyeLaDeRaiz() throws Exception {
        String ruta = guardar(documento("PROVISIONAL", "APROBADO"));
        assertThrows(IllegalStateException.class,
                () -> ConfiguracionCobertura.cargarCobertura(mapper, ruta, false, true));
    }

    @Test void exigeRevisionDeTodasLasFeatures() throws Exception {
        ObjectNode doc = documento("APROBADO", "APROBADO");
        var segunda = doc.path("features").get(0).deepCopy();
        ((ObjectNode) segunda.path("properties")).put("revision_equipo", "PENDIENTE");
        ((com.fasterxml.jackson.databind.node.ArrayNode) doc.path("features")).add(segunda);
        String ruta = guardar(doc);
        assertThrows(IllegalStateException.class,
                () -> ConfiguracionCobertura.cargarCobertura(mapper, ruta, false, true));
    }

    @Test void perfilProdPorDefectoTambienBloqueaBorradores() throws Exception {
        String ruta = guardar(documento("PROVISIONAL", "PENDIENTE"));
        var entorno = new MockEnvironment();
        entorno.setDefaultProfiles("prod");
        assertThrows(IllegalStateException.class,
                () -> new ConfiguracionCobertura().coberturaManchaUrbana(mapper, ruta, true, entorno));
    }

    @Test void perfilProdActivoBloqueaAunqueTambienEsteDev() throws Exception {
        String ruta = guardar(documento("PROVISIONAL", "PENDIENTE"));
        var entorno = new MockEnvironment().withProperty("spring.profiles.active", "dev,prod");
        entorno.setActiveProfiles("dev", "prod");
        assertThrows(IllegalStateException.class,
                () -> new ConfiguracionCobertura().coberturaManchaUrbana(mapper, ruta, true, entorno));
    }

    @Test void archivoVacioSeRechazaConUnErrorDeDatos() throws Exception {
        Path vacio = temporal.resolve("vacio.geojson");
        Files.writeString(vacio, "");
        assertThrows(IllegalArgumentException.class,
                () -> ConfiguracionCobertura.cargarCobertura(mapper, vacio.toString(), true, false));
    }

    @Test void archivoInexistenteSeRechazaAlArrancar() {
        assertThrows(IllegalStateException.class,
                () -> ConfiguracionCobertura.cargarCobertura(mapper,
                        temporal.resolve("ausente.geojson").toString(), true, false));
    }
}
