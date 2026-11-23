package com.churoviaje.backend.anh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.churoviaje.backend.anh.ServidorAnhFalso.Respuesta;
import com.churoviaje.backend.config.PropiedadesAnh;
import com.churoviaje.backend.infraestructura.anh.ClienteAnh;
import com.churoviaje.backend.infraestructura.anh.FalloAnh;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** HU-14: el cliente de ANH (RN-09.3 a RN-09.5) contra un servidor falso en 127.0.0.1. Las esperas no duermen. */
class ClienteAnhTest {

    private static final String RUTA = "estaciones/?departamento=6&producto=0";
    private static final Instant AHORA = Instant.parse("2026-11-23T14:00:00Z");

    private ServidorAnhFalso anh;
    private final List<Duration> esperas = new ArrayList<>();
    private ClienteAnh cliente;

    @BeforeEach
    void preparar() {
        anh = new ServidorAnhFalso();
        cliente = cliente(1, Duration.ofSeconds(1));
    }

    @AfterEach
    void cerrar() {
        anh.close();
    }

    private ClienteAnh cliente(int reintentos, Duration pausa) {
        PropiedadesAnh propiedades = new PropiedadesAnh(true, anh.base(), Duration.ofMinutes(10), Duration.ZERO,
                Duration.ofSeconds(2), reintentos, pausa);
        return new ClienteAnh(propiedades, new ObjectMapper(), Clock.fixed(AHORA, ZoneOffset.UTC), esperas::add);
    }

