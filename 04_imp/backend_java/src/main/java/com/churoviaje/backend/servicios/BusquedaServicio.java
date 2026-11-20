package com.churoviaje.backend.servicios;

import com.churoviaje.backend.dominio.Surtidor;
import com.churoviaje.backend.dominio.SurtidorRepository;
import com.churoviaje.backend.dominio.Ubicacion;
import com.churoviaje.backend.dominio.busqueda.CategoriaLugar;
import com.churoviaje.backend.dominio.busqueda.LugarRepository;
import com.churoviaje.backend.dominio.busqueda.TextoBusqueda;
import com.churoviaje.backend.dto.LugaresRespuesta;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Búsqueda de destinos (D-47, CU-03): calles y lugares de OSM (V10) más los surtidores del catálogo (V2). Primero los
 * que empiezan con lo escrito, luego los más cercanos al conductor.
 */
@Service
public class BusquedaServicio {

    /** Cuántos resultados devuelve, como mucho. */
    public static final int RESULTADOS = 10;
    /** Cuántos lugares se piden a la base antes de ordenar por coincidencia. */
    private static final int CANDIDATOS = 60;
    /** Un texto con más palabras no es una búsqueda razonable de un destino. */
    private static final int PALABRAS_MAXIMAS = 8;

    private final LugarRepository lugares;
    private final SurtidorRepository surtidores;

    public BusquedaServicio(LugarRepository lugares, SurtidorRepository surtidores) {
        this.lugares = lugares;
        this.surtidores = surtidores;
    }

    private record Candidato(LugaresRespuesta.LugarEncontrado lugar, int coincidencia, Double distancia) {}

    @Transactional(readOnly = true)
    public LugaresRespuesta buscar(String texto, Ubicacion cerca) {
        String buscado = TextoBusqueda.normalizar(texto);
        List<String> palabras = TextoBusqueda.palabras(texto).stream().limit(PALABRAS_MAXIMAS).toList();
        List<Candidato> candidatos = new ArrayList<>();
        for (LugarRepository.Encontrado e : lugares.buscar(palabras, cerca, CANDIDATOS)) {
            candidatos.add(new Candidato(LugaresRespuesta.LugarEncontrado.de(e.nombre(), e.categoria(), e.ubicacion(),
                    e.distanciaM()), TextoBusqueda.cercania(e.nombreNormalizado(), buscado), e.distanciaM()));
        }
        for (Surtidor s : surtidores.todos()) {
            String nombre = TextoBusqueda.normalizar(s.nombre());
            List<String> nombres = Stream.concat(Stream.of(nombre), s.alias().stream().map(TextoBusqueda::normalizar))
                    .toList();
            if (nombres.stream().anyMatch(n -> TextoBusqueda.coincide(n, palabras))) {
                Double distancia = cerca == null ? null : distanciaM(cerca, s.ubicacion());
                int coincidencia = nombres.stream().mapToInt(n -> TextoBusqueda.cercania(n, buscado)).min().orElse(2);
                candidatos.add(new Candidato(LugaresRespuesta.LugarEncontrado.de(s.nombre(), CategoriaLugar.SURTIDOR,
                        s.ubicacion(), distancia), coincidencia, distancia));
            }
        }
        candidatos.sort(Comparator.comparingInt(Candidato::coincidencia)
                .thenComparing(Candidato::distancia, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(c -> c.lugar().nombre()));
        return new LugaresRespuesta(candidatos.stream().limit(RESULTADOS).map(Candidato::lugar).toList());
    }

    /** Haversine: con unas decenas de surtidores no hace falta ir a PostGIS. */
    static double distanciaM(Ubicacion a, Ubicacion b) {
        double lat1 = Math.toRadians(a.latitud().doubleValue());
        double lat2 = Math.toRadians(b.latitud().doubleValue());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.longitud().doubleValue() - a.longitud().doubleValue());
        double h = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * 6_371_008.8 * Math.asin(Math.min(1, Math.sqrt(h)));
    }
}
