import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;

/** T-117: medidor conservador, independiente de Maven, Java 17+. No almacena cuerpos de estaciones. */
public final class MedicionANH {
    private static final String ANH_BASE = "https://abastecimiento.prod.anh.gob.bo/api/";
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final String MEDICION_HEADER = "consulta_id,hora_utc,fin_utc,tipo,producto,intento,estado_http,tiempo_ms,n_estaciones,retry_after,error_codigo,updated_at_con_zona,updated_at_sin_zona,updated_at_ausente,updated_at_invalido";
    private static final String ESTACIONES_HEADER = "consulta_id,hora_utc,id_estacion,producto_id,saldo,updated_at,zona_en_updated_at";
    private final Config conf;
    private final HttpClient client;
    private final State state;
    private final Path medPath;
    private final Path stationPath;

    public static void main(String[] args) {
        try {
            Config conf = Config.read(args);
            Files.createDirectories(conf.dir);
            Path lockPath = conf.dir.resolve("medicion.lock");
            try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock lock = channel.tryLock()) {
                if (lock == null) throw new IllegalStateException("Otro medidor utiliza esta carpeta.");
                MedicionANH app = new MedicionANH(conf);
                if (conf.disclaimer) app.disclaimerUnaVez();
                else app.run();
            }
        } catch (Exception ex) {
            System.err.println("T-117: " + ex.getClass().getSimpleName() + ": " + safeError(ex));
            System.exit(2);
        }
    }

    private MedicionANH(Config conf) throws IOException {
        this.conf = conf;
        this.client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(conf.timeout)).build();
        this.state = new State(conf.dir.resolve("estado.properties"));
        this.medPath = conf.dir.resolve("medicion-" + conf.origin + ".csv");
        this.stationPath = conf.dir.resolve("estaciones-" + conf.origin + ".csv");
        if (!conf.disclaimer) {
            ensureCsv(medPath, MEDICION_HEADER);
            ensureCsv(stationPath, ESTACIONES_HEADER);
        }
    }

    private void run() throws Exception {
        if (state.get("inicio_utc").isEmpty()) {
            state.set("inicio_utc", Instant.now().toString());
            state.set("origen", conf.origin);
            state.set("duracion_horas", Integer.toString(conf.hours));
            state.set("base_url", conf.base.toString());
            state.set("pruebas", Boolean.toString(conf.mock));
            state.save();
        } else if (!state.get("origen").equals(conf.origin) || !state.get("duracion_horas").equals(Integer.toString(conf.hours))
                || !state.get("base_url").equals(conf.base.toString()) || !state.get("pruebas").equals(Boolean.toString(conf.mock))) {
            throw new IllegalArgumentException("No se permite reiniciar una misma carpeta con origen/duracion distintos.");
        }
        Instant end = Instant.parse(state.get("inicio_utc")).plus(Duration.ofHours(conf.hours));
        int ciclos = 0;
        System.out.println("T-117 " + conf.origin + ": inicio=" + state.get("inicio_utc") + " fin=" + end + " intervalo=" + conf.interval + "s; salida=" + conf.dir);
        while (Instant.now().isBefore(end)) {
            Instant next = max(parseInstant(state.get("siguiente_ciclo_utc")), parseInstant(state.get("bloqueo_hasta_utc")));
            if (next != null && !next.isBefore(end)) break;
            if (conf.once && next != null && Instant.now().isBefore(next)) {
                System.out.println("No se repite ciclo antes de " + next + ". Reanudación segura.");
                break;
            }
            if (next != null) sleepUntil(next);
            if (!Instant.now().isBefore(end)) break;
            Instant start = Instant.now();
            state.set("siguiente_ciclo_utc", start.plusSeconds(conf.interval).toString());
            state.save(); // reserva ANTES de consultar: relanzar no duplica ciclos
            try {
                cycle();
            } catch (PauseException ex) {
                System.err.println("Se respeta la pausa indicada por ANH hasta " + state.get("bloqueo_hasta_utc"));
            }
            ciclos++;
            if (conf.once) break;
        }
        System.out.println("T-117: terminada esta ejecución; ciclos=" + ciclos + ". Los datos de 72 h requieren las dos etapas reales.");
    }

    private void cycle() throws Exception {
        List<Map<String, Object>> departments;
        try {
            departments = fetch("departamentos/?producto=0", "catalogo", 0, -1);
        } catch (RequestFailure e) {
            if (e.blocked) throw new PauseException();
            if (e.tls) return; // Nunca intenta otro endpoint tras error TLS.
            try {
                departments = fetch("departamentos/?producto=1", "catalogo", 1, -1);
            } catch (RequestFailure fallback) {
                if (fallback.blocked) throw new PauseException();
                return;
            }
        }
        final int departmentId;
        try {
            departmentId = validateTarija(departments);
        } catch (ContractFailure ex) { return; }
        for (int product : new int[]{0, 1}) {
            try {
                fetch("estaciones/?departamento=" + departmentId + "&producto=" + product,
                        "estaciones", product, departmentId);
            } catch (RequestFailure failure) {
                if (failure.blocked) throw new PauseException();
                // Otro producto se consulta aunque este falle (RN-09.16).
            }
        }
    }

    /** Una petición real (o un reintento) deja un registro, incluso si falla. */
    private List<Map<String, Object>> fetch(String relative, String type, int product, int department) throws Exception {
        int retries = conf.retries;
        for (int attempt = 0; attempt <= retries; attempt++) {
            throttle();
            URI uri = conf.base.resolve(relative);
            assertOrigin(uri);
            String begin = Instant.now().toString();
            String id = UUID.randomUUID().toString();
            // Actualizar ANTES de abrir socket para evitar peticiones seguidas tras caída.
            state.set("ultima_peticion_utc", begin);
            state.save();
            long started = System.nanoTime();
            int status = 0;
            String retryAfter = "";
            String errorCode = "";
            List<Map<String, Object>> parsed = Collections.emptyList();
            List<Station> stations = Collections.emptyList();
            boolean retry = false, ssl = false, blocked = false;
            int[] timezoneCounts = {0, 0, 0, 0};
            try {
                HttpRequest request = HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(conf.timeout))
                        .header("Accept", "application/json")
                        .header("User-Agent", "ChuroViaje-T117-Medicion/1.0").build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream stream = response.body()) {
                    status = response.statusCode();
                    retryAfter = response.headers().firstValue("Retry-After").orElse("");
                    if (status == 200) {
                        String ct = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
                        if (!ct.contains("json")) throw new ContractFailure("content_type_no_json");
                        byte[] body = stream.readNBytes(MAX_BYTES + 1);
                        if (body.length > MAX_BYTES) throw new ContractFailure("respuesta_mayor_10mib");
                        String jsonText;
                        try {
                            jsonText = StandardCharsets.UTF_8.newDecoder()
                                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                                .decode(ByteBuffer.wrap(body)).toString();
                        } catch (java.nio.charset.CharacterCodingException ex) {
                            throw new ContractFailure("utf8_invalido");
                        }
                        Object json = new Json(jsonText).parse();
                        parsed = validateEnvelope(json);
                        if ("catalogo".equals(type)) validateTarija(parsed);
                        if ("estaciones".equals(type)) {
                            stations = validateStations(parsed, department, product);
                            for (Station row : stations) timezoneCounts[zoneIndex(row.updatedAt)]++;
                        }
                    } else {
                        errorCode = "HTTP_" + status;
                        retry = status == 429 || status == 502 || status == 503 || status == 504;
                        if (retry && !retryAfter.isBlank()) {
                            long cooldown = retrySeconds(retryAfter);
                            if (cooldown > 30) {
                                Instant until = Instant.now().plusSeconds(cooldown);
                                state.set("bloqueo_hasta_utc", max(until, parseInstant(state.get("bloqueo_hasta_utc"))).toString());
                                state.save();
                                blocked = true;
                            }
                        }
                    }
                }
            } catch (ContractFailure exception) {
                errorCode = exception.getMessage();
            } catch (SSLException exception) {
                errorCode = "ERROR_TLS";
                ssl = true;
            } catch (HttpTimeoutException exception) {
                errorCode = "TIMEOUT";
                retry = true;
            } catch (IOException exception) {
                errorCode = exception instanceof ConnectException ? "ERROR_CONEXION" : "ERROR_RED";
                retry = true;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                errorCode = "INTERRUMPIDO";
            }
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            String end = Instant.now().toString();
            state.set("ultima_peticion_fin_utc", end);
            state.save();
            if (!stations.isEmpty()) {
                for (Station station : stations) {
                    appendLine(stationPath, csv(id, begin, station.id, product, station.saldo,
                            station.updatedAt, zoneName(station.updatedAt)));
                }
            }
            appendLine(medPath, csv(id, begin, end, type, product, attempt + 1,
                    status == 0 ? "" : status, durationMs,
                    (status == 200 && "estaciones".equals(type) && errorCode.isEmpty()) ? stations.size() : "",
                    retryAfter, errorCode, timezoneCounts[0], timezoneCounts[1], timezoneCounts[2], timezoneCounts[3]));
            if (errorCode.isEmpty()) return parsed;
            System.err.println(begin + " " + type + " producto=" + product + " HTTP=" + status + " " + errorCode);
            if (blocked) throw new RequestFailure(true, false);
            if (!retry || attempt >= retries || ssl) throw new RequestFailure(false, ssl);
            long delay = Math.max(1, retrySeconds(retryAfter));
            if (delay > 30) { // resguardado por si ANH proporciona Retry-After no estándar
                state.set("bloqueo_hasta_utc", Instant.now().plusSeconds(delay).toString());
                state.save();
                throw new RequestFailure(true, false);
            }
            sleepUntil(Instant.now().plusSeconds(delay));
        }
        throw new RequestFailure(false, false);
    }

    private void throttle() throws Exception {
        Instant next = max(parseInstant(state.get("ultima_peticion_utc")),
                           parseInstant(state.get("ultima_peticion_fin_utc")));
        if (next != null) sleepUntil(next.plusMillis(1100));
        Instant cooldown = parseInstant(state.get("bloqueo_hasta_utc"));
        if (cooldown != null && Instant.now().isBefore(cooldown)) throw new PauseException();
    }

    private void disclaimerUnaVez() throws Exception {
        if (!state.get("disclaimer_intentado_utc").isEmpty()) {
            System.out.println("GET /api/Disclaimer ya se intentó en esta carpeta; no se repetirá.");
            return;
        }
        throttle();
        Instant now = Instant.now();
        state.set("disclaimer_intentado_utc", now.toString());
        state.set("ultima_peticion_utc", now.toString());
        state.save();
        Path target = conf.dir.resolve("disclaimer-evidencia-NO-COMMIT.txt");
        String message;
        try {
            URI uri = conf.base.resolve("Disclaimer");
            assertOrigin(uri);
            HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(conf.timeout)).GET()
                    .header("Accept", "application/json").build();
            HttpResponse<byte[]> response = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            // Evidencia solicitada por T-117, SEPARADA de los registros de estaciones.
            byte[] body = response.body();
            if (body.length > 1024 * 1024) throw new IOException("Disclaimer supera 1 MiB.");
            message = "hora_utc: " + now + "\nhttp_status: " + response.statusCode()
                    + "\ncontent_type: " + response.headers().firstValue("Content-Type").orElse("")
                    + "\nrespuesta_del_endpoint:\n" + new String(body, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            message = "hora_utc: " + now + "\nresultado: no_verificado\nerror: " + safeError(ex);
        }
        Files.writeString(target, message, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        System.out.println("Evidencia Disclaimer guardada fuera de Git: " + target);
    }

    private void assertOrigin(URI target) {
        URI base = conf.base;
        if (!target.getScheme().equalsIgnoreCase(base.getScheme()) ||
                !target.getHost().equalsIgnoreCase(base.getHost()) || target.getPort() != base.getPort() ||
                !target.getPath().startsWith(base.getPath())) throw new IllegalArgumentException("Origen de API no autorizado.");
    }

    private static List<Map<String, Object>> validateEnvelope(Object json) throws ContractFailure {
        Map<String, Object> root = object(json);
        Object code = root.get("decCodigo");
        if (!(code instanceof BigDecimal b) || b.compareTo(BigDecimal.ONE) != 0) throw new ContractFailure("decCodigo_no_exitoso");
        for (Map<String, Object> val : List.of(root)) rejectPagination(val);
        for (String key : new String[]{"pagination", "paginacion"}) {
            if (root.get(key) instanceof Map<?, ?> m) rejectPagination(object(m));
        }
        Object res = root.get("oResultado");
        if (!(res instanceof List<?> rows)) throw new ContractFailure("oResultado_no_lista");
        List<Map<String, Object>> parsed = new ArrayList<>();
        for (Object row : rows) parsed.add(object(row));
        return parsed;
    }

    private static void rejectPagination(Map<String, Object> obj) throws ContractFailure {
        for (String key : new String[]{"next", "next_page", "siguiente"}) {
            Object val = obj.get(key);
            if (val != null && !Boolean.FALSE.equals(val) && !"".equals(val) && !BigDecimal.ZERO.equals(val))
                throw new ContractFailure("paginacion_detectada");
        }
    }

    private static int validateTarija(List<Map<String, Object>> departments) throws ContractFailure {
        int matches = 0, stationId = -1;
        for (Map<String, Object> row : departments) {
            String name = string(row.get("nombre"));
            if ("tarija".equals(Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").strip().toLowerCase(Locale.ROOT))) {
                matches++;
                stationId = positiveId(row.get("id"));
            }
        }
        if (matches != 1) throw new ContractFailure("catalogo_tarija_ambiguo");
        return stationId;
    }

    private static List<Station> validateStations(List<Map<String, Object>> items, int department, int product) throws ContractFailure {
        List<Station> rows = new ArrayList<>();
        Set<Integer> ids = new HashSet<>();
        for (Map<String, Object> obj : items) {
            int id = positiveId(obj.get("id"));
            if (!ids.add(id)) throw new ContractFailure("estacion_duplicada");
            if (obj.get("departamento_id") != null && positiveId(obj.get("departamento_id")) != department)
                throw new ContractFailure("departamento_distinto");
            Map<String, Object> productData = obj;
            if (obj.containsKey("productos")) {
                if (!(obj.get("productos") instanceof List<?> all)) throw new ContractFailure("productos_malformados");
                int matches = 0;
                for (Object item : all) {
                    Map<String, Object> entry = object(item);
                    if (nonnegativeId(entry.get("producto_id")) == product) { productData = entry; matches++; }
                }
                if (matches != 1) throw new ContractFailure("producto_ambiguo");
            }
            if (productData.containsKey("producto_id") && productData.get("producto_id") != null &&
                    nonnegativeId(productData.get("producto_id")) != product) throw new ContractFailure("producto_distinto");
            String updated = string(productData.get("updated_at"));
            String saldo = amount(productData.get("saldo_litros"));
            rows.add(new Station(id, saldo, updated));
        }
        return rows;
    }

    private static String amount(Object val) {
        if (val == null || val instanceof Boolean) return "";
        try {
            BigDecimal parsed = new BigDecimal(val.toString().strip());
            return parsed.signum() < 0 ? "" : parsed.stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ex) {
            return "";
        }
    }

    private static int positiveId(Object val) throws ContractFailure {
        int id = nonnegativeId(val);
        if (id == 0) throw new ContractFailure("identificador_no_positivo");
        return id;
    }
    private static int nonnegativeId(Object val) throws ContractFailure {
        if (val instanceof Boolean || val == null) throw new ContractFailure("identificador_no_entero");
        String raw = val.toString().strip();
        if (!raw.matches("[0-9]+")) throw new ContractFailure("identificador_no_entero");
        try { return Integer.parseInt(raw); } catch (NumberFormatException ex) { throw new ContractFailure("identificador_fuera_rango"); }
    }
    private static String string(Object val) { return val instanceof String ? ((String) val).strip() : ""; }
    private static int zoneIndex(String value) { // 0 = con zona, 1 = sin zona, 2 = ausente, 3 = invalida
        if (value == null || value.isBlank()) return 2;
        try { OffsetDateTime.parse(value); return 0; } catch (DateTimeParseException ignored) { }
        try { LocalDateTime.parse(value); return 1; } catch (DateTimeParseException ignored) { }
        return 3;
    }
    private static String zoneName(String value) {
        return switch (zoneIndex(value)) { case 0 -> "SI"; case 1 -> "NO"; case 2 -> "AUSENTE"; default -> "INVALIDO"; };
    }

    private static long retrySeconds(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        try { return Math.max(0, Long.parseLong(raw.strip())); } catch (NumberFormatException ignored) { }
        try {
            Instant when = ZonedDateTime.parse(raw.strip(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            return Math.max(0, (long) Math.ceil(Duration.between(Instant.now(), when).toMillis() / 1000.0));
        } catch (DateTimeParseException ignored) { return 0; }
    }
    private static Instant parseInstant(String raw) { return raw == null || raw.isBlank() ? null : Instant.parse(raw); }
    private static Instant max(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }
    private static void sleepUntil(Instant next) throws InterruptedException {
        while (Instant.now().isBefore(next)) {
            long millis = Duration.between(Instant.now(), next).toMillis();
            if (millis <= 0) return;
            Thread.sleep(Math.min(5000, millis));
        }
    }
    private static String safeError(Exception ex) {
        return ex instanceof ContractFailure ? ex.getMessage() : ex.getClass().getSimpleName();
    }
    private static String csv(Object... values) {
        StringBuilder b = new StringBuilder();
        for (Object val : values) {
            if (!b.isEmpty()) b.append(',');
            String s = val == null ? "" : val.toString();
            // Protege la apertura de CSV con Excel contra inyección de fórmulas.
            if (!s.isBlank() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
            b.append('"').append(s.replace("\"", "\"\"")).append('"');
        }
        return b.append('\n').toString();
    }
    private static void ensureCsv(Path path, String header) throws IOException {
        if (!Files.exists(path)) Files.writeString(path, header + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
    private static void appendLine(Path path, String line) throws IOException {
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.APPEND, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
            ByteBuffer data = ByteBuffer.wrap(bytes);
            while (data.hasRemaining()) channel.write(data);
            channel.force(true);
        }
    }
    private record Station(int id, String saldo, String updatedAt) { }
    private static final class ContractFailure extends Exception { ContractFailure(String reason) { super(reason); } }
    private static final class RequestFailure extends Exception {
        final boolean blocked, tls;
        RequestFailure(boolean blocked, boolean tls) { this.blocked = blocked; this.tls = tls; }
    }
    private static final class PauseException extends Exception { }

    private static final class State {
        final Path path;
        final Properties props = new Properties();
        State(Path p) throws IOException {
            path = p;
            if (Files.exists(p)) try (var r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) { props.load(r); }
        }
        String get(String key) { return props.getProperty(key, ""); }
        void set(String key, String value) { props.setProperty(key, value); }
        void save() throws IOException {
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            try (var out = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) { props.store(out, "T-117, no incluir en Git"); }
            try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ex) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
        }
    }

    private static final class Config {
        URI base = URI.create(ANH_BASE);
        Path dir;
        String origin;
        int hours;
        int timeout = 12;
        int retries = 1;
        int interval = 600;
        boolean once, disclaimer, mock;
        static Config read(String[] argv) {
            Config cfg = new Config();
            Map<String, String> args = new HashMap<>();
            for (int i = 0; i < argv.length; i++) {
                String arg = argv[i];
                if (arg.equals("--una-vez") || arg.equals("--disclaimer") || arg.equals("--pruebas")) {
                    if (args.put(arg, "true") != null) throw new IllegalArgumentException("Opción repetida: " + arg);
                } else if (arg.startsWith("--") && i + 1 < argv.length && !argv[i + 1].startsWith("--")) {
                    if (args.put(arg, argv[++i]) != null) throw new IllegalArgumentException("Opción repetida: " + arg);
                } else throw new IllegalArgumentException("Argumento desconocido o sin valor: " + arg);
            }
            Set<String> supported = Set.of("--salida", "--origen", "--horas", "--timeout", "--reintentos", "--intervalo-segundos", "--una-vez", "--disclaimer", "--pruebas", "--base-url");
            for (String key : args.keySet()) if (!supported.contains(key)) throw new IllegalArgumentException("Opción desconocida: " + key);
            cfg.dir = Path.of(args.getOrDefault("--salida", "datos-medicion")).toAbsolutePath();
            cfg.origin = args.getOrDefault("--origen", "local");
            if (!cfg.origin.equals("local") && !cfg.origin.equals("oracle")) throw new IllegalArgumentException("origen debe ser local u oracle");
            cfg.hours = bounded(args.getOrDefault("--horas", cfg.origin.equals("local") ? "48" : "24"), 1, 72, "horas");
            cfg.timeout = bounded(args.getOrDefault("--timeout", "12"), 1, 120, "timeout");
            cfg.retries = bounded(args.getOrDefault("--reintentos", "1"), 0, 2, "reintentos");
            cfg.interval = bounded(args.getOrDefault("--intervalo-segundos", "600"), 1, 86400, "intervalo-segundos");
            cfg.mock = args.containsKey("--pruebas");
            cfg.once = args.containsKey("--una-vez");
            cfg.disclaimer = args.containsKey("--disclaimer");
            if (args.containsKey("--base-url")) cfg.base = URI.create(args.get("--base-url"));
            if (!cfg.base.toString().endsWith("/")) throw new IllegalArgumentException("base-url debe acabar en /");
            boolean real = cfg.base.equals(URI.create(ANH_BASE));
            boolean local = "http".equalsIgnoreCase(cfg.base.getScheme()) &&
                    Set.of("localhost", "127.0.0.1", "::1").contains(cfg.base.getHost());
            if (!((real && !cfg.mock) || (!real && cfg.mock && local)))
                throw new IllegalArgumentException("Solo se permite el origen HTTPS oficial, o HTTP loopback explícito para pruebas.");
            if (real && cfg.interval < 600) throw new IllegalArgumentException("Nunca reducir consulta a ANH por debajo de 600 s.");
            if (cfg.base.getRawQuery() != null || cfg.base.getFragment() != null) throw new IllegalArgumentException("base-url no admite query ni fragmento.");
            return cfg;
        }
        private static int bounded(String raw, int min, int max, String field) {
            try {
                int n = Integer.parseInt(raw);
                if (n < min || n > max) throw new NumberFormatException();
                return n;
            } catch (NumberFormatException e) { throw new IllegalArgumentException(field + " fuera del rango " + min + ".." + max); }
        }
    }

    /** JSON estricto y autónomo; no requiere Maven. Usa BigDecimal para impedir redondeos de IDs/saldos. */
    private static final class Json {
        final String input;
        int i;
        Json(String s) { input = s; }
        Object parse() throws ContractFailure {
            Object result = value();
            ws();
            if (i != input.length()) throw bad();
            return result;
        }
        void ws() { while (i < input.length() && " \r\n\t".indexOf(input.charAt(i)) >= 0) i++; }
        ContractFailure bad() { return new ContractFailure("json_invalido"); }
        boolean consume(char c) { ws(); if (i < input.length() && input.charAt(i) == c) { i++; return true; } return false; }
        Object value() throws ContractFailure {
            ws(); if (i >= input.length()) throw bad();
            char c = input.charAt(i);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return text();
            if (c == '-' || Character.isDigit(c)) return number();
            if (input.startsWith("null", i)) { i += 4; return null; }
            if (input.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (input.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            throw bad();
        }
        Map<String, Object> object() throws ContractFailure {
            if (!consume('{')) throw bad();
            Map<String, Object> m = new HashMap<>();
            if (consume('}')) return m;
            do {
                ws(); if (i >= input.length() || input.charAt(i) != '"') throw bad();
                String key = text();
                if (!consume(':') || m.containsKey(key)) throw bad();
                m.put(key, value());
                if (consume('}')) return m;
            } while (consume(','));
            throw bad();
        }
        List<Object> array() throws ContractFailure {
            if (!consume('[')) throw bad();
            List<Object> values = new ArrayList<>();
            if (consume(']')) return values;
            do {
                values.add(value());
                if (consume(']')) return values;
            } while (consume(','));
            throw bad();
        }
        String text() throws ContractFailure {
            if (!consume('"')) throw bad();
            StringBuilder out = new StringBuilder();
            while (i < input.length()) {
                char c = input.charAt(i++);
                if (c == '"') return out.toString();
                if (c == '\\') {
                    if (i >= input.length()) throw bad();
                    char e = input.charAt(i++);
                    switch (e) {
                        case '"', '\\', '/' -> out.append(e);
                        case 'b' -> out.append('\b'); case 'f' -> out.append('\f');
                        case 'n' -> out.append('\n'); case 'r' -> out.append('\r'); case 't' -> out.append('\t');
                        case 'u' -> {
                            if (i + 4 > input.length()) throw bad();
                            try { out.append((char) Integer.parseInt(input.substring(i, i + 4), 16)); }
                            catch (NumberFormatException x) { throw bad(); }
                            i += 4;
                        }
                        default -> throw bad();
                    }
                } else {
                    if (c < 32) throw bad();
                    out.append(c);
                }
            }
            throw bad();
        }
        BigDecimal number() throws ContractFailure {
            int start = i;
            if (input.charAt(i) == '-') i++;
            if (i >= input.length()) throw bad();
            if (input.charAt(i) == '0') i++;
            else if (input.charAt(i) >= '1' && input.charAt(i) <= '9') {
                while (i < input.length() && Character.isDigit(input.charAt(i))) i++;
            } else throw bad();
            if (i < input.length() && input.charAt(i) == '.') {
                i++;
                int p = i;
                while (i < input.length() && Character.isDigit(input.charAt(i))) i++;
                if (p == i) throw bad();
            }
            if (i < input.length() && (input.charAt(i) == 'e' || input.charAt(i) == 'E')) {
                i++;
                if (i < input.length() && (input.charAt(i) == '+' || input.charAt(i) == '-')) i++;
                int p = i;
                while (i < input.length() && Character.isDigit(input.charAt(i))) i++;
                if (p == i) throw bad();
            }
            try { return new BigDecimal(input.substring(start, i)); }
            catch (NumberFormatException e) { throw bad(); }
        }
    }
    private static Map<String, Object> object(Object val) throws ContractFailure {
        if (!(val instanceof Map<?, ?> m)) throw new ContractFailure("objeto_esperado");
        Map<String, Object> out = new HashMap<>();
        for (var entry : m.entrySet()) {
            if (!(entry.getKey() instanceof String)) throw new ContractFailure("clave_no_cadena");
            out.put((String) entry.getKey(), entry.getValue());
        }
        return out;
    }
}
