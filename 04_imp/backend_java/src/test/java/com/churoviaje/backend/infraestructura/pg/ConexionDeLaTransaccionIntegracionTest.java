package com.churoviaje.backend.infraestructura.pg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.churoviaje.backend.config.ConfiguracionRelojDePrueba;
import com.churoviaje.backend.config.PruebaIntegracionPostgis;
import com.churoviaje.backend.config.RelojDePrueba;
import com.churoviaje.backend.dominio.Conductor;
import com.churoviaje.backend.dominio.ConductorRepository;
import com.churoviaje.backend.dominio.EventoRepository;
import com.churoviaje.backend.dominio.EventoVial;
import com.churoviaje.backend.dominio.Sesion;
import com.churoviaje.backend.dominio.SesionRepository;
import com.churoviaje.backend.dominio.Ubicacion;
import com.churoviaje.backend.dominio.catalogo.CatalogoRepository;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * CP-170 (T-122): dentro de una transaccion, los repositorios de {@code infraestructura.pg} usan la conexion de la
 * transaccion aunque cada operacion pida y cierre la suya (D-37). Sin eso, el bloqueo del conductor se soltaria al
 * terminar la consulta y lo guardado antes de un rechazo quedaria en la base.
 */
@Import(ConfiguracionRelojDePrueba.class)
class ConexionDeLaTransaccionIntegracionTest extends PruebaIntegracionPostgis {

    private static final Instant AHORA = Instant.parse("2026-11-01T12:00:00Z");

    @Autowired ConductorRepository conductores;
    @Autowired EventoRepository eventos;
    @Autowired CatalogoRepository catalogo;
    @Autowired SesionRepository sesiones;
    @Autowired PlatformTransactionManager gestorTransacciones;
    @Autowired RelojDePrueba reloj;
    /** Solo para preparar y revisar la base fuera de las transacciones de la prueba. */
    @Autowired JdbcTemplate jdbc;
    /** El pool sin el proxy: {@code getConnection()} siempre entrega otra conexion, aunque haya una transaccion. */
    @Autowired DataSource pool;

    private TransactionTemplate transaccion;
    private long conductor;

    @BeforeEach
    void preparar() {
        jdbc.update("delete from evento_vial");
        jdbc.update("delete from sesion");
        jdbc.update("delete from conductor");
        reloj.fijar(AHORA);
        transaccion = new TransactionTemplate(gestorTransacciones);
        conductor = jdbc.queryForObject(
                "insert into conductor (google_id, fecha_alta, simulado) values ('google-cp170', now(), false) returning id",
                Long.class);
    }

    /** Intenta bloquear al conductor desde otra conexion, sin esperar: falla si alguien ya lo tiene bloqueado. */
    private void bloquearDesdeOtraConexion() throws SQLException {
        try (Connection otra = pool.getConnection();
                PreparedStatement sentencia = otra.prepareStatement(
                        "select id from conductor where id = ? for no key update nowait")) {
            sentencia.setLong(1, conductor);
            sentencia.executeQuery().close();
        }
    }

    @Test
    @DisplayName("CP-170: el conductor que bloquea el repositorio sigue bloqueado hasta que termina la transaccion")
    void elBloqueoDuraHastaElFinDeLaTransaccion() throws SQLException {
        transaccion.executeWithoutResult(estado -> {
            assertTrue(conductores.buscarYBloquear(conductor).isPresent());
            // El repositorio ya cerro su conexion, pero el bloqueo sigue: otra conexion no puede tomarlo.
            SQLException ocupado = assertThrows(SQLException.class, this::bloquearDesdeOtraConexion);
            assertEquals("55P03", ocupado.getSQLState(), "lock_not_available");
            // Y otro repositorio, en la misma transaccion, sigue trabajando con la misma conexion.
            assertEquals(0, eventos.contarReportesDesde(conductor, AHORA.minusSeconds(600)));
        });
        bloquearDesdeOtraConexion(); // terminada la transaccion, el bloqueo se solto
    }

    @Test
    @DisplayName("CP-170: si la transaccion se deshace, lo que guardo el repositorio no queda en la base")
    void unRechazoNoDejaNadaGuardado() {
        EventoVial evento = EventoVial.reportar(catalogo.buscarTipoDeEvento("ACCIDENTE").orElseThrow(),
                new Ubicacion(new BigDecimal("-21.5355"), new BigDecimal("-64.7296")), AHORA.atOffset(ZoneOffset.UTC),
                0, conductor, false, AHORA);

        IllegalStateException rechazo = assertThrows(IllegalStateException.class,
                () -> transaccion.executeWithoutResult(estado -> {
                    eventos.guardarNuevo(evento);
                    assertEquals(1, eventos.contarReportesDesde(conductor, AHORA.minusSeconds(600)),
                            "dentro de la transaccion el evento ya se ve");
                    throw new IllegalStateException("rechazo despues de guardar");
                }));

        assertEquals("rechazo despues de guardar", rechazo.getMessage());
        assertEquals(0, jdbc.queryForObject("select count(*) from evento_vial", Long.class));
    }

    @Test
    @DisplayName("RN-08.4: guardar dos veces la misma cuenta de Google deja un solo conductor, el primero")
    void elAltaRepetidaDevuelveElConductorQueYaEstaba() {
        Conductor primero = conductores.guardarNuevo(Conductor.deGoogle("google-repetida", AHORA));
        Conductor segundo = conductores.guardarNuevo(Conductor.deGoogle("google-repetida", AHORA.plusSeconds(60)));

        assertEquals(primero.getId(), segundo.getId());
        assertEquals(AHORA, segundo.getFechaAlta());
        assertEquals(1, jdbc.queryForObject("select count(*) from conductor where google_id = 'google-repetida'",
                Long.class));
    }

    @Test
    @DisplayName("Un error de la base sale como ErrorDeBaseDeDatos, con su SQLState, y deshace la transaccion")
    void unErrorDeLaBaseSaleComoErrorDeBaseDeDatos() {
        Conductor inexistente = Conductor.desdeRegistro(999_999L, "google-que-no-existe", AHORA, false);
        String huella = "a".repeat(64);

        ErrorDeBaseDeDatos error = assertThrows(ErrorDeBaseDeDatos.class,
                () -> transaccion.executeWithoutResult(estado -> {
                    conductores.guardarNuevo(Conductor.deGoogle("google-antes-del-error", AHORA));
                    sesiones.guardar(Sesion.iniciar(inexistente, huella, AHORA, Duration.ofDays(30)));
                }));

        assertEquals("23503", ((SQLException) error.getCause()).getSQLState(), "foreign_key_violation");
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from conductor where google_id = 'google-antes-del-error'", Long.class));
    }

    @Test
    @DisplayName("Fuera de una transaccion, cada operacion toma una conexion del pool y la devuelve")
    void sinTransaccionCadaOperacionUsaSuConexion() throws SQLException {
        assertTrue(conductores.buscarYBloquear(conductor).isPresent());
        bloquearDesdeOtraConexion(); // la consulta termino sola y no dejo nada bloqueado
    }
}
