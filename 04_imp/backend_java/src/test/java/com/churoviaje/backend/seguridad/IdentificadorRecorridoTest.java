package com.churoviaje.backend.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import com.churoviaje.backend.config.RelojDePrueba;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #74: el identificador temporal de recorrido de la telemetría. */
class IdentificadorRecorridoTest {

    @Test
    @DisplayName("El mismo conductor tiene el mismo recorrido durante el día, y otro al día siguiente")
    void cambiaConElDia() {
        RelojDePrueba reloj = new RelojDePrueba();
        reloj.fijar(Instant.parse("2026-11-14T04:30:00Z")); // 00:30 en Bolivia
        IdentificadorRecorrido recorridos = new IdentificadorRecorrido(reloj);
        String temprano = recorridos.de(42);
        reloj.fijar(Instant.parse("2026-11-15T03:59:00Z")); // 23:59 del mismo día en Bolivia
        assertThat(recorridos.de(42)).isEqualTo(temprano);
        reloj.avanzar(Duration.ofMinutes(2));                // ya es el día siguiente
        assertThat(recorridos.de(42)).isNotEqualTo(temprano);
    }

    @Test
    @DisplayName("Dos conductores nunca comparten recorrido, y el identificador no deja ver al conductor")
    void noDejaVerAlConductor() {
        IdentificadorRecorrido recorridos = new IdentificadorRecorrido(new RelojDePrueba());
        String a = recorridos.de(1);
        assertThat(a).matches("[0-9a-f]{32}").isNotEqualTo(recorridos.de(2));
        // Otra instancia (por ejemplo, tras reiniciar el backend) tiene otra clave: la relación se perdió.
        assertThat(new IdentificadorRecorrido(new RelojDePrueba()).de(1)).isNotEqualTo(a);
    }
}