    @Test
    @DisplayName("Una respuesta 200 en JSON se entrega con un solo intento")
    void respuestaValida() throws Exception {
        anh.responder(RUTA, Respuesta.json(ServidorAnhFalso.listado()));

        ClienteAnh.Respuesta r = cliente.get(RUTA);

        assertThat(r.estadoHttp()).isEqualTo(200);
        assertThat(r.intentos()).isEqualTo(1);
        assertThat(r.cuerpo().get("decCodigo").intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("RN-09.4: un 503 se reintenta una vez, con la pausa entre peticiones")
    void reintentaUnaVez() throws Exception {
        anh.responder(RUTA, Respuesta.estado(503), Respuesta.json(ServidorAnhFalso.listado()));

        ClienteAnh.Respuesta r = cliente.get(RUTA);

        assertThat(r.intentos()).isEqualTo(2);
        assertThat(anh.pedidos(RUTA)).isEqualTo(2);
        assertThat(esperas).contains(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("RN-09.4: si sigue fallando, termina con el último estado y la cantidad de intentos")
    void siempre503() {
        anh.responder(RUTA, Respuesta.estado(503));

        assertThatThrownBy(() -> cliente.get(RUTA))
                .isInstanceOfSatisfying(FalloAnh.class, f -> {
                    assertThat(f.motivo()).isEqualTo(FalloAnh.Motivo.HTTP);
                    assertThat(f.estadoHttp()).isEqualTo(503);
                    assertThat(f.intentos()).isEqualTo(2);
                    assertThat(f.pausarHasta()).isNull();
                });
        assertThat(anh.pedidos(RUTA)).isEqualTo(2);
    }

    @Test
    @DisplayName("RN-09.4: se espera lo que pide Retry-After antes de reintentar")
    void respetaRetryAfter() throws Exception {
        anh.responder(RUTA, Respuesta.estado(429).conCabecera("Retry-After", "7"),
                Respuesta.json(ServidorAnhFalso.listado()));

        cliente.get(RUTA);

        assertThat(esperas).contains(Duration.ofSeconds(7));
    }

    @Test
    @DisplayName("RN-09.4: si Retry-After pide más de 30 s, no se reintenta y se pausa hasta entonces")
    void retryAfterLargo() {
        anh.responder(RUTA, Respuesta.estado(503).conCabecera("Retry-After", "120"));

        assertThatThrownBy(() -> cliente.get(RUTA))
                .isInstanceOfSatisfying(FalloAnh.class,
                        f -> assertThat(f.pausarHasta()).isEqualTo(AHORA.plusSeconds(120)));
        assertThat(anh.pedidos(RUTA)).isEqualTo(1);
    }

    @Test
    @DisplayName("Retry-After también puede venir como fecha HTTP")
    void retryAfterComoFecha() {
        anh.responder(RUTA, Respuesta.estado(503).conCabecera("Retry-After", "Mon, 23 Nov 2026 14:05:00 GMT"));

        assertThatThrownBy(() -> cliente.get(RUTA))
                .isInstanceOfSatisfying(FalloAnh.class,
                        f -> assertThat(f.pausarHasta()).isEqualTo(Instant.parse("2026-11-23T14:05:00Z")));
    }

    @Test
    @DisplayName("Otros errores HTTP (404, 500) no se reintentan")
    void otrosErroresNoSeReintentan() {
        anh.responder(RUTA, Respuesta.estado(500));

        assertThatThrownBy(() -> cliente.get(RUTA)).isInstanceOf(FalloAnh.class);
        assertThat(anh.pedidos(RUTA)).isEqualTo(1);
    }

    @Test
    @DisplayName("RN-09.5: un 200 que no es JSON, o JSON roto, no se entrega")
    void noEsJson() {
        anh.responder(RUTA, new Respuesta(200, "<html></html>", java.util.Map.of("Content-Type", "text/html")));
        assertThatThrownBy(() -> cliente.get(RUTA))
                .isInstanceOfSatisfying(FalloAnh.class, f -> assertThat(f.motivo()).isEqualTo(FalloAnh.Motivo.NO_ES_JSON));

        anh.responder(RUTA, Respuesta.json("{\"decCodigo\": 1, \"decCodigo\": 0}"));
        assertThatThrownBy(() -> cliente.get(RUTA))
                .isInstanceOfSatisfying(FalloAnh.class, f -> assertThat(f.motivo()).isEqualTo(FalloAnh.Motivo.NO_ES_JSON));
    }

    @Test
    @DisplayName("RN-09.5: una respuesta de más de 10 MiB se descarta sin reintentar")
    void demasiadoGrande() {
        anh.responder(RUTA, Respuesta.json("{\"x\": \"" + "a".repeat(ClienteAnh.TAMANO_MAXIMO) + "\"}"));

        assertThatThrownBy(() -> cliente.get(RUTA))
                .isInstanceOfSatisfying(FalloAnh.class,
                        f -> assertThat(f.motivo()).isEqualTo(FalloAnh.Motivo.DEMASIADO_GRANDE));
        assertThat(anh.pedidos(RUTA)).isEqualTo(1);
    }

    @Test
    @DisplayName("RN-09.3: una redirección a otro servidor se detiene")
    void redireccionAOtroServidor() {
        anh.responder(RUTA, new Respuesta(302, "", java.util.Map.of("Location", "https://otro.example.com/api/")));

        assertThatThrownBy(() -> cliente.get(RUTA))
                .isInstanceOfSatisfying(FalloAnh.class,
                        f -> assertThat(f.motivo()).isEqualTo(FalloAnh.Motivo.REDIRECCION_NO_PERMITIDA));
    }

    @Test
    @DisplayName("Una redirección dentro del mismo servidor se sigue")
    void redireccionDentroDeAnh() throws Exception {
        anh.responder(RUTA, new Respuesta(301, "", java.util.Map.of("Location", "/api/estaciones/nueva")));
        anh.responder("estaciones/nueva", Respuesta.json(ServidorAnhFalso.listado()));

        assertThat(cliente.get(RUTA).estadoHttp()).isEqualTo(200);
    }

    @Test
    @DisplayName("RN-09.3: con la base en HTTPS, una redirección a HTTP o a otro servidor no se permite")
    void politicaDeRedirecciones() throws Exception {
        URI base = URI.create("https://abastecimiento.prod.anh.gob.bo/api/");
        var permitida = ClienteAnh.class.getDeclaredMethod("redireccionPermitida", URI.class, URI.class);
        permitida.setAccessible(true);
        assertThat((boolean) permitida.invoke(null, base,
                URI.create("https://abastecimiento.prod.anh.gob.bo/api/estaciones/"))).isTrue();
        assertThat((boolean) permitida.invoke(null, base,
                URI.create("http://abastecimiento.prod.anh.gob.bo/api/estaciones/"))).isFalse();
        assertThat((boolean) permitida.invoke(null, base,
                URI.create("https://abastecimiento.prod.anh.gob.bo.example.com/api/"))).isFalse();
        assertThat((boolean) permitida.invoke(null, base,
                URI.create("https://abastecimiento.prod.anh.gob.bo:8443/api/"))).isFalse();
    }

    @Test
    @DisplayName("Sin conexión se reintenta y termina como SIN_CONEXION")
    void sinConexion() {
        anh.close();

        assertThatThrownBy(() -> cliente.get(RUTA))
                .isInstanceOfSatisfying(FalloAnh.class, f -> {
                    assertThat(f.motivo()).isEqualTo(FalloAnh.Motivo.SIN_CONEXION);
                    assertThat(f.intentos()).isEqualTo(2);
                });
    }

    @Test
    @DisplayName("RN-09.3: la configuración rechaza una base por HTTP que no sea la propia máquina, y ciclos de menos de 10 min")
    void configuracion() {
        assertThatThrownBy(() -> new PropiedadesAnh(true, URI.create("http://abastecimiento.prod.anh.gob.bo/api/"),
                Duration.ofMinutes(10), Duration.ZERO, Duration.ofSeconds(12), 1, Duration.ofSeconds(1)))
                .hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> new PropiedadesAnh(true, URI.create("https://abastecimiento.prod.anh.gob.bo/api/"),
                Duration.ofMinutes(5), Duration.ZERO, Duration.ofSeconds(12), 1, Duration.ofSeconds(1)))
                .hasMessageContaining("10 minutos");
        PropiedadesAnh sinBarra = new PropiedadesAnh(true, URI.create("https://abastecimiento.prod.anh.gob.bo/api"),
                Duration.ofMinutes(10), Duration.ZERO, Duration.ofSeconds(12), 1, Duration.ofSeconds(1));
        assertThat(sinBarra.base().toString()).endsWith("/api/");
    }
}
