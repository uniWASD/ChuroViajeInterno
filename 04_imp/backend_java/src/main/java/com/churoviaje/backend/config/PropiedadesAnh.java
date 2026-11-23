package com.churoviaje.backend.config;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * La consulta a ANH (CU-09). Los valores de partida son los que propone T-117 (#112, PR #123): consultar cada 10
 * minutos (P-29), 12 segundos de espera por petición, 1 reintento y 1 segundo entre peticiones (P-32).
 *
 * @param habilitada     si la tarea periódica consulta a ANH. Apagada en el ambiente de pruebas, para no duplicar las
 *                       consultas de producción
 * @param base           raíz de la API de ANH. Siempre HTTPS (RN-09.3); HTTP solo hacia la propia máquina, para el
 *                       servidor falso de las pruebas
 * @param ciclo          cada cuánto se consulta (P-29)
 * @param esperaInicial  cuánto espera la primera consulta después de arrancar el backend
 * @param esperaPeticion tiempo máximo de cada petición (P-32)
 * @param reintentos     cuántas veces se repite una petición que falló por falta de conexión, tiempo agotado o 429,
 *                       502, 503 o 504 (P-32, RN-09.4)
 * @param pausa          pausa mínima entre una petición y la siguiente (P-32, RN-09.4)
 */
@ConfigurationProperties(prefix = "churoviaje.anh")
public record PropiedadesAnh(boolean habilitada, URI base, Duration ciclo, Duration esperaInicial,
        Duration esperaPeticion, int reintentos, Duration pausa) {

    public PropiedadesAnh {
        Objects.requireNonNull(base, "churoviaje.anh.base");
        Objects.requireNonNull(ciclo, "churoviaje.anh.ciclo");
        Objects.requireNonNull(esperaInicial, "churoviaje.anh.espera-inicial");
        Objects.requireNonNull(esperaPeticion, "churoviaje.anh.espera-peticion");
        Objects.requireNonNull(pausa, "churoviaje.anh.pausa");
        if (!base.toString().endsWith("/")) {
            base = URI.create(base + "/");
        }
        if (!"https".equalsIgnoreCase(base.getScheme()) && !haciaEstaMaquina(base)) {
            throw new IllegalStateException("La API de ANH se consulta solo por HTTPS (RN-09.3): " + base);
        }
        if (base.getHost() == null) {
            throw new IllegalStateException("churoviaje.anh.base no tiene servidor: " + base);
        }
        // RN-09.1 y T-117: no se carga a ANH más de una vez cada 10 minutos sin justificarlo por escrito.
        if (ciclo.compareTo(Duration.ofMinutes(10)) < 0) {
            throw new IllegalStateException("churoviaje.anh.ciclo no puede ser menor que 10 minutos (P-29)");
        }
        if (esperaPeticion.isNegative() || esperaPeticion.isZero()) {
            throw new IllegalStateException("churoviaje.anh.espera-peticion debe ser mayor que cero");
        }
        if (reintentos < 0 || reintentos > 3) {
            throw new IllegalStateException("churoviaje.anh.reintentos debe estar entre 0 y 3");
        }
        if (pausa.isNegative() || esperaInicial.isNegative()) {
            throw new IllegalStateException("churoviaje.anh.pausa y espera-inicial no pueden ser negativas");
        }
    }

    static boolean haciaEstaMaquina(URI uri) {
        String host = uri.getHost();
        return "http".equalsIgnoreCase(uri.getScheme())
                && ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "[::1]".equals(host));
    }
}
