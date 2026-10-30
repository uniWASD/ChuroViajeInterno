package com.churoviaje.backend.config;

import com.churoviaje.backend.dominio.cobertura.CoberturaManchaUrbana;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration
public class ConfiguracionCobertura {
    @Bean
    public CoberturaManchaUrbana coberturaManchaUrbana(ObjectMapper mapper,
            @Value("${churoviaje.cobertura.ruta}") String ruta,
            @Value("${churoviaje.cobertura.permitir-provisional:false}") boolean permitirProvisional,
            Environment entorno) throws IOException {
        // acceptsProfiles considera tambien el perfil por defecto cuando no hay activos.
        boolean produccion = entorno.acceptsProfiles(Profiles.of("prod"));
        return cargarCobertura(mapper, ruta, permitirProvisional, produccion);
    }

    static CoberturaManchaUrbana cargarCobertura(ObjectMapper mapper, String ruta,
            boolean permitirProvisional, boolean produccion) throws IOException {
        // No basta con confiar en application-prod.yml: una variable de entorno
        // podría sobrescribir accidentalmente permitir-provisional. En prod no se permite.
        if (produccion && permitirProvisional) {
            throw new IllegalStateException("En prod está prohibido habilitar coberturas provisionales");
        }
        Path archivo = Path.of(ruta).toAbsolutePath().normalize();
        if (!Files.isRegularFile(archivo)) {
            throw new IllegalStateException("No existe el GeoJSON de cobertura: " + archivo);
        }
        JsonNode documento;
        try (var entrada = Files.newInputStream(archivo)) {
            documento = mapper.readTree(entrada);
        }
        if (documento == null || !documento.isObject()) {
            throw new IllegalArgumentException("El GeoJSON debe contener un objeto: " + archivo);
        }
        String estado = documento.path("estado_validacion").asText();
        // Aprobación conjunta: raíz y TODOS los polígonos (también MultiPolygon).
        // En desarrollo puede abrirse el borrador con permitir-provisional=true;
        // en producción NUNCA se interpreta PENDIENTE como APROBADO.
        if (!permitirProvisional && (!"APROBADO".equals(estado)
                || !todasLasGeometriasAprobadas(documento))) {
            throw new IllegalStateException("La mancha urbana requiere aprobación cartográfica integral: " + archivo);
        }
        return new LectorCoberturaGeoJson().leer(documento);
    }

    static boolean todasLasGeometriasAprobadas(JsonNode documento) {
        if (documento == null || !"FeatureCollection".equals(documento.path("type").asText())) {
            // Sin metadatos verificables para cada geometría: no aprobar en producción.
            return false;
        }
        JsonNode elementos = documento.path("features");
        if (!elementos.isArray() || elementos.isEmpty()) {
            return false;
        }
        for (JsonNode feature : elementos) {
            if (!"Feature".equals(feature.path("type").asText())
                    || !"APROBADO".equals(feature.path("properties").path("revision_equipo").asText())) {
                return false;
            }
        }
        return true;
    }
}
