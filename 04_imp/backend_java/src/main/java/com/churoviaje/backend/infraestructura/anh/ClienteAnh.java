package com.churoviaje.backend.infraestructura.anh;

import com.churoviaje.backend.config.PropiedadesAnh;
import com.churoviaje.backend.infraestructura.anh.FalloAnh.Motivo;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.math.BigInteger;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * El cliente de la API de ANH (CU-09). Solo hace {@code GET} por HTTPS, sin credenciales ni datos de personas, con el
 * certificado y el nombre del servidor verificados (RN-09.3). Espera, reintenta y respeta {@code Retry-After} como dice
 * RN-09.4, y solo entrega una respuesta 200, JSON y de hasta 10 MiB (RN-09.5). No guarda ni registra el cuerpo.
 */
@Component
public class ClienteAnh {

    /** RN-09.5. */
    public static final int TAMANO_MAXIMO = 10 * 1024 * 1024;
    /** RN-09.4: si ANH pide esperar más que esto, no se reintenta y se deja de consultar hasta entonces. */
    public static final Duration RETRY_AFTER_MAXIMO = Duration.ofSeconds(30);
    private static final Set<Integer> REINTENTABLES = Set.of(429, 502, 503, 504);
    private static final int REDIRECCIONES_MAXIMAS = 3;
    private static final String AGENTE = "ChuroViaje-backend/1.0 (consulta de solo lectura; CU-09)";

    /** Cómo se espera entre peticiones. Las pruebas la reemplazan para no dormir de verdad. */
    @FunctionalInterface
    public interface Espera {
        void esperar(Duration duracion) throws InterruptedException;
    }

    /** Una respuesta aceptada: el JSON y cuántos intentos hicieron falta. */
    public record Respuesta(String ruta, int estadoHttp, int intentos, JsonNode cuerpo) {}

    private final PropiedadesAnh propiedades;
    private final ObjectMapper json;
    private final Clock reloj;
    private final HttpClient http;
    private final Espera espera;
    private Instant finUltimaPeticion = Instant.EPOCH;

    @Autowired
    public ClienteAnh(PropiedadesAnh propiedades, ObjectMapper json, Clock reloj) {
        this(propiedades, json, reloj, duracion -> Thread.sleep(duracion.toMillis()));
    }

    public ClienteAnh(PropiedadesAnh propiedades, ObjectMapper json, Clock reloj, Espera espera) {
        this.propiedades = Objects.requireNonNull(propiedades);
        this.json = json.copy()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        // RN-09.12: ANH puede entregar decimales con más de dos posiciones; no convertirlos primero a double.
        this.json.setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
        this.reloj = reloj;
        this.espera = espera;
        // Las redirecciones las sigue este cliente, para comprobar cada destino (RN-09.3). La verificación del
        // certificado y del nombre del servidor es la de Java y no se toca.
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(propiedades.esperaPeticion())
                .sslParameters(parametrosTls())
                .build();
    }

    /**
     * Pide una ruta relativa a la raíz de ANH, por ejemplo {@code estaciones/?departamento=6&producto=0}.
     *
     * @throws FalloAnh si no hubo una respuesta utilizable después de los reintentos
     */
    public synchronized Respuesta get(String ruta) throws FalloAnh, InterruptedException {
        if (ruta.startsWith("/") || ruta.contains("://") || ruta.contains("..")) {
            throw new IllegalArgumentException("Ruta relativa inválida: " + ruta);
        }
        int intentos = 0;
        Duration esperaPedida = Duration.ZERO;
        while (true) {
            intentos++;
            esperarTurno(esperaPedida);
            try {
                return pedir(ruta, intentos);
            } catch (Reintentable r) {
                Instant ahora = reloj.instant();
                Duration retryAfter = r.retryAfter(ahora);
                if (retryAfter != null && retryAfter.compareTo(RETRY_AFTER_MAXIMO) > 0) {
                    Instant reanudar;
                    try {
                        reanudar = ahora.plus(retryAfter);
                    } catch (DateTimeException | ArithmeticException fueraDeRango) {
                        // Un Retry-After enorme no debe convertirse en una nueva consulta prematura.
                        reanudar = Instant.MAX;
                    }
                    throw new FalloAnh(ruta, r.motivo, r.estadoHttp, intentos, reanudar,
                            "ANH pidió esperar " + retryAfter.toSeconds() + " s");
                }
                if (intentos > propiedades.reintentos()) {
                    throw new FalloAnh(ruta, r.motivo, r.estadoHttp, intentos, null, r.getMessage());
                }
                esperaPedida = retryAfter == null ? Duration.ZERO : retryAfter;
            }
        }
    }

