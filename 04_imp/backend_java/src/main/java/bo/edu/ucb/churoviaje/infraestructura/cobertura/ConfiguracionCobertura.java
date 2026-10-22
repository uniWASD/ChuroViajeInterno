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
        if (!permitirProvisional && !"APROBADO".equals(estado)) {
            throw new IllegalStateException("La mancha urbana requiere aprobación cartográfica: " + archivo);
        }
        return new LectorCoberturaGeoJson().leer(documento);
    }
}
