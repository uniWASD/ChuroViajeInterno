package com.churoviaje.backend.servicios;

import com.churoviaje.backend.dominio.ConfirmacionRecargaRepository;
import com.churoviaje.backend.dominio.EventoRepository;
import com.churoviaje.backend.dominio.SesionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * T-105 (#85): cada día borra lo que el sistema ya no necesita ({@code 06_base_de_datos.md}): los eventos retirados o
 * vencidos hace más de 30 días con sus votos, las confirmaciones de recarga de más de 30 días (P-21) y las sesiones
 * cuyo token de renovación venció (RN-08.14). Cerrar sesión ya borra la fila al momento. Nada vigente se borra.
 */
@Component
public class LimpiezaDatos {

    private static final Logger LOG = LoggerFactory.getLogger(LimpiezaDatos.class);

    /** P-21: cuánto se conserva lo que ya no se muestra. */
    public static final Duration CONSERVACION = Duration.ofDays(30);

    /** Lo que borró una pasada. */
    public record Resultado(int eventos, int votos, int confirmaciones, int sesiones) {}

    private final EventoRepository eventos;
    private final ConfirmacionRecargaRepository confirmaciones;
    private final SesionRepository sesiones;
    private final Clock reloj;

    public LimpiezaDatos(EventoRepository eventos, ConfirmacionRecargaRepository confirmaciones,
            SesionRepository sesiones, Clock reloj) {
        this.eventos = eventos;
        this.confirmaciones = confirmaciones;
        this.sesiones = sesiones;
        this.reloj = reloj;
    }

    /** Todos los días a las 03:30 de Tarija, cuando casi nadie usa la app. */
    @Scheduled(cron = "${churoviaje.limpieza.cron:0 30 3 * * *}", zone = "America/La_Paz")
    @Transactional
    public Resultado limpiar() {
        Instant ahora = reloj.instant();
        Instant limite = ahora.minus(CONSERVACION);
        EventoRepository.Borrados borrados = eventos.borrarTerminadosAntesDe(limite, ahora);
        int viejas = confirmaciones.borrarAnterioresA(limite);
        int vencidas = sesiones.borrarVencidas(ahora);
        Resultado resultado = new Resultado(borrados.eventos(), borrados.votos(), viejas, vencidas);
        LOG.info("Limpieza diaria: {} eventos y {} votos de más de 30 días, {} confirmaciones de recarga y {} sesiones "
                + "vencidas", resultado.eventos(), resultado.votos(), resultado.confirmaciones(), resultado.sesiones());
        return resultado;
    }
}
