package com.churoviaje.backend.anh;

import static com.churoviaje.backend.anh.ServidorAnhFalso.catalogo;
import static com.churoviaje.backend.anh.ServidorAnhFalso.estacion;
import static com.churoviaje.backend.anh.ServidorAnhFalso.listado;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.churoviaje.backend.dominio.EstadoCombustible;
import com.churoviaje.backend.dominio.OrigenCombustible;
import com.churoviaje.backend.dominio.anh.LecturaAnh;
import com.churoviaje.backend.dominio.anh.ReporteAnh;
import com.churoviaje.backend.dominio.anh.RespuestaDescartada;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** HU-14: {@link ReporteAnh} valida lo que responde ANH (RN-09.2, RN-09.5 a RN-09.7, RN-09.11 a RN-09.13). */
class ReporteAnhTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String HORA = "2026-11-23T10:00:00";
    private static final Instant LEIDO = Instant.parse("2026-11-23T14:05:00Z");

    private static JsonNode json(String texto) throws Exception {
        return JSON.readTree(texto);
    }

    private static ReporteAnh leer(String cuerpo) throws Exception {
        return ReporteAnh.leer(json(cuerpo), 6, 0);
    }

    private static String estacion(int id, String litros) {
        return ServidorAnhFalso.estacion(id, "ESTACION " + id, -21.52, -64.72, 0, litros, "alto", HORA);
    }

    @Test
    @DisplayName("RN-09.2: Tarija se busca por nombre en el catálogo")
    void tarijaPorNombre() throws Exception {
        assertThat(ReporteAnh.idDeTarija(json(catalogo()))).isEqualTo(6);
        assertThat(ReporteAnh.idDeTarija(json("{\"decCodigo\": 1, \"oResultado\": [{\"id\": \"9\", \"nombre\": \" TARIJA \"}]}")))
                .isEqualTo(9);
    }

    @Test
    @DisplayName("RN-09.2: un identificador textual de diez dígitos dentro del rango INTEGER se acepta")
    void idTextualDeDiezDigitos() throws Exception {
        assertThat(ReporteAnh.idDeTarija(json(
                "{\"decCodigo\": 1, \"oResultado\": [{\"id\": \"2000000000\", \"nombre\": \"Tarija\"}]}")))
                .isEqualTo(2_000_000_000);
    }

    @Test
    @DisplayName("RN-09.2: sin Tarija, o con dos, no se supone ningún id")
    void tarijaTieneQueSerUnica() {
        assertThatThrownBy(() -> ReporteAnh.idDeTarija(json("{\"decCodigo\": 1, \"oResultado\": [{\"id\": 1, \"nombre\": \"La Paz\"}]}")))
                .isInstanceOf(RespuestaDescartada.class);
        assertThatThrownBy(() -> ReporteAnh.idDeTarija(json(
                "{\"decCodigo\": 1, \"oResultado\": [{\"id\": 6, \"nombre\": \"Tarija\"}, {\"id\": 8, \"nombre\": \"tarija\"}]}")))
                .isInstanceOf(RespuestaDescartada.class)
                .hasMessageContaining("más de un");
    }

    @Test
    @DisplayName("RN-09.5: decCodigo distinto de 1 es un error de ANH, no una lista vacía")
    void decCodigoDistintoDeUno() {
        assertThatThrownBy(() -> leer("{\"decCodigo\": 0, \"oResultado\": []}")).isInstanceOf(RespuestaDescartada.class);
        assertThatThrownBy(() -> leer("{\"decCodigo\": \"1\", \"oResultado\": []}")).isInstanceOf(RespuestaDescartada.class);
        assertThatThrownBy(() -> leer("{\"oResultado\": []}")).isInstanceOf(RespuestaDescartada.class);
        assertThatThrownBy(() -> leer("{\"decCodigo\": 1, \"oResultado\": {}}")).isInstanceOf(RespuestaDescartada.class);
        assertThatThrownBy(() -> leer("[]")).isInstanceOf(RespuestaDescartada.class);
    }

    @Test
    @DisplayName("CP-163 (unidad): una estación de otro departamento descarta la respuesta completa")
    void otroDepartamento() {
        String otra = estacion(2002, "10").replace("\"departamento_id\": 6", "\"departamento_id\": 7");
        assertThatThrownBy(() -> leer(listado(estacion(2001, "10"), otra)))
                .isInstanceOf(RespuestaDescartada.class)
                .hasMessageContaining("otro departamento");
    }

    @Test
    @DisplayName("RN-09.6: otro combustible, estación repetida, id no entero o paginación descartan la respuesta")
    void descartesCompletos() {
        assertThatThrownBy(() -> leer(listado(estacion(2001, "10").replace("\"producto_id\": 0", "\"producto_id\": 1"))))
                .hasMessageContaining("otro combustible");
        assertThatThrownBy(() -> leer(listado(estacion(2001, "10"), estacion(2001, "20"))))
                .hasMessageContaining("repetida");
        assertThatThrownBy(() -> leer(listado(estacion(2001, "10").replace("\"id\": 2001", "\"id\": 20.5"))))
                .hasMessageContaining("entero");
        assertThatThrownBy(() -> leer(listado(estacion(2001, "10").replace("\"id\": 2001", "\"id\": \"abc\""))))
                .hasMessageContaining("entero");
        assertThatThrownBy(() -> leer("{\"decCodigo\": 1, \"next\": \"?page=2\", \"oResultado\": [" + estacion(2001, "10") + "]}"))
                .hasMessageContaining("paginada");
        assertThatThrownBy(() -> leer("{\"decCodigo\": 1, \"total_pages\": 3, \"oResultado\": []}"))
                .hasMessageContaining("paginada");
    }

    @Test
    @DisplayName("Una señal de paginación vacía (null, false o 0) no descarta nada")
    void paginacionVacia() throws Exception {
        ReporteAnh reporte = leer("{\"decCodigo\": 1, \"next\": null, \"hasMore\": false, \"page\": 0, \"oResultado\": ["
                + estacion(2001, "10") + "]}");
        assertThat(reporte.lecturas()).hasSize(1);
    }

    @Test
    @DisplayName("CP-164 (unidad): un saldo «abc» queda sin saldo y no frena a las demás estaciones")
    void cp164SaldoInvalido() throws Exception {
        ReporteAnh reporte = leer(listado(estacion(2001, "\"abc\""), estacion(2002, "20")));
        assertThat(reporte.lecturas()).extracting(LecturaAnh::litros).containsExactly(null, new java.math.BigDecimal("20"));
        assertThat(reporte.lecturas().get(0).comoEstado(1, "GASOLINA", LEIDO)).isEmpty();
        EstadoCombustible estado = reporte.lecturas().get(1).comoEstado(1, "GASOLINA", LEIDO).orElseThrow();
        assertThat(estado.disponible()).isTrue();
        assertThat(estado.origen()).isEqualTo(OrigenCombustible.ANH);
    }

    @Test
    @DisplayName("RN-09.7: un saldo negativo, booleano o ausente es «sin saldo»; nunca cero")
    void saldosInvalidos() throws Exception {
        ReporteAnh reporte = leer(listado(estacion(2001, "-5"), estacion(2002, "true"), estacion(2003, "null"),
                estacion(2004, "\"12.5\"")));
        assertThat(reporte.lecturas()).extracting(LecturaAnh::litros)
                .containsExactly(null, null, null, new java.math.BigDecimal("12.5"));
    }

    @Test
    @DisplayName("RN-09.11: 0 litros es agotado y más de 0 es disponible; el estado de ANH no decide")
    void traduccionDeLitros() throws Exception {
        ReporteAnh reporte = leer(listado(
                ServidorAnhFalso.estacion(2001, "A", -21.52, -64.72, 0, "0", "alto", HORA),
                ServidorAnhFalso.estacion(2002, "B", -21.52, -64.72, 0, "37", "bajo", HORA)));
        EstadoCombustible agotado = reporte.lecturas().get(0).comoEstado(1, "GASOLINA", LEIDO).orElseThrow();
        EstadoCombustible disponible = reporte.lecturas().get(1).comoEstado(2, "GASOLINA", LEIDO).orElseThrow();
        assertThat(agotado.disponible()).isFalse();
        assertThat(agotado.estadoAnh()).isEqualTo("ALTO");
        assertThat(disponible.disponible()).isTrue();
        assertThat(disponible.estadoAnh()).isEqualTo("BAJO");
        assertThat(disponible.consultadoEn()).isEqualTo(LEIDO);
    }

    @Test
    @DisplayName("RN-09.13: updated_at sin zona es hora de Tarija; con zona, tal cual; sin fecha no hay estado")
    void fechas() throws Exception {
        ReporteAnh reporte = leer(listado(
                ServidorAnhFalso.estacion(2001, "A", -21.52, -64.72, 0, "5", "alto", "2026-11-23T10:00:00"),
                ServidorAnhFalso.estacion(2002, "B", -21.52, -64.72, 0, "5", "alto", "2026-11-23 10:00:00.123"),
                ServidorAnhFalso.estacion(2003, "C", -21.52, -64.72, 0, "5", "alto", "2026-11-23T10:00:00Z"),
                ServidorAnhFalso.estacion(2004, "D", -21.52, -64.72, 0, "5", "alto", "ayer")));
        assertThat(reporte.lecturas()).extracting(LecturaAnh::actualizado).containsExactly(
                Instant.parse("2026-11-23T14:00:00Z"), Instant.parse("2026-11-23T14:00:00.123Z"),
                Instant.parse("2026-11-23T10:00:00Z"), null);
        assertThat(reporte.lecturas().get(3).comoEstado(1, "GASOLINA", LEIDO)).isEmpty();
    }

    @Test
    @DisplayName("Con una lista productos se toma solo el combustible pedido; si no está, esa estación no trae dato")
    void listaDeProductos() throws Exception {
        String conLista = """
                {"id": 2001, "nombre": "A", "lat": -21.52, "lng": -64.72, "productos": [
                  {"producto_id": 0, "saldo_litros": 100, "saldo_estado": "medio", "updated_at": "%s"},
                  {"producto_id": 1, "saldo_litros": 0, "saldo_estado": "bajo", "updated_at": "%s"}]}""".formatted(HORA, HORA);
        String sinGasolina = """
                {"id": 2002, "nombre": "B", "lat": -21.52, "lng": -64.72, "productos": [
                  {"producto_id": 1, "saldo_litros": 0, "saldo_estado": "bajo", "updated_at": "%s"}]}""".formatted(HORA);
        ReporteAnh reporte = leer(listado(conLista, sinGasolina));
        assertThat(reporte.lecturas()).hasSize(1);
        assertThat(reporte.lecturas().get(0).litros()).isEqualByComparingTo("100");
        assertThat(reporte.lecturas().get(0).estadoAnh()).isEqualTo("MEDIO");
    }

    @Test
    @DisplayName("Sin coordenadas o en 0,0 la estación queda sin ubicación (y el servicio la ignora)")
    void sinUbicacion() throws Exception {
        ReporteAnh reporte = leer(listado(
                ServidorAnhFalso.estacion(2001, "A", 0, 0, 0, "5", "alto", HORA),
                estacion(2002, "5").replace("\"lat\": -21.52", "\"lat\": \"x\"")));
        assertThat(reporte.lecturas()).allSatisfy(l -> assertThat(l.ubicacion()).isNull());
    }
}
