package com.churoviaje.backend.simulacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.churoviaje.backend.config.AmbienteSimulacion;
import com.churoviaje.backend.config.PropiedadesSimulacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** P-20: dónde hay sesiones de prueba y modo demostración (#84). */
class AmbienteSimulacionTest {

    private static final String CREDENCIAL = "credencial-de-prueba-solo-para-las-pruebas-0123456789";

    private static AmbienteSimulacion ambiente(String credencial, boolean demostracion, String... perfiles) {
        MockEnvironment entorno = new MockEnvironment();
        entorno.setActiveProfiles(perfiles);
        return new AmbienteSimulacion(new PropiedadesSimulacion(credencial, demostracion, ""), entorno);
    }

    @Test
    @DisplayName("Sin credencial no hay sesiones de prueba (el caso de dev y de las pruebas automáticas)")
    void sinCredencial() {
        AmbienteSimulacion a = ambiente("", false, "dev");
        assertThat(a.sesionesDePruebaHabilitadas()).isFalse();
        assertThat(a.credencialCorrecta("")).isFalse();
    }

    @Test
    @DisplayName("En el perfil pruebas, con credencial, se habilitan; solo vale la misma credencial")
    void enPruebas() {
        AmbienteSimulacion a = ambiente(CREDENCIAL, true, "pruebas");
        assertThat(a.sesionesDePruebaHabilitadas()).isTrue();
        assertThat(a.modoDemostracion()).isTrue();
        assertThat(a.credencialCorrecta(CREDENCIAL)).isTrue();
        assertThat(a.credencialCorrecta(CREDENCIAL + "x")).isFalse();
        assertThat(a.credencialCorrecta(null)).isFalse();
    }

    @Test
    @DisplayName("En el perfil prod nunca: ni sesiones de prueba ni modo demostración")
    void enProduccion() {
        AmbienteSimulacion a = ambiente(CREDENCIAL, true, "prod");
        assertThat(a.sesionesDePruebaHabilitadas()).isFalse();
        assertThat(a.modoDemostracion()).isFalse();
        assertThat(a.credencialCorrecta(CREDENCIAL)).isFalse();
    }

    @Test
    @DisplayName("Una credencial de prueba corta no deja arrancar el backend")
    void credencialCorta() {
        assertThatThrownBy(() -> new PropiedadesSimulacion("corta", false, ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SIMULACION_CREDENCIAL");
    }
}
