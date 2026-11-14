package com.churoviaje.backend.dominio.monitoreo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.churoviaje.backend.dominio.Surtidor;
import com.churoviaje.backend.dominio.Ubicacion;
import com.churoviaje.backend.dominio.catalogo.TipoCombustible;
import com.churoviaje.backend.dominio.catalogo.ValorFila;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResultadoAnalisisTest {

    private static final Instant AHORA = Instant.parse("2026-11-14T19:20:00Z");
    private static final Ubicacion PUNTO = new Ubicacion(new BigDecimal("-21.52"), new BigDecimal("-64.72"));
    private static final CatalogoDeAvisos CATALOGO = new CatalogoDeAvisos(
            List.of(new Surtidor(1, "Las Vegas", PUNTO, List.of("vegas")), new Surtidor(2, "El Portillo", PUNTO, List.of())),
            List.of(new TipoCombustible("GASOLINA", "Gasolina"), new TipoCombustible("DESCONOCIDO", "Desconocido")));

    private static AvisoRecibido aviso(String surtidor, String fecha) {
        return new AvisoRecibido(surtidor, "Gasolina", "DISPONIBLE", null, fecha);
    }

    @Test
    @DisplayName("Un mensaje hasta 5 minutos en el futuro se tolera por el reloj; más allá se descarta")
    void fechaEnElFuturo() {
        ResultadoAnalisis r = ResultadoAnalisis.validar(List.of(aviso("Vegas", "2026-11-14T15:25:00-04:00"),
                aviso("Vegas", "2026-11-14T15:25:01-04:00")), CATALOGO, AHORA);
        assertThat(r.aceptados()).isEqualTo(1);
        assertThat(r.descartados()).containsExactly(new ResultadoAnalisis.Descartado(1, ResultadoAnalisis.Motivo.FECHA_INVALIDA));
    }

    @Test
    @DisplayName("Con la misma fecha se queda el primero del lote")
    void empateSeQuedaElPrimero() {
        ResultadoAnalisis r = ResultadoAnalisis.validar(List.of(
                new AvisoRecibido("Las Vegas", "Gasolina", "AGOTADO", null, "2026-11-14T15:00:00-04:00"),
                new AvisoRecibido("Vegas", "Gasolina", "DISPONIBLE", null, "2026-11-14T15:00:00-04:00")), CATALOGO, AHORA);
        assertThat(r.estados()).singleElement().satisfies(e -> assertThat(e.disponible()).isFalse());
        assertThat(r.aceptados()).isEqualTo(2);
    }

    @Test
    @DisplayName("Un surtidor hecho solo de signos es desconocido, no un error")
    void surtidorSoloSignos() {
        ResultadoAnalisis r = ResultadoAnalisis.validar(List.of(aviso("---", "2026-11-14T15:00:00-04:00")), CATALOGO, AHORA);
        assertThat(r.descartados()).extracting(ResultadoAnalisis.Descartado::motivo)
                .containsExactly(ResultadoAnalisis.Motivo.SURTIDOR_DESCONOCIDO);
    }

    @Test
    @DisplayName("Los valores de la fila se reconocen sin importar mayúsculas ni espacios")
    void filas() {
        assertThat(ResultadoAnalisis.fila(" sin fila ")).isEqualTo(ValorFila.SIN_FILA);
        assertThat(ResultadoAnalisis.fila("Larga")).isEqualTo(ValorFila.LARGA);
        assertThat(ResultadoAnalisis.fila("enorme")).isNull();
        assertThat(ResultadoAnalisis.fila(null)).isNull();
    }

    @Test
    @DisplayName("El dominio también rechaza un lote de más de 1000 avisos")
    void maximo() {
        List<AvisoRecibido> lote = Collections.nCopies(1001, aviso("Vegas", "2026-11-14T15:00:00-04:00"));
        assertThrows(IllegalArgumentException.class, () -> ResultadoAnalisis.validar(lote, CATALOGO, AHORA));
    }
}
