package com.churoviaje.backend.anh;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Un servidor de ANH falso para las pruebas de CU-09, en 127.0.0.1 y con un puerto libre. Cada ruta responde lo que la
 * prueba le pida; si se le dan varias respuestas, las entrega en orden y repite la última. Una ruta sin respuesta da
 * 404. Las formas del JSON salen del informe técnico de ANH (T-115).
 */
public final class ServidorAnhFalso implements AutoCloseable {

    /** Una respuesta: estado HTTP, cuerpo y cabeceras. */
    public record Respuesta(int estado, String cuerpo, Map<String, String> cabeceras) {

        public static Respuesta json(String cuerpo) {
            return new Respuesta(200, cuerpo, Map.of("Content-Type", "application/json; charset=utf-8"));
        }

        public static Respuesta estado(int estado) {
            return new Respuesta(estado, "{}", Map.of("Content-Type", "application/json"));
        }

        public Respuesta conCabecera(String nombre, String valor) {
            Map<String, String> todas = new HashMap<>(cabeceras);
            todas.put(nombre, valor);
            return new Respuesta(estado, cuerpo, todas);
        }
    }

    private final HttpServer servidor;
    private final Map<String, Deque<Respuesta>> respuestas = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> pedidos = new ConcurrentHashMap<>();

    public ServidorAnhFalso() {
        try {
            servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        servidor.createContext("/", intercambio -> {
            URI uri = intercambio.getRequestURI();
            String clave = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
            pedidos.computeIfAbsent(clave, c -> new AtomicInteger()).incrementAndGet();
            Deque<Respuesta> cola = respuestas.get(clave);
            Respuesta r;
            synchronized (this) {
                r = cola == null || cola.isEmpty() ? Respuesta.estado(404)
                        : cola.size() > 1 ? cola.pollFirst() : cola.peekFirst();
            }
            byte[] cuerpo = r.cuerpo().getBytes(StandardCharsets.UTF_8);
            r.cabeceras().forEach((k, v) -> intercambio.getResponseHeaders().add(k, v));
            intercambio.sendResponseHeaders(r.estado(), cuerpo.length == 0 ? -1 : cuerpo.length);
            if (cuerpo.length > 0) {
                try (OutputStream salida = intercambio.getResponseBody()) {
                    salida.write(cuerpo);
                }
            }
            intercambio.close();
        });
        servidor.start();
    }

    /** La raíz de la API falsa, como {@code churoviaje.anh.base}. */
    public URI base() {
        return URI.create("http://127.0.0.1:" + servidor.getAddress().getPort() + "/api/");
    }

    /** Qué responde una ruta relativa a la raíz, por ejemplo {@code estaciones/?departamento=6&producto=0}. */
    public synchronized void responder(String ruta, Respuesta... enOrden) {
        respuestas.put("/api/" + ruta, new ArrayDeque<>(List.of(enOrden)));
    }

    /** Cuántas veces se pidió una ruta. */
    public int pedidos(String ruta) {
        AtomicInteger n = pedidos.get("/api/" + ruta);
        return n == null ? 0 : n.get();
    }

    public synchronized void reiniciar() {
        respuestas.clear();
        pedidos.clear();
    }

    @Override
    public void close() {
        servidor.stop(0);
    }

    // ------------------------------------------------------------- respuestas típicas de ANH

    /** El catálogo de departamentos con Tarija en el id 6. */
    public static String catalogo() {
        return """
                {"decCodigo": 1, "oResultado": [
                  {"id": 1, "nombre": "La Paz"}, {"id": 6, "nombre": "Tarija"}, {"id": 7, "nombre": "Santa Cruz"}]}""";
    }

    /** Un listado de estaciones con las filas dadas (cada una, un objeto JSON). */
    public static String listado(String... estaciones) {
        return "{\"decCodigo\": 1, \"oResultado\": [" + String.join(",", estaciones) + "]}";
    }

    /** Una estación con los campos que lee el backend y algunos que descarta (RN-09.18). */
    public static String estacion(int id, String nombre, double lat, double lng, int producto, String litros,
            String estado, String actualizado) {
        return """
                {"id": %d, "nombre": "%s", "departamento_id": 6, "direccion": "Calle falsa 123", "zona": "Centro",
                 "lat": %s, "lng": %s, "producto_id": %d, "producto_nombre": "%s", "saldo_litros": %s,
                 "saldo_estado": "%s", "con_venta": true, "fecha_ultima_venta": "2026-11-23T09:00:00",
                 "updated_at": "%s"}""".formatted(id, nombre, lat, lng, producto, producto == 0 ? "Gasolina" : "Diésel",
                litros, estado, actualizado);
    }
}
