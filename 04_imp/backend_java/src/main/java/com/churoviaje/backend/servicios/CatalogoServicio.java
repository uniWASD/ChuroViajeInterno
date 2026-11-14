package com.churoviaje.backend.servicios;

import com.churoviaje.backend.dominio.catalogo.CatalogoRepository;
import com.churoviaje.backend.dominio.catalogo.CatalogosRespuesta;
import com.churoviaje.backend.dominio.catalogo.CatalogosRespuesta.TiempoReporteDto;
import com.churoviaje.backend.dominio.catalogo.CatalogosRespuesta.TipoCombustibleDto;
import com.churoviaje.backend.dominio.catalogo.CatalogosRespuesta.TipoEventoDto;
import com.churoviaje.backend.dominio.catalogo.CatalogosRespuesta.ValorFilaDto;
import com.churoviaje.backend.dominio.catalogo.TiempoReporte;
import com.churoviaje.backend.dominio.catalogo.TipoCombustible;
import com.churoviaje.backend.dominio.catalogo.ValorFila;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/** Arma lo que la app necesita para no usar valores propios (T-094, D-06). */
@Service
public class CatalogoServicio {

    /** Orden estable para la app: los reportables primero y la congestion al final. Un tipo nuevo va despues. */
    private static final List<String> ORDEN_EVENTOS =
            List.of("ACCIDENTE", "CONTROL_POLICIAL", "BLOQUEO", "OTRO", "CONGESTION");
    private static final List<String> ORDEN_COMBUSTIBLES =
            List.of("GASOLINA", "DIESEL", "GASOLINA_PREMIUM", "DIESEL_ULS", TipoCombustible.DESCONOCIDO);

    private final CatalogoRepository repositorio;

    public CatalogoServicio(CatalogoRepository repositorio) {
        this.repositorio = repositorio;
    }

    public CatalogosRespuesta obtenerCatalogos() {
        return new CatalogosRespuesta(
                repositorio.tiposDeEventoParaMostrar().stream()
                        .sorted(porOrden(ORDEN_EVENTOS, TipoEventoDto::codigo))
                        .toList(),
                repositorio.tiposDeCombustibleParaMostrar().stream()
                        .sorted(porOrden(ORDEN_COMBUSTIBLES, TipoCombustibleDto::codigo))
                        .toList(),
                TiempoReporte.OPCIONES.stream()
                        .map(t -> new TiempoReporteDto(t.minutos(), t.nombre(), t.porDefecto()))
                        .toList(),
                Arrays.stream(ValorFila.values())
                        .map(v -> new ValorFilaDto(v.name(), v.nombre()))
                        .toList());
    }

    private static <T> Comparator<T> porOrden(List<String> orden, java.util.function.Function<T, String> codigo) {
        return Comparator.<T>comparingInt(t -> {
            int posicion = orden.indexOf(codigo.apply(t));
            return posicion < 0 ? Integer.MAX_VALUE : posicion;
        }).thenComparing(codigo);
    }
}
