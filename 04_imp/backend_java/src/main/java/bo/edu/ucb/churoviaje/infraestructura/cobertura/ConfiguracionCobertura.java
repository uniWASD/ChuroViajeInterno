package bo.edu.ucb.churoviaje.infraestructura.cobertura;

import bo.edu.ucb.churoviaje.dominio.ubicacion.CoberturaManchaUrbana;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracionCobertura {
    @Bean
    public CoberturaManchaUrbana coberturaManchaUrbana(ObjectMapper mapper,
            @Value("${churoviaje.cobertura.ruta}") String ruta,
            @Value("${churoviaje.cobertura.permitir-provisional:false}") boolean permitirProvisional)
            throws IOException {
        Path archivo = Path.of(ruta).toAbsolutePath().normalize();
        if (!Files.isRegularFile(archivo)) {
            throw new IllegalStateException("No existe el GeoJSON de cobertura: " + archivo);
        }
        JsonNode documento;
        try (var entrada = Files.newInputStream(archivo)) {
            documento = mapper.readTree(entrada);
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
        if (!"FeatureCollection".equals(documento.path("type").asText())) {
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
