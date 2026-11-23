package com.churoviaje.backend.dominio.anh;

import com.churoviaje.backend.dominio.Ubicacion;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lo que ANH respondió en un listado de estaciones, ya validado (CU-09, RN-09.5 a RN-09.7, RN-09.12, RN-09.13). Una
 * respuesta que no cumple el contrato se descarta completa con {@link RespuestaDescartada}; un dato malo de una sola
 * estación solo anula ese dato. Aquí no se decide qué estaciones se toman (eso es RN-09.8 y RN-09.9).
 */
public record ReporteAnh(int departamento, int producto, List<LecturaAnh> lecturas) {

    /** RN-09.13: un {@code updated_at} sin zona horaria es hora de Tarija. */
    public static final ZoneId ZONA_TARIJA = ZoneId.of("America/La_Paz");

    /** RN-09.6: señales de que el listado viene por páginas y estaría incompleto. */
    private static final Set<String> PAGINACION = Set.of("next", "previous", "siguiente", "anterior", "page", "pages",
            "pagina", "paginas", "totalpages", "totalpaginas", "pagesize", "tamanopagina", "cursor", "offset", "limit",
            "hasmore", "haymas", "links", "nextpage", "paginasiguiente");

    private static final Set<String> ESTADOS_ANH = Set.of("ALTO", "MEDIO", "BAJO");

