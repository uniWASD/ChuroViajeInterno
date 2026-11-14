package com.churoviaje.backend.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EstadoCombustibleTest {

    private static final Instant AHORA = Instant.parse("2026-11-14T21:00:00Z");

    private static EstadoCombustible de(OrigenCombustible origen, String tipo, Duration hace) {
        Instant fecha = AHORA.minus(hace);
        return origen == OrigenCombustible.ANH
                ? new EstadoCombustible(1, tipo, origen, true, null, fecha, BigDecimal.TEN, "MEDIO", fecha)
                : new EstadoCombustible(1, tipo, origen, true, null, fecha, null, null, null);
    }

    @Test
    @DisplayName("RN-05.5: vale hasta 6 horas, contadas desde la lectura en ANH")
    void vigencia() {
        assertThat(de(OrigenCombustible.WHATSAPP, "GASOLINA", Duration.ofMinutes(359)).estaVigente(AHORA)).isTrue();
        assertThat(de(OrigenCombustible.WHATSAPP, "GASOLINA", Duration.ofHours(6)).estaVigente(AHORA)).isFalse();
        EstadoCombustible anh = new EstadoCombustible(1, "DIESEL", OrigenCombustible.ANH, true, null,
                AHORA.minus(Duration.ofDays(2)), BigDecimal.ONE, "BAJO", AHORA.minus(Duration.ofHours(1)));
        assertThat(anh.estaVigente(AHORA)).isTrue();
    }

    @Test
    @DisplayName("RN-05.3: uno por tipo, el más reciente de los vigentes")
    void paraMostrar() {
        List<EstadoCombustible> elegidos = EstadoCombustible.paraMostrar(List.of(
                de(OrigenCombustible.WHATSAPP, "GASOLINA", Duration.ofHours(2)),
                de(OrigenCombustible.CONDUCTORES, "GASOLINA", Duration.ofHours(1)),
                de(OrigenCombustible.ANH, "GASOLINA", Duration.ofHours(7)),
                de(OrigenCombustible.ANH, "DIESEL", Duration.ofHours(1))), AHORA);
        assertThat(elegidos).extracting(EstadoCombustible::tipoCodigo, EstadoCombustible::origen)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("GASOLINA", OrigenCombustible.CONDUCTORES),
                        org.assertj.core.groups.Tuple.tuple("DIESEL", OrigenCombustible.ANH));
    }

    @Test
    @DisplayName("Los litros y el estado de ANH son solo de ANH, y los litros nunca negativos")
    void soloAnh() {
        assertThrows(IllegalArgumentException.class, () -> new EstadoCombustible(1, "GASOLINA",
                OrigenCombustible.WHATSAPP, true, null, AHORA, BigDecimal.ONE, null, null));
        assertThrows(NullPointerException.class, () -> new EstadoCombustible(1, "GASOLINA", OrigenCombustible.ANH,
                true, null, AHORA, BigDecimal.ONE, "ALTO", null));
        assertThrows(IllegalArgumentException.class, () -> new EstadoCombustible(1, "GASOLINA", OrigenCombustible.ANH,
                false, null, AHORA, BigDecimal.valueOf(-1), "BAJO", AHORA));
    }
}
