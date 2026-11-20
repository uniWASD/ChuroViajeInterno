package com.churoviaje.backend.dominio.busqueda;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TextoBusquedaTest {

    @Test
    @DisplayName("CP-188: tildes, mayúsculas, signos y abreviaturas no cambian la búsqueda")
    void normaliza() {
        assertThat(TextoBusqueda.normalizar("Av. Las Américas")).isEqualTo("avenida las americas");
        assertThat(TextoBusqueda.normalizar("  avenida   LAS americas ")).isEqualTo("avenida las americas");
        assertThat(TextoBusqueda.normalizar("Pje. Ñandú")).isEqualTo("pasaje nandu");
        assertThat(TextoBusqueda.normalizar("U.E. San Jerónimo")).isEqualTo("u e san jeronimo");
        assertThat(TextoBusqueda.normalizar("¿?¡!")).isEmpty();
        assertThat(TextoBusqueda.normalizar(null)).isEmpty();
    }

    @Test
    @DisplayName("CP-188: cada palabra escrita es el comienzo de una palabra del nombre")
    void coincide() {
        String hospital = TextoBusqueda.normalizar("Hospital Regional San Juan de Dios");
        assertThat(TextoBusqueda.coincide(hospital, List.of("san", "juan"))).isTrue();
        assertThat(TextoBusqueda.coincide(hospital, List.of("hosp", "dios"))).isTrue();
        assertThat(TextoBusqueda.coincide(hospital, List.of("uan"))).isFalse();
        assertThat(TextoBusqueda.coincide(hospital, List.of("san", "pedro"))).isFalse();
    }

    @Test
    @DisplayName("CP-188: primero lo que empieza con lo escrito, después la calle con ese nombre")
    void cercania() {
        assertThat(TextoBusqueda.cercania("sucre", "sucre")).isZero();
        assertThat(TextoBusqueda.cercania("calle sucre", "sucre")).isEqualTo(1);
        assertThat(TextoBusqueda.cercania("plaza antonio jose de sucre", "sucre")).isEqualTo(2);
    }
}