    /** RN-09.4: la pausa entre peticiones, y como mínimo lo que pidió {@code Retry-After}. */
    private void esperarTurno(Duration esperaPedida) throws InterruptedException {
        Duration desdeLaUltima = Duration.between(finUltimaPeticion, reloj.instant());
        if (desdeLaUltima.isNegative()) {
            // El reloj retrocedió (por ejemplo, al ajustarse la hora): se cuenta como recién terminada.
            desdeLaUltima = Duration.ZERO;
        }
        Duration falta = propiedades.pausa().minus(desdeLaUltima);
        if (esperaPedida.compareTo(falta) > 0) {
            falta = esperaPedida;
        }
        if (falta.isPositive()) {
            espera.esperar(falta);
        }
    }

    private Respuesta pedir(String ruta, int intentos) throws FalloAnh, Reintentable, InterruptedException {
        URI base = propiedades.base();
        URI destino = base.resolve(ruta);
        try {
            for (int redirecciones = 0; ; redirecciones++) {
                HttpResponse<InputStream> respuesta = enviar(destino);
                int estado = respuesta.statusCode();
                if (estado >= 300 && estado < 400 && estado != 304) {
                    cerrar(respuesta);
                    URI siguiente = respuesta.headers().firstValue("Location").map(destino::resolve).orElse(null);
                    if (siguiente == null || !redireccionPermitida(base, siguiente)
                            || redirecciones >= REDIRECCIONES_MAXIMAS) {
                        throw new FalloAnh(ruta, Motivo.REDIRECCION_NO_PERMITIDA, estado, intentos, null,
                                "Redirección detenida hacia " + (siguiente == null ? "ninguna parte" : siguiente.getHost()));
                    }
                    destino = siguiente;
                    continue;
                }
                if (REINTENTABLES.contains(estado)) {
                    String retryAfter = respuesta.headers().firstValue("Retry-After").orElse(null);
                    cerrar(respuesta);
                    throw new Reintentable(Motivo.HTTP, estado, retryAfter, "HTTP " + estado);
                }
                if (estado != 200) {
                    cerrar(respuesta);
                    throw new FalloAnh(ruta, Motivo.HTTP, estado, intentos, null, "HTTP " + estado);
                }
                String tipo = respuesta.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
                if (!tipo.contains("json")) {
                    cerrar(respuesta);
                    throw new FalloAnh(ruta, Motivo.NO_ES_JSON, estado, intentos, null, "Content-Type " + tipo);
                }
                byte[] cuerpo = leer(respuesta, ruta, intentos);
                try {
                    return new Respuesta(ruta, estado, intentos, json.readTree(cuerpo));
                } catch (IOException e) {
                    throw new FalloAnh(ruta, Motivo.NO_ES_JSON, estado, intentos, null, "El cuerpo no es JSON válido");
                }
            }
        } catch (HttpTimeoutException e) {
            throw new Reintentable(Motivo.TIEMPO_AGOTADO, null, null, "Tiempo agotado");
        } catch (SSLException e) {
            // Un certificado inválido no se arregla repitiendo la petición, y nunca se ignora (RN-09.3).
            throw new FalloAnh(ruta, Motivo.CERTIFICADO, null, intentos, null, "Certificado no válido: " + e.getMessage());
        } catch (ConnectException e) {
            throw new Reintentable(Motivo.SIN_CONEXION, null, null, "Sin conexión");
        } catch (IOException e) {
            if (e.getCause() instanceof SSLException ssl) {
                throw new FalloAnh(ruta, Motivo.CERTIFICADO, null, intentos, null, "Certificado no válido: " + ssl.getMessage());
            }
            throw new Reintentable(Motivo.SIN_CONEXION, null, null, "Sin conexión: " + e.getClass().getSimpleName());
        } finally {
            finUltimaPeticion = reloj.instant();
        }
    }

