package com.churoviaje.backend.servicios;

import com.churoviaje.backend.config.PropiedadesSeguridad;
import com.churoviaje.backend.dominio.Conductor;
import com.churoviaje.backend.dominio.Sesion;
import com.churoviaje.backend.errores.ExcepcionRechazo;
import com.churoviaje.backend.repositorios.ConductorRepositorio;
import com.churoviaje.backend.repositorios.SesionRepositorio;
import com.churoviaje.backend.seguridad.JwtServicio;
import com.churoviaje.backend.seguridad.TokensRenovacion;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** CU-08: inicio, renovacion y cierre de sesion. */
@Service
public class SesionServicio {

    /** Lo que recibe la app al iniciar sesion (RN-08.5, RN-08.12). */
    public record SesionIniciada(String jwt, OffsetDateTime expiraEn, String tokenRenovacion) {}

    /** Lo que recibe la app al renovar: un JWT nuevo; el token de renovacion sigue siendo el mismo. */
    public record JwtRenovado(String jwt, OffsetDateTime expiraEn) {}

    private final ValidadorTokenGoogle validadorGoogle;
    private final ConductorRepositorio conductores;
    private final SesionRepositorio sesiones;
    private final JwtServicio jwtServicio;
    private final TokensRenovacion tokensRenovacion;
    private final PropiedadesSeguridad propiedades;
    private final Clock reloj;
    private final TransactionTemplate transaccion;

    public SesionServicio(ValidadorTokenGoogle validadorGoogle, ConductorRepositorio conductores,
            SesionRepositorio sesiones, JwtServicio jwtServicio, TokensRenovacion tokensRenovacion,
            PropiedadesSeguridad propiedades, Clock reloj, PlatformTransactionManager gestorTransacciones) {
        this.validadorGoogle = validadorGoogle;
        this.conductores = conductores;
        this.sesiones = sesiones;
        this.jwtServicio = jwtServicio;
        this.tokensRenovacion = tokensRenovacion;
        this.propiedades = propiedades;
        this.reloj = reloj;
        this.transaccion = new TransactionTemplate(gestorTransacciones);
    }

    /** RN-08.3 a RN-08.6: valida con Google, busca o crea al conductor y abre la sesion. */
    public SesionIniciada iniciarSesion(String tokenGoogle) {
        String googleId;
        try {
            googleId = validadorGoogle.identificarCuenta(tokenGoogle)
                    .orElseThrow(() -> new ExcepcionRechazo(HttpStatus.UNAUTHORIZED, "TOKEN_GOOGLE_INVALIDO",
                            "No pudimos verificar tu cuenta de Google. Intenta de nuevo."));
        } catch (ValidadorTokenGoogle.GoogleNoDisponibleException e) {
            throw new ExcepcionRechazo(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_NO_DISPONIBLE",
                    "No pudimos conectar con Google. Revisa tu conexión e intenta de nuevo.");
        }

        Instant ahora = reloj.instant();
        String tokenRenovacionNuevo = tokensRenovacion.generar();
        Long conductorId = obtenerOCrearConductor(googleId, ahora);

        transaccion.executeWithoutResult(estado -> sesiones.save(Sesion.iniciar(
                conductores.getReferenceById(conductorId),
                tokensRenovacion.huella(tokenRenovacionNuevo),
                ahora,
                propiedades.renovacion().duracion())));

        return new SesionIniciada(
                jwtServicio.emitir(conductorId, ahora),
                aFecha(jwtServicio.vencimientoDesde(ahora)),
                tokenRenovacionNuevo);
    }

    /** RN-08.12: JWT nuevo sin pasar por Google mientras el token de renovacion siga vigente. */
    public JwtRenovado renovar(String tokenRenovacion) {
        Instant ahora = reloj.instant();
        Long conductorId = transaccion.execute(estado ->
                sesiones.buscarPorHash(tokensRenovacion.huella(tokenRenovacion))
                        .filter(sesion -> sesion.estaVigente(ahora))
                        .map(sesion -> sesion.getConductor().getId())
                        .orElse(null));
        if (conductorId == null) {
            throw new ExcepcionRechazo(HttpStatus.UNAUTHORIZED, "SESION_VENCIDA",
                    "Tu sesión venció. Inicia sesión de nuevo.");
        }
        return new JwtRenovado(jwtServicio.emitir(conductorId, ahora), aFecha(jwtServicio.vencimientoDesde(ahora)));
    }

    /** RN-08.14: el token de renovacion deja de valer. Cerrar una sesion que ya no existe no es un error. */
    public void cerrar(String tokenRenovacion) {
        transaccion.executeWithoutResult(estado -> sesiones.borrarPorHash(tokensRenovacion.huella(tokenRenovacion)));
    }

    /**
     * RN-08.4: en el primer ingreso se crea el conductor; en los siguientes se usa el mismo. Si dos
     * primeros ingresos llegan a la vez, la base rechaza el segundo y se lee el que ya existe.
     */
    private Long obtenerOCrearConductor(String googleId, Instant ahora) {
        try {
            return transaccion.execute(estado -> conductores.findByGoogleId(googleId)
                    .orElseGet(() -> conductores.saveAndFlush(Conductor.deGoogle(googleId, ahora)))
                    .getId());
        } catch (DataIntegrityViolationException carrera) {
            return transaccion.execute(estado -> conductores.findByGoogleId(googleId)
                    .orElseThrow(() -> carrera)
                    .getId());
        }
    }

    private static OffsetDateTime aFecha(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
    }
}
