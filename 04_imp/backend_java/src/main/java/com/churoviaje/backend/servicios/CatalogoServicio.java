package com.churoviaje.backend.servicios;

import com.churoviaje.backend.dominio.catalogo.TiempoReporte;
import com.churoviaje.backend.dominio.catalogo.TipoCombustible;
import com.churoviaje.backend.dominio.catalogo.TipoEvento;
import com.churoviaje.backend.dominio.catalogo.ValorFila;
import com.churoviaje.backend.dto.CatalogosRespuesta;
import com.churoviaje.backend.dto.CatalogosRespuesta.TiempoReporteDto;
import com.churoviaje.backend.dto.CatalogosRespuesta.TipoCombustibleDto;
import com.churoviaje.backend.dto.CatalogosRespuesta.TipoEventoDto;
import com.churoviaje.backend.dto.CatalogosRespuesta.ValorFilaDto;
import com.churoviaje.backend.repositorios.CatalogoRepositorio;
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

    private final CatalogoRepositorio repositorio;

    public CatalogoServicio(CatalogoRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    public CatalogosRespuesta obtenerCatalogos() {
        return new CatalogosRespuesta(
                repositorio.tiposDeEvento().stream()
                        .sorted(porOrden(ORDEN_EVENTOS, TipoEvento::codigo))
                        .map(t -> new TipoEventoDto(t.codigo(), t.nombre(), t.color(), t.vigenciaMinutos(), t.reportable()))
                        .toList(),
                repositorio.tiposDeCombustible().stream()
                        .sorted(porOrden(ORDEN_COMBUSTIBLES, TipoCombustible::codigo))
                        .map(t -> new TipoCombustibleDto(t.codigo(), t.nombre(), t.seleccionable()))
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