    /** RN-09.3: TLS 1.2 como mínimo y verificación explícita del nombre del servidor. */
    static SSLParameters parametrosTls() {
        SSLParameters tls = new SSLParameters();
        tls.setProtocols(new String[] {"TLSv1.3", "TLSv1.2"});
        tls.setEndpointIdentificationAlgorithm("HTTPS");
        return tls;
    }

    private HttpResponse<InputStream> enviar(URI destino) throws IOException, InterruptedException {
        HttpRequest peticion = HttpRequest.newBuilder(destino)
                .GET()
                .timeout(propiedades.esperaPeticion())
                .header("Accept", "application/json")
                .header("User-Agent", AGENTE)
                .build();
        return http.send(peticion, HttpResponse.BodyHandlers.ofInputStream());
    }

    /** RN-09.5: lee como mucho 10 MiB; si hay más, la respuesta se descarta entera. */
    private static byte[] leer(HttpResponse<InputStream> respuesta, String ruta, int intentos)
            throws IOException, FalloAnh {
        try (InputStream entrada = respuesta.body()) {
            byte[] cuerpo = entrada.readNBytes(TAMANO_MAXIMO + 1);
            if (cuerpo.length > TAMANO_MAXIMO) {
                throw new FalloAnh(ruta, Motivo.DEMASIADO_GRANDE, respuesta.statusCode(), intentos, null,
                        "La respuesta pasa de 10 MiB");
            }
            return cuerpo;
        }
    }

    private static void cerrar(HttpResponse<InputStream> respuesta) {
        try {
            respuesta.body().close();
        } catch (IOException ignorada) {
            // La conexión se descarta igual.
        }
    }

    /** RN-09.3: una redirección solo sigue si va al mismo servidor de ANH, con el mismo esquema y puerto. */
    static boolean redireccionPermitida(URI base, URI destino) {
        return destino.getScheme() != null && destino.getScheme().equalsIgnoreCase(base.getScheme())
                && destino.getHost() != null && destino.getHost().equalsIgnoreCase(base.getHost())
                && destino.getPort() == base.getPort() && destino.getUserInfo() == null;
    }

    /** {@code Retry-After} en segundos o como fecha HTTP; null si no vino o no se entiende. */
    static Duration leerRetryAfter(String valor, Instant ahora) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String texto = valor.trim();
        if (texto.matches("\\d+")) {
            try {
                return Duration.ofSeconds(new BigInteger(texto).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue());
            } catch (NumberFormatException fueraDeRango) {
                return Duration.ofSeconds(Long.MAX_VALUE);
            }
        }
        try {
            Instant hasta = ZonedDateTime.parse(texto, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            Duration falta = Duration.between(ahora, hasta);
            return falta.isNegative() ? Duration.ZERO : falta;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Un fallo que, según RN-09.4, se puede reintentar. */
    private static final class Reintentable extends Exception {
        private final Motivo motivo;
        private final Integer estadoHttp;
        private final String retryAfter;

        Reintentable(Motivo motivo, Integer estadoHttp, String retryAfter, String detalle) {
            super(detalle, null, false, false);
            this.motivo = motivo;
            this.estadoHttp = estadoHttp;
            this.retryAfter = retryAfter;
        }

        Duration retryAfter(Instant ahora) {
            return leerRetryAfter(retryAfter, ahora);
        }
    }
}
