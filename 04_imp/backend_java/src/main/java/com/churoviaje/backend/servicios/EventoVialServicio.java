package com.churoviaje.backend.servicios;

import com.churoviaje.backend.dominio.Conductor;
import com.churoviaje.backend.dominio.ConductorRepository;
import com.churoviaje.backend.dominio.EventoRepository;
import com.churoviaje.backend.dominio.EventoVial;
import com.churoviaje.backend.dominio.Ubicacion;
import com.churoviaje.backend.dominio.catalogo.CatalogoRepository;
import com.churoviaje.backend.dominio.cobertura.CoberturaManchaUrbana;
import com.churoviaje.backend.errores.ExcepcionRechazo;
import com.churoviaje.backend.motor.AvisoTrafico;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registra los eventos que reportan los conductores (CU-01). */
@Service
public class EventoVialServicio {

    private final EventoRepository eventos;
    private final ConductorRepository conductores;
    private final CatalogoRepository catalogo;
    private final CoberturaManchaUrbana cobertura;
    private final Clock reloj;
    private final ApplicationEventPublisher avisos;

    public EventoVialServicio(EventoRepository eventos, ConductorRepository conductores, CatalogoRepository catalogo,
            CoberturaManchaUrbana cobertura, Clock reloj, ApplicationEventPublisher avisos) {
        this.eventos = eventos;
        this.conductores = conductores;
        this.catalogo = catalogo;
        this.cobertura = cobertura;
        this.reloj = reloj;
        this.avisos = avisos;
    }

    /**
     * RN-01.2 a RN-01.11, en una sola transaccion: si algo falla, no queda nada guardado (RN-01.11). El conductor
     * queda bloqueado mientras se revisa, asi que dos reportes suyos que llegan juntos no esquivan el duplicado ni
     * el limite.
     */
    @Transactional
    public EventoVial registrarEvento(long conductorId, String codigoTipo, Ubicacion ubicacion,
            OffsetDateTime horaConfirmacion, Integer minutosElegidos) {
        Instant ahora = reloj.instant();
        var tipo = catalogo.buscarTipoDeEvento(codigoTipo).orElseThrow(EventoVial::tipoNoReportable); // RN-01.3
        ubicacion.validarParaReporte(cobertura); // RN-01.6
        Conductor reportante = conductores.buscarYBloquear(conductorId)
                .orElseThrow(() -> new ExcepcionRechazo(HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO",
                        "Inicia sesión para continuar."));
        // RN-01.2, RN-01.4, RN-01.7 y RN-01.17 (un conductor simulado deja eventos simulados)
        EventoVial evento = EventoVial.reportar(tipo, ubicacion, horaConfirmacion, minutosElegidos, conductorId,
                reportante.isSimulado(), ahora);
        Conductor.verificarLimiteDeReportes(
                eventos.contarReportesDesde(conductorId, ahora.minus(Conductor.VENTANA_REPORTES))); // RN-01.9
        if (eventos.existeDuplicado(evento)) {
            throw EventoVial.duplicado(); // RN-01.8
        }
        EventoVial guardado = evento.conId(eventos.guardarNuevo(evento)); // RN-01.10
        avisos.publishEvent(AvisoTrafico.nace(guardado)); // T-099: el motor lo recibe cuando quedo guardado
        return guardado;
    }
}
