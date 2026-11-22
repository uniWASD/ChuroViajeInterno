package com.churoviaje.backend.servicios;

import static org.assertj.core.api.Assertions.assertThat;

import com.churoviaje.backend.config.ConfiguracionRelojDePrueba;
import com.churoviaje.backend.config.PruebaIntegracionPostgis;
import com.churoviaje.backend.config.RelojDePrueba;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** T-105 (#85): la limpieza diaria borra lo que pasó los 30 días y nada vigente. CP-144. */
@Import(ConfiguracionRelojDePrueba.class)
class LimpiezaDatosIntegracionTest extends PruebaIntegracionPostgis {

    private static final Instant AHORA = Instant.parse("2026-11-22T07:30:00Z");

    @Autowired JdbcTemplate jdbc;
    @Autowired RelojDePrueba reloj;
    @Autowired LimpiezaDatos limpieza;

    private long reportante;
    private long votante;
    private long surtidor;

    @BeforeEach
    void preparar() {
        jdbc.update("delete from confirmacion_recarga");
        jdbc.update("delete from voto");
        jdbc.update("delete from evento_vial");
        jdbc.update("delete from sesion");
        jdbc.update("delete from conductor");
        reloj.fijar(AHORA);
        reportante = conductor("google-reportante");
        votante = conductor("google-votante");
        surtidor = jdbc.queryForObject("select id from surtidor order by id limit 1", Long.class);
    }

    private long conductor(String googleId) {
        return jdbc.queryForObject("insert into conductor (google_id, fecha_alta) values (?, now()) returning id",
                Long.class, googleId);
    }

    private static Timestamp hace(Duration tiempo) {
        return Timestamp.from(AHORA.minus(tiempo));
    }

    /** Un accidente (vence a los 60 minutos) que ocurrió hace {@code ocurrio}; {@code estado} como quedó en la base. */
    private long accidente(Duration ocurrio, String estado, Duration actualizado) {
        return jdbc.queryForObject("""
                insert into evento_vial (tipo_codigo, ubicacion, ocurrio_en, registrado_en, actualizado_en,
                                         reportante_id, origen, estado)
                values ('ACCIDENTE', ST_SetSRID(ST_MakePoint(-64.7296, -21.5355), 4326)::geography, ?, ?, ?, ?,
                        'CONDUCTOR', ?)
                returning id
                """, Long.class, hace(ocurrio), hace(ocurrio), hace(actualizado), reportante, estado);
    }

    /** Un accidente que venció hace {@code dias} días. */
    private long vencidoHace(int dias) {
        Duration ocurrio = Duration.ofDays(dias).plusMinutes(60);
        return accidente(ocurrio, "VENCIDO", Duration.ofDays(dias));
    }

    private void voto(long evento) {
        jdbc.update("insert into voto (evento_id, conductor_id, valor, fecha) values (?, ?, 'REAL', ?)",
                evento, votante, hace(Duration.ofDays(40)));
    }

    private void confirmacion(Duration hace) {
        jdbc.update("insert into confirmacion_recarga (conductor_id, surtidor_id, tipo_codigo, fecha) values (?, ?, 'DIESEL', ?)",
                reportante, surtidor, hace(hace));
    }

    private void sesion(String huella, Duration emitida, Duration vence) {
        jdbc.update("insert into sesion (conductor_id, token_renovacion_hash, emitida_en, expira_en) values (?, ?, ?, ?)",
                reportante, huella, hace(emitida), Timestamp.from(AHORA.plus(vence)));
    }

    private boolean existe(long evento) {
        return jdbc.queryForObject("select count(*) from evento_vial where id = ?", Integer.class, evento) == 1;
    }

    @Test
    @DisplayName("CP-144: un evento vencido hace 31 días y sus votos se borran; uno vencido hace 29 días se conserva")
    void cp144EventoVencidoHace31Dias() {
        long viejo = vencidoHace(31);
        voto(viejo);
        long reciente = vencidoHace(29);
        voto(reciente);

        LimpiezaDatos.Resultado r = limpieza.limpiar();

        assertThat(existe(viejo)).isFalse();
        assertThat(existe(reciente)).isTrue();
        assertThat(jdbc.queryForList("select evento_id from voto", Long.class)).containsExactly(reciente);
        assertThat(r.eventos()).isEqualTo(1);
        assertThat(r.votos()).isEqualTo(1);
    }

    @Test
    @DisplayName("Un retirado se cuenta desde que se retiró, y uno vencido que la tarea no marcó, desde que venció")
    void retiradosYVencidosSinMarcar() {
        long retiradoViejo = accidente(Duration.ofDays(40), "RETIRADO", Duration.ofDays(31));
        long retiradoReciente = accidente(Duration.ofDays(40), "RETIRADO", Duration.ofDays(29));
        long sinMarcarViejo = accidente(Duration.ofDays(31).plusMinutes(60), "ACTIVO", Duration.ofDays(31).plusMinutes(60));

        limpieza.limpiar();

        assertThat(existe(retiradoViejo)).isFalse();
        assertThat(existe(retiradoReciente)).isTrue();
        assertThat(existe(sinMarcarViejo)).isFalse();
    }

    @Test
    @DisplayName("Nada vigente se borra: un evento activo, una confirmación reciente ni una sesión válida")
    void nadaVigenteSeBorra() {
        long activo = accidente(Duration.ofMinutes(10), "ACTIVO", Duration.ofMinutes(10));
        voto(activo);
        long confirmado = accidente(Duration.ofMinutes(30), "CONFIRMADO", Duration.ofMinutes(5));
        confirmacion(Duration.ofMinutes(5));
        sesion("a".repeat(64), Duration.ofDays(10), Duration.ofDays(20));

        LimpiezaDatos.Resultado r = limpieza.limpiar();

        assertThat(r).isEqualTo(new LimpiezaDatos.Resultado(0, 0, 0, 0));
        assertThat(existe(activo)).isTrue();
        assertThat(existe(confirmado)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from voto", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from confirmacion_recarga", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from sesion", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("RN-08.14 y P-21: se borran las confirmaciones de más de 30 días y las sesiones vencidas")
    void confirmacionesYSesiones() {
        confirmacion(Duration.ofDays(31));
        confirmacion(Duration.ofDays(29));
        sesion("b".repeat(64), Duration.ofDays(31), Duration.ofDays(-1));
        sesion("c".repeat(64), Duration.ofDays(5), Duration.ofDays(25));

        LimpiezaDatos.Resultado r = limpieza.limpiar();

        assertThat(r.confirmaciones()).isEqualTo(1);
        assertThat(r.sesiones()).isEqualTo(1);
        assertThat(jdbc.queryForList("select fecha from confirmacion_recarga", Timestamp.class))
                .containsExactly(hace(Duration.ofDays(29)));
        assertThat(jdbc.queryForList("select token_renovacion_hash from sesion", String.class))
                .isEqualTo(List.of("c".repeat(64)));
    }
}