    /** {@code 2026-11-23T10:15:00}, con fracción de segundo opcional, o con un espacio en lugar de la T. */
    private static final DateTimeFormatter LOCAL = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd")
            .optionalStart().appendLiteral('T').optionalEnd()
            .optionalStart().appendLiteral(' ').optionalEnd()
            .appendPattern("HH:mm")
            .optionalStart().appendPattern(":ss").optionalEnd()
            .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true).optionalEnd()
            .toFormatter(Locale.ROOT);

    public ReporteAnh {
        lecturas = List.copyOf(lecturas);
    }

    /**
     * RN-09.2: el id de Tarija en el catálogo de departamentos. Tiene que haber exactamente una entrada con ese
     * nombre; si no, no se consulta y no se supone ningún id.
     */
    public static int idDeTarija(JsonNode catalogo) throws RespuestaDescartada {
        JsonNode filas = resultado(catalogo);
        Integer id = null;
        for (JsonNode fila : filas) {
            if ("tarija".equals(normalizar(texto(fila.get("nombre"))))) {
                if (id != null) {
                    throw new RespuestaDescartada("El catálogo de departamentos tiene más de un Tarija");
                }
                id = entero(fila.get("id"));
                if (id == null || id <= 0) {
                    throw new RespuestaDescartada("El id de Tarija en el catálogo no es un entero positivo");
                }
            }
        }
        if (id == null) {
            throw new RespuestaDescartada("El catálogo de departamentos no tiene a Tarija");
        }
        return id;
    }

    /**
     * Lee el listado de estaciones de un departamento y un combustible.
     *
     * @throws RespuestaDescartada si la respuesta no se puede usar (RN-09.5, RN-09.6); ningún estado cambia
     */
    public static ReporteAnh leer(JsonNode cuerpo, int departamento, int producto) throws RespuestaDescartada {
        JsonNode filas = resultado(cuerpo);
        List<LecturaAnh> lecturas = new ArrayList<>();
        Set<Integer> vistas = new HashSet<>();
        for (JsonNode estacion : filas) {
            Integer id = entero(estacion.get("id"));
            if (id == null || id <= 0) {
                throw new RespuestaDescartada("Una estación trae un identificador que no es un entero positivo");
            }
            if (!vistas.add(id)) {
                throw new RespuestaDescartada("La estación " + id + " viene repetida");
            }
            JsonNode departamentoEstacion = estacion.get("departamento_id");
            if (presente(departamentoEstacion)) {
                Integer dep = entero(departamentoEstacion);
                if (dep == null || dep != departamento) {
                    throw new RespuestaDescartada("La estación " + id + " es de otro departamento");
                }
            }
            JsonNode datos = datosDelProducto(estacion, id, producto);
            if (datos == null) {
                // Esa estación no trae el combustible pedido: no es cero litros, simplemente no hay dato.
                continue;
            }
            lecturas.add(new LecturaAnh(id, texto(estacion.get("nombre")), ubicacion(estacion),
                    litros(datos.get("saldo_litros")), estadoAnh(datos.get("saldo_estado")),
                    fecha(datos.get("updated_at"))));
        }
        return new ReporteAnh(departamento, producto, lecturas);
    }

    /** RN-09.5: un objeto con {@code decCodigo} igual a 1 y la lista en {@code oResultado}, sin paginación. */
    private static JsonNode resultado(JsonNode cuerpo) throws RespuestaDescartada {
        if (cuerpo == null || !cuerpo.isObject()) {
            throw new RespuestaDescartada("La respuesta no es un objeto JSON");
        }
        JsonNode codigo = cuerpo.get("decCodigo");
        if (codigo == null || !codigo.isIntegralNumber() || !codigo.canConvertToInt() || codigo.intValue() != 1) {
            throw new RespuestaDescartada("decCodigo no es 1: error de ANH, no una lista vacía");
        }
        for (Iterator<Map.Entry<String, JsonNode>> it = cuerpo.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> campo = it.next();
            if (PAGINACION.contains(normalizar(campo.getKey()).replace(" ", "")) && senal(campo.getValue())) {
                throw new RespuestaDescartada("La respuesta viene paginada (" + campo.getKey() + "): estaría incompleta");
            }
        }
        JsonNode filas = cuerpo.get("oResultado");
        if (filas == null || !filas.isArray()) {
            throw new RespuestaDescartada("oResultado no es una lista");
        }
        for (JsonNode fila : filas) {
            if (!fila.isObject()) {
                throw new RespuestaDescartada("oResultado tiene elementos que no son objetos");
            }
        }
        return filas;
    }

    /**
     * Los datos del combustible pedido: los de la estación o, si trae una lista {@code productos}, el único elemento
     * de ese combustible. Un registro de otro combustible descarta la respuesta (RN-09.6).
     */
    private static JsonNode datosDelProducto(JsonNode estacion, int id, int producto) throws RespuestaDescartada {
        JsonNode datos = estacion;
        JsonNode productos = estacion.get("productos");
        if (presente(productos)) {
            if (!productos.isArray()) {
                throw new RespuestaDescartada("La estación " + id + " trae productos que no son una lista");
            }
            JsonNode elegido = null;
            for (JsonNode p : productos) {
                if (!p.isObject()) {
                    throw new RespuestaDescartada("La estación " + id + " trae productos que no son objetos");
                }
                Integer codigo = entero(p.get("producto_id"));
                if (codigo == null) {
                    throw new RespuestaDescartada("La estación " + id + " trae un producto sin código entero");
                }
                if (codigo == producto) {
                    if (elegido != null) {
                        throw new RespuestaDescartada("La estación " + id + " trae dos veces el mismo combustible");
                    }
                    elegido = p;
                }
            }
            return elegido;
        }
        JsonNode codigo = datos.get("producto_id");
        if (presente(codigo)) {
            Integer valor = entero(codigo);
            if (valor == null || valor != producto) {
                throw new RespuestaDescartada("La estación " + id + " es de otro combustible que el pedido");
            }
        }
        return datos;
    }

    /** RN-09.7: un número válido y no negativo; si no, «sin saldo». */
    static BigDecimal litros(JsonNode valor) {
        if (!presente(valor) || valor.isBoolean()) {
            return null;
        }
        BigDecimal litros;
        if (valor.isNumber()) {
            if (valor.isFloatingPointNumber() && !Double.isFinite(valor.doubleValue())) {
                return null;
            }
            litros = valor.decimalValue();
        } else if (valor.isTextual() && valor.asText().trim().matches("\\d+(\\.\\d+)?")) {
            litros = new BigDecimal(valor.asText().trim());
        } else {
            return null;
        }
        return litros.signum() < 0 ? null : litros;
    }

    /** RN-09.12: el estado de ANH tal como llega, si es uno de los tres que se conocen. */
    static String estadoAnh(JsonNode valor) {
        String estado = normalizar(texto(valor)).toUpperCase(Locale.ROOT);
        return ESTADOS_ANH.contains(estado) ? estado : null;
    }

    /** RN-09.13: con zona, tal cual; sin zona, hora de Tarija; si falta o no se entiende, null. */
    static Instant fecha(JsonNode valor) {
        String texto = texto(valor);
        if (texto == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(texto).toInstant();
        } catch (DateTimeParseException conZona) {
            try {
                return LocalDateTime.parse(texto, LOCAL).atZone(ZONA_TARIJA).toInstant();
            } catch (DateTimeParseException sinZona) {
                return null;
            }
        }
    }

    private static Ubicacion ubicacion(JsonNode estacion) {
        Double lat = decimal(estacion.get("lat"));
        Double lng = decimal(estacion.get("lng"));
        if (lat == null || lng == null || Math.abs(lat) > 90 || Math.abs(lng) > 180 || (lat == 0 && lng == 0)) {
            return null;
        }
        return new Ubicacion(BigDecimal.valueOf(lat), BigDecimal.valueOf(lng));
    }

    private static Double decimal(JsonNode valor) {
        if (!presente(valor) || valor.isBoolean()) {
            return null;
        }
        if (valor.isNumber()) {
            double d = valor.doubleValue();
            return Double.isFinite(d) ? d : null;
        }
        if (valor.isTextual()) {
            try {
                double d = Double.parseDouble(valor.asText().trim());
                return Double.isFinite(d) ? d : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** Un entero: número entero de JSON o texto de solo dígitos, como lo acepta el prototipo. */
    static Integer entero(JsonNode valor) {
        if (!presente(valor)) {
            return null;
        }
        if (valor.isIntegralNumber() && valor.canConvertToInt()) {
            return valor.intValue();
        }
        if (valor.isTextual() && valor.asText().trim().matches("\\d{1,9}")) {
            return Integer.parseInt(valor.asText().trim());
        }
        return null;
    }

    private static boolean presente(JsonNode valor) {
        return valor != null && !valor.isNull() && !valor.isMissingNode();
    }

    /** Un campo de paginación cuenta si trae algo: null, false, 0 o vacío no son señales. */
    private static boolean senal(JsonNode valor) {
        if (!presente(valor)) {
            return false;
        }
        if (valor.isBoolean()) {
            return valor.booleanValue();
        }
        if (valor.isNumber()) {
            return valor.doubleValue() != 0;
        }
        if (valor.isTextual()) {
            return !valor.asText().isBlank();
        }
        return !valor.isEmpty();
    }

    private static String texto(JsonNode valor) {
        if (valor == null || !valor.isTextual() || valor.asText().isBlank()) {
            return null;
        }
        return valor.asText().trim();
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
