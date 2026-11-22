package com.churoviaje.backend.servicios;

import com.churoviaje.backend.config.AmbienteSimulacion;
import com.churoviaje.backend.dominio.DatosSimuladosRepository;
import com.churoviaje.backend.dominio.DatosSimuladosRepository.Borrado;
import com.churoviaje.backend.errores.ExcepcionRechazo;
import com.churoviaje.backend.motor.AvisoTrafico;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mantenimiento de los datos del simulador (CU-07). */
@Service
public class SimulacionServicio {

    /** Lo que responde el borrado: cuántas filas simuladas se borraron de cada tabla. */
    public record ResultadoBorrado(int votos, int eventos, int conductores) {}

    private final AmbienteSimulacion ambiente;
    private final DatosSimuladosRepository datos;
    private final RegistroPruebasCarga registro;
    private final ApplicationEventPublisher avisos;

    public SimulacionServicio(AmbienteSimulacion ambiente, DatosSimuladosRepository datos,
            RegistroPruebasCarga registro, ApplicationEventPublisher avisos) {
        this.ambiente = ambiente;
        this.datos = datos;
        this.registro = registro;
        this.avisos = avisos;
    }

    /**
     * RN-07.8 y RN-07.9: borra todas las filas simuladas, en una sola transacción, sin tocar una real. Solo existe
     * donde las credenciales de prueba están habilitadas; en {@code prod} responde 403. Los eventos simulados que
     * seguían en el mapa dejan de pesar en las rutas (RN-02.15).
     */
    @Transactional
    public ResultadoBorrado borrarDatosSimulados(String credencial) {
        if (!ambiente.sesionesDePruebaHabilitadas()) {
            throw new ExcepcionRechazo(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO",
                    "El borrado de datos simulados no está habilitado en este ambiente.");
        }
        if (!ambiente.credencialCorrecta(credencial)) {
            throw new ExcepcionRechazo(HttpStatus.UNAUTHORIZED, "CREDENCIAL_PRUEBA_INVALIDA",
                    "La credencial de prueba no es válida.");
        }
        Borrado borrado = datos.borrarTodo();
        borrado.eventosQueSeMostraban().forEach(id -> avisos.publishEvent(AvisoTrafico.retira(id)));
        registro.anotar("Borrado de datos simulados: votos=%d eventos=%d conductores=%d"
                .formatted(borrado.votos(), borrado.eventos(), borrado.conductores()));
        return new ResultadoBorrado(borrado.votos(), borrado.eventos(), borrado.conductores());
    }
}
