package com.churoviaje.backend.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.churoviaje.backend.config.PruebaIntegracionPostgis;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T-118 (#113): el vínculo de cada surtidor con su estación de ANH (V12) y las columnas de ANH de
 * {@code estado_combustible} (V8). CP-159 y CP-160, más las demás restricciones que repite la base (RN-09.10, RN-09.7).
 */
class VinculoAnhIntegracionTest extends PruebaIntegracionPostgis {

    private static final Instant LEIDO = Instant.parse("2026-11-23T14:00:00Z");
    private static final Instant ACTUALIZADO = Instant.parse("2026-11-23T13:40:00Z");

    @Autowired JdbcTemplate jdbc;

    private long lasVegas;
    private long donDaniel;
    private List<Map<String, Object>> vinculosDeV13;

    @BeforeEach
    void preparar() {
        vinculosDeV13 = jdbc.queryForList("select id, anh_id from surtidor where anh_id is not null");
        jdbc.update("delete from estado_combustible");
        jdbc.update("update surtidor set anh_id = null");
        lasVegas = jdbc.queryForObject("select id from surtidor where nombre = 'Las Vegas'", Long.class);
        donDaniel = jdbc.queryForObject("select id from surtidor where nombre = 'Don Daniel'", Long.class);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("delete from estado_combustible");
        jdbc.update("update surtidor set anh_id = null");
        for (Map<String, Object> v : vinculosDeV13) {
            jdbc.update("update surtidor set anh_id = ? where id = ?", v.get("anh_id"), v.get("id"));
        }
    }

    @Test
    @DisplayName("V13 carga los 14 vínculos revisados, uno por surtidor del catálogo")
    void v13CargaLosVinculosRevisados() {
        Map<Integer, String> vinculos = new java.util.TreeMap<>();
        for (Map<String, Object> v : vinculosDeV13) {
            vinculos.put((Integer) v.get("anh_id"),
                    jdbc.queryForObject("select nombre from surtidor where id = ?", String.class, v.get("id")));
        }
        assertThat(vinculos).hasSize(14)
                .containsEntry(2977, "El Molle")
                .containsEntry(2642, "Las Vegas")
                .containsEntry(2911, "Campesino")
                .containsEntry(2388, "Panamericano")
                .containsEntry(2341, "Ex Terminal")
                .containsEntry(2974, "YPFB Parada Chaco");
    }

    @Test
    @DisplayName("CP-159: asignar el mismo anh_id a dos surtidores falla por la restricción de unicidad")
    void cp159MismoAnhIdEnDosSurtidores() {
        jdbc.update("update surtidor set anh_id = 2341 where id = ?", lasVegas);

        assertThatThrownBy(() -> jdbc.update("update surtidor set anh_id = 2341 where id = ?", donDaniel))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_surtidor_anh_id");
        assertThat(jdbc.queryForObject("select count(*) from surtidor where anh_id = 2341", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("anh_id es opcional: los surtidores sin estación de ANH quedan en null, y puede haber muchos")
    void anhIdOpcional() {
        jdbc.update("update surtidor set anh_id = 2341 where id = ?", lasVegas);

        assertThat(jdbc.queryForObject("select count(*) from surtidor where anh_id is null", Integer.class))
                .isGreaterThan(1);
    }

    @Test
    @DisplayName("Un anh_id que no es un entero positivo se rechaza")
    void anhIdPositivo() {
        assertThatThrownBy(() -> jdbc.update("update surtidor set anh_id = 0 where id = ?", lasVegas))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_surtidor_anh_id");
    }

    @Test
    @DisplayName("CP-160: estado_combustible guarda un dato de ANH con 0 litros, estado «bajo» y consultado_en")
    void cp160GuardaDatoDeAnh() {
        insertarAnh(lasVegas, new BigDecimal("0"), "BAJO");

        Map<String, Object> fila = jdbc.queryForMap("""
                select origen, disponible, litros, estado_anh, fecha_dato, consultado_en
                from estado_combustible where surtidor_id = ?""", lasVegas);
        assertThat(fila.get("origen")).isEqualTo("ANH");
        assertThat(fila.get("disponible")).isEqualTo(false);
        assertThat((BigDecimal) fila.get("litros")).isEqualByComparingTo("0");
        assertThat(fila.get("estado_anh")).isEqualTo("BAJO");
        assertThat(((Timestamp) fila.get("fecha_dato")).toInstant()).isEqualTo(ACTUALIZADO);
        assertThat(((Timestamp) fila.get("consultado_en")).toInstant()).isEqualTo(LEIDO);
    }

    @Test
    @DisplayName("CP-160: estado_combustible rechaza un dato de ANH con litros negativos")
    void cp160RechazaLitrosNegativos() {
        assertThatThrownBy(() -> insertarAnh(lasVegas, new BigDecimal("-1"), "BAJO"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_estado_combustible_litros");
        assertThat(jdbc.queryForObject("select count(*) from estado_combustible", Integer.class)).isZero();
    }

    @Test
    @DisplayName("El estado de ANH solo admite alto, medio o bajo, y un dato de ANH siempre sabe cuándo se leyó")
    void otrasRestriccionesDeAnh() {
        assertThatThrownBy(() -> insertarAnh(lasVegas, new BigDecimal("10"), "VACIO"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_estado_combustible_estado_anh");
        assertThatThrownBy(() -> jdbc.update("""
                insert into estado_combustible (surtidor_id, tipo_codigo, origen, disponible, fecha_dato, litros,
                                                actualizado_en)
                values (?, 'GASOLINA', 'ANH', true, ?, 10, ?)""",
                lasVegas, Timestamp.from(ACTUALIZADO), Timestamp.from(LEIDO)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_estado_combustible_solo_anh");
    }

    @Test
    @DisplayName("V15: anh_actualizado es solo de ANH, y un dato de ANH sin él (de la versión anterior) se acepta")
    void anhActualizadoSoloDeAnh() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into estado_combustible (surtidor_id, tipo_codigo, origen, disponible, fecha_dato,
                                                actualizado_en, anh_actualizado)
                values (?, 'GASOLINA', 'WHATSAPP', true, ?, ?, ?)""",
                lasVegas, Timestamp.from(ACTUALIZADO), Timestamp.from(LEIDO), Timestamp.from(ACTUALIZADO)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_estado_combustible_solo_anh_actualizado");
        jdbc.update("""
                insert into estado_combustible (surtidor_id, tipo_codigo, origen, disponible, fecha_dato, litros,
                                                consultado_en, actualizado_en)
                values (?, 'GASOLINA', 'ANH', true, ?, 10, ?, ?)""",
                lasVegas, Timestamp.from(ACTUALIZADO), Timestamp.from(LEIDO), Timestamp.from(LEIDO));
        assertThat(jdbc.queryForObject("select count(*) from estado_combustible where origen = 'ANH' "
                + "and anh_actualizado is null", Integer.class)).isEqualTo(1);
    }

    private void insertarAnh(long surtidor, BigDecimal litros, String estadoAnh) {
        jdbc.update("""
                insert into estado_combustible (surtidor_id, tipo_codigo, origen, disponible, fecha_dato, litros,
                                                estado_anh, consultado_en, actualizado_en, anh_actualizado)
                values (?, 'GASOLINA', 'ANH', ?, ?, ?, ?, ?, ?, ?)""",
                surtidor, litros.signum() > 0, Timestamp.from(ACTUALIZADO), litros, estadoAnh,
                Timestamp.from(LEIDO), Timestamp.from(LEIDO), Timestamp.from(ACTUALIZADO));
    }
}
