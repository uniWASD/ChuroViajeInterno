package com.churoviaje.backend.servicios;

import com.churoviaje.backend.dominio.Ubicacion;
import com.churoviaje.backend.dominio.busqueda.CategoriaLugar;
import com.churoviaje.backend.dominio.busqueda.Lugar;
import com.churoviaje.backend.dominio.busqueda.LugarRepository;
import com.churoviaje.backend.dominio.busqueda.TextoBusqueda;
import com.churoviaje.backend.dominio.cobertura.CoberturaManchaUrbana;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga de los lugares que se pueden buscar (D-47) desde el JSON de {@code preparar_lugares.py}. Solo reemplaza la
 * tabla si el extracto cambió, todo en una transacción: si algo falla, quedan los lugares de antes.
 */
@Service
public class LugarServicio {

    private static final Logger LOG = LoggerFactory.getLogger(LugarServicio.class);
    private static final Set<CategoriaLugar> DE_OSM = EnumSet.complementOf(EnumSet.of(CategoriaLugar.SURTIDOR));

    /** Lo que pasó con un archivo: si se cargó, cuántos lugares quedaron y cuántos se descartaron. */
    public record Carga(boolean cargado, LocalDate fechaExtracto, int guardados, int descartados) {}

    private final LugarRepository lugares;
    private final CoberturaManchaUrbana cobertura;
    private final ObjectMapper json;

    public LugarServicio(LugarRepository lugares, CoberturaManchaUrbana cobertura, ObjectMapper json) {
        this.lugares = lugares;
        this.cobertura = cobertura;
        this.json = json;
    }

    @Transactional
    public Carga cargar(Path archivo) throws IOException {
        if (!Files.isRegularFile(archivo)) {
            throw new IllegalStateException("No existe el archivo de lugares: " + archivo.toAbsolutePath());
        }
        JsonNode documento;
        try (InputStream entrada = Files.newInputStream(archivo)) {
            documento = json.readTree(entrada);
        }
        LocalDate fecha = fecha(documento, archivo);
        JsonNode elementos = documento.path("lugares");
        if (!elementos.isArray() || elementos.isEmpty()) {
            throw new IllegalStateException("El archivo de lugares no trae lugares: " + archivo);
        }
        Optional<LugarRepository.Extracto> cargado = lugares.extractoCargado();
        if (cargado.isPresent() && cargado.get().fecha().equals(fecha)) {
            return new Carga(false, fecha, cargado.get().cantidad(), 0);
        }
        List<Lugar> validos = new ArrayList<>();
        Set<String> claves = new HashSet<>();
        int descartados = 0;
        for (JsonNode elemento : elementos) {
            Lugar lugar = lugar(elemento);
            if (lugar == null || !claves.add(lugar.clave())) {
                descartados++;
            } else {
                validos.add(lugar);
            }
        }
        if (validos.isEmpty()) {
            throw new IllegalStateException("Ningún lugar del archivo es válido: " + archivo);
        }
        lugares.reemplazar(fecha, validos);
        LOG.info("Lugares de búsqueda cargados: {} del extracto {} ({} descartados).", validos.size(), fecha, descartados);
        return new Carga(true, fecha, validos.size(), descartados);
    }

    private static LocalDate fecha(JsonNode documento, Path archivo) {
        try {
            return LocalDate.parse(documento.path("fecha_extracto").asText());
        } catch (DateTimeParseException e) {
            throw new IllegalStateException("El archivo de lugares no trae una fecha_extracto válida: " + archivo, e);
        }
    }

    /** Un lugar del archivo, o nulo si le falta algo o está fuera de la mancha urbana. */
    private Lugar lugar(JsonNode elemento) {
        String clave = elemento.path("clave").asText("").trim();
        String nombre = elemento.path("nombre").asText("").trim();
        JsonNode lat = elemento.path("latitud");
        JsonNode lon = elemento.path("longitud");
        if (clave.isEmpty() || clave.length() > 60 || nombre.isEmpty() || nombre.length() > 200
                || TextoBusqueda.normalizar(nombre).isEmpty() || !lat.isNumber() || !lon.isNumber()) {
            return null;
        }
        CategoriaLugar categoria;
        try {
            categoria = CategoriaLugar.valueOf(elemento.path("categoria").asText(""));
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!DE_OSM.contains(categoria) || !cobertura.contiene(lat.doubleValue(), lon.doubleValue())) {
            return null;
        }
        return new Lugar(clave, nombre, categoria, new Ubicacion(lat.decimalValue(), lon.decimalValue()));
    }
}
