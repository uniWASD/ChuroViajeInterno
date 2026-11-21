package com.churoviaje.backend.servicios;

import com.churoviaje.backend.dominio.Conductor;
import com.churoviaje.backend.dominio.ConductorRepository;
import com.churoviaje.backend.dominio.ConfirmacionRecarga;
import com.churoviaje.backend.dominio.ConfirmacionRecargaRepository;
import com.churoviaje.backend.dominio.EstadoCombustible;
import com.churoviaje.backend.dominio.EstadoCombustibleRepository;
import com.churoviaje.backend.dominio.SurtidorRepository;
import com.churoviaje.backend.dominio.Ubicacion;
import com.churoviaje.backend.dominio.catalogo.CatalogoRepository;
import com.churoviaje.backend.dominio.catalogo.TipoCombustible;
import com.churoviaje.backend.dto.SurtidoresRespuesta;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.churoviaje.backend.errores.ExcepcionRechazo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta de surtidores (CU-05): los ordena por cercanía en PostGIS (RN-05.2) y de cada uno muestra, por tipo, el
 * dato vigente más reciente de los tres orígenes (RN-05.3, RN-05.5). Son pocas decenas de surtidores: se leen todos
 * los estados en una consulta y se eligen en {@link EstadoCombustible#paraMostrar}.
 */
@Service
public class SurtidorServicio {

    private final SurtidorRepository surtidores;
    private final EstadoCombustibleRepository estados;
    private final CatalogoRepository catalogo;
    private final Clock reloj;
    private final ConductorRepository conductores;
    private final ConfirmacionRecargaRepository confirmaciones;

    public SurtidorServicio(SurtidorRepository surtidores, EstadoCombustibleRepository estados,
            CatalogoRepository catalogo, Clock reloj, ConductorRepository conductores,
            ConfirmacionRecargaRepository confirmaciones) {
        this.surtidores = surtidores;
        this.estados = estados;
        this.catalogo = catalogo;
        this.reloj = reloj;
        this.conductores = conductores;
        this.confirmaciones = confirmaciones;
    }

    /**
     * CU-05, HU-12: registra que el conductor recargó {@code codigoTipo} en el surtidor, si la parada está junto a él
     * (RN-05.9, RN-05.13) y no lo confirmó hace poco (RN-05.14). Deja ese combustible disponible con origen
     * Conductores (RN-05.11). Todo en una transacción y con el conductor bloqueado, así dos confirmaciones que llegan
     * juntas no pasan las dos.
     */
    @Transactional
    public ConfirmacionRecarga confirmarRecarga(long conductorId, long surtidorId, String codigoTipo,
            Ubicacion parada) {
        Instant ahora = reloj.instant();
        Conductor conductor = conductores.buscarYBloquear(conductorId)
                .orElseThrow(() -> new ExcepcionRechazo(HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO",
                        "Inicia sesión para continuar."));
        if (conductor.isSimulado()) {
            // D-48: estado_combustible no tiene marca de simulado; un fantasma no puede pisar un dato real
            throw new ExcepcionRechazo(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO",
                    "Un usuario fantasma no confirma recargas.");
        }
        var cercano = surtidores.buscarConDistancia(surtidorId, parada)
                .orElseThrow(() -> new ExcepcionRechazo(HttpStatus.NOT_FOUND, "SURTIDOR_NO_ENCONTRADO",
                        "Ese surtidor no existe."));
        TipoCombustible tipo = catalogo.tiposDeCombustible().stream()
                .filter(t -> t.codigo().equals(codigoTipo))
                .findFirst()
                .orElseThrow(() -> new ExcepcionRechazo(HttpStatus.BAD_REQUEST, "PETICION_INVALIDA",
                        "Ese tipo de combustible no existe."));
        ConfirmacionRecarga confirmacion = ConfirmacionRecarga.confirmar(conductorId, cercano.surtidor(), tipo,
                cercano.distanciaM(), ahora); // RN-05.9, RN-05.13
        if (confirmaciones.existeDesde(conductorId, surtidorId, tipo.codigo(), confirmacion.repetidaDesde())) {
            throw ConfirmacionRecarga.repetida(); // RN-05.14
        }
        confirmaciones.guardar(confirmacion);
        estados.guardarSiEsMasReciente(confirmacion.comoEstado(), ahora); // RN-05.11, RN-05.12
        return confirmacion;
    }

    @Transactional(readOnly = true)
    public SurtidoresRespuesta consultar(Ubicacion desde) {
        Instant ahora = reloj.instant();
        Map<String, String> nombres = catalogo.tiposDeCombustible().stream()
                .collect(Collectors.toMap(TipoCombustible::codigo, TipoCombustible::nombre));
        Map<Long, List<EstadoCombustible>> porSurtidor = estados.todos().stream()
                .collect(Collectors.groupingBy(EstadoCombustible::surtidorId));
        List<SurtidoresRespuesta.SurtidorConEstado> lista = surtidores.porCercania(desde).stream()
                .map(c -> SurtidoresRespuesta.SurtidorConEstado.de(c.surtidor(), c.distanciaM(),
                        EstadoCombustible.paraMostrar(porSurtidor.getOrDefault(c.surtidor().id(), List.of()), ahora)
                                .stream()
                                .sorted(Comparator.comparing(EstadoCombustible::tipoCodigo))
                                .map(e -> SurtidoresRespuesta.Combustible.de(e, nombres))
                                .toList()))
                .toList();
        return new SurtidoresRespuesta(lista);
    }
}
