package com.churoviaje.backend.servicios;

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

    public SurtidorServicio(SurtidorRepository surtidores, EstadoCombustibleRepository estados,
            CatalogoRepository catalogo, Clock reloj) {
        this.surtidores = surtidores;
        this.estados = estados;
        this.catalogo = catalogo;
        this.reloj = reloj;
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
