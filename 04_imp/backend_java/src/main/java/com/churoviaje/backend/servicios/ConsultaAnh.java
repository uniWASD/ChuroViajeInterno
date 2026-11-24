package com.churoviaje.backend.servicios;

import com.churoviaje.backend.config.PropiedadesAnh;
import com.churoviaje.backend.dominio.EstadoCombustibleRepository;
import com.churoviaje.backend.dominio.EstadoCombustibleRepository.ResultadoAnh;
import com.churoviaje.backend.dominio.SurtidorRepository;
import com.churoviaje.backend.dominio.anh.LecturaAnh;
import com.churoviaje.backend.dominio.anh.ReporteAnh;
import com.churoviaje.backend.dominio.anh.RespuestaDescartada;
import com.churoviaje.backend.dominio.cobertura.CoberturaManchaUrbana;
import com.churoviaje.backend.infraestructura.anh.ClienteAnh;
import com.churoviaje.backend.infraestructura.anh.FalloAnh;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * HU-14 (CU-09): la tarea periódica que consulta a ANH y actualiza el estado de los surtidores vinculados. Es el único
 * componente de ChuroViaje que llama a ANH (RN-09.1, D-29). Cada ciclo deja su resumen en el registro (RN-09.19), sin
 * el cuerpo de las respuestas ni nada de las estaciones ignoradas, salvo las candidatas a entrar al catálogo (RN-09.9).
 */
@Component
public class ConsultaAnh {

    private static final Logger LOG = LoggerFactory.getLogger(ConsultaAnh.class);

    /** Los combustibles que se piden y su tipo en el catálogo: 0, Gasolina; 1, Diesel (05_contratos.md). */
    private static final Map<Integer, String> PRODUCTOS = Map.of(0, "GASOLINA", 1, "DIESEL");

    /** Lo que pasó con una ruta del ciclo, para el registro (RN-09.19) y las pruebas. */
    public record Ruta(String ruta, Integer estadoHttp, int intentos, int llegaron, int aceptadas, int ignoradas,
            int sinDato, String motivo) {
        public boolean bien() {
            return motivo == null;
        }
    }

    /** El resumen de un ciclo. {@code consulto} es falso si no se pidió nada a ANH. */
    public record Resumen(Instant hora, boolean consulto, List<Ruta> rutas, List<Integer> candidatas) {}

    private final ClienteAnh cliente;
    private final SurtidorRepository surtidores;
    private final EstadoCombustibleRepository estados;
    private final CoberturaManchaUrbana mancha;
    private final PropiedadesAnh propiedades;
    private final Clock reloj;
    private volatile Instant noConsultarAntesDe;

    public ConsultaAnh(ClienteAnh cliente, SurtidorRepository surtidores, EstadoCombustibleRepository estados,
            CoberturaManchaUrbana mancha, PropiedadesAnh propiedades, Clock reloj) {
        this.cliente = cliente;
        this.surtidores = surtidores;
        this.estados = estados;
        this.mancha = mancha;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    /** RN-09.1: cada P-29, contando desde que termina el ciclo anterior. */
    @Scheduled(fixedDelayString = "${churoviaje.anh.ciclo:PT10M}",
            initialDelayString = "${churoviaje.anh.espera-inicial:PT2M}")
    public void programada() {
        if (!propiedades.habilitada()) {
            return;
        }
        try {
            consultar();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            // Un error inesperado no debe apagar la tarea: el próximo ciclo vuelve a intentar.
            LOG.error("ANH: el ciclo terminó con un error inesperado: {}", e.toString());
        }
    }

    /** Un ciclo completo. Las pruebas lo llaman directamente. */
    public Resumen consultar() throws InterruptedException {
        Instant inicio = reloj.instant();
        if (noConsultarAntesDe != null && inicio.isBefore(noConsultarAntesDe)) {
            LOG.info("ANH: no se consulta hasta {}, como pidió Retry-After (RN-09.4)", noConsultarAntesDe);
            return new Resumen(inicio, false, List.of(), List.of());
        }
        List<Ruta> rutas = new ArrayList<>();
        Integer tarija = buscarTarija(rutas);
        List<Integer> candidatas = new ArrayList<>();
        if (tarija != null) {
            Map<Integer, Long> vinculados = surtidores.vinculadosAnh();
            for (Map.Entry<Integer, String> producto : new java.util.TreeMap<>(PRODUCTOS).entrySet()) {
                if (noConsultarAntesDe != null && reloj.instant().isBefore(noConsultarAntesDe)) {
                    break;
                }
                rutas.add(consultarProducto(tarija, producto.getKey(), producto.getValue(), vinculados, candidatas));
            }
        }
        Resumen resumen = new Resumen(inicio, true, List.copyOf(rutas), List.copyOf(candidatas));
        registrar(resumen);
        return resumen;
    }

    /** RN-09.2: el id de Tarija en el catálogo; si el primero no sirve por algo que no sea TLS, el alternativo. */
    private Integer buscarTarija(List<Ruta> rutas) throws InterruptedException {
        for (String ruta : List.of("departamentos/?producto=0", "departamentos/?producto=1")) {
            ClienteAnh.Respuesta respuesta;
            try {
                respuesta = cliente.get(ruta);
            } catch (FalloAnh e) {
                rutas.add(fallo(e));
                if (e.motivo() == FalloAnh.Motivo.CERTIFICADO || e.pausarHasta() != null) {
                    return null;
                }
                continue;
            }
            try {
                int id = ReporteAnh.idDeTarija(respuesta.cuerpo());
                rutas.add(new Ruta(ruta, respuesta.estadoHttp(), respuesta.intentos(), 0, 0, 0, 0, null));
                return id;
            } catch (RespuestaDescartada e) {
                rutas.add(new Ruta(ruta, respuesta.estadoHttp(), respuesta.intentos(), 0, 0, 0, 0, e.getMessage()));
            }
        }
        return null;
    }

    private Ruta consultarProducto(int departamento, int producto, String tipo, Map<Integer, Long> vinculados,
            List<Integer> candidatas) throws InterruptedException {
        String ruta = "estaciones/?departamento=" + departamento + "&producto=" + producto;
        ClienteAnh.Respuesta respuesta;
        try {
            respuesta = cliente.get(ruta);
        } catch (FalloAnh e) {
            // RN-09.15 y RN-09.16: nada cambia para este combustible; el otro sigue.
            return fallo(e);
        }
        ReporteAnh reporte;
        try {
            reporte = ReporteAnh.leer(respuesta.cuerpo(), departamento, producto);
        } catch (RespuestaDescartada e) {
            return new Ruta(ruta, respuesta.estadoHttp(), respuesta.intentos(), -1, 0, 0, 0, e.getMessage());
        }
        Instant leidoEn = reloj.instant();
        int aceptadas = 0;
        int ignoradas = 0;
        int sinDato = 0;
        for (LecturaAnh lectura : reporte.lecturas()) {
            // RN-09.8: fuera de la mancha urbana (o sin ubicación) se ignora.
            if (lectura.ubicacion() == null || !lectura.ubicacion().estaDentroDeManchaUrbana(mancha)) {
                ignoradas++;
                continue;
            }
            Long surtidor = vinculados.get(lectura.anhId());
            if (surtidor == null) {
                // RN-09.9: dentro de la mancha y sin vincular: candidata a entrar al catálogo.
                ignoradas++;
                if (!candidatas.contains(lectura.anhId())) {
                    candidatas.add(lectura.anhId());
                    LOG.info("ANH: la estación {} ({}) está en la mancha urbana y no tiene surtidor en el catálogo",
                            lectura.anhId(), lectura.nombre() == null ? "sin nombre" : lectura.nombre());
                }
                continue;
            }
            var estado = lectura.comoEstado(surtidor, tipo, leidoEn);
            if (estado.isEmpty()) {
                // RN-09.11 y RN-09.13: sin saldo o sin fecha no se crea ni se cambia ningún estado.
                sinDato++;
                continue;
            }
            ResultadoAnh resultado = estados.guardarDeAnh(estado.get(), leidoEn);
            if (resultado != ResultadoAnh.MAS_VIEJO) {
                aceptadas++;
            } else {
                ignoradas++;
            }
        }
        return new Ruta(ruta, respuesta.estadoHttp(), respuesta.intentos(), reporte.lecturas().size(), aceptadas,
                ignoradas, sinDato, null);
    }

    private Ruta fallo(FalloAnh e) {
        if (e.pausarHasta() != null) {
            noConsultarAntesDe = e.pausarHasta();
        }
        String motivo = e.motivo() + (e.getMessage() == null ? "" : ": " + e.getMessage());
        return new Ruta(e.ruta(), e.estadoHttp(), e.intentos(), -1, 0, 0, 0, motivo);
    }

    /** RN-09.19: una línea por ruta, sin el cuerpo de las respuestas. */
    private static void registrar(Resumen resumen) {
        for (Ruta r : resumen.rutas()) {
            if (r.bien()) {
                LOG.info("ANH: {} -> HTTP {} en {} intento(s); llegaron {}, aceptadas {}, ignoradas {}, sin dato {}",
                        r.ruta(), r.estadoHttp(), r.intentos(), Math.max(r.llegaron(), 0), r.aceptadas(),
                        r.ignoradas(), r.sinDato());
            } else {
                LOG.warn("ANH: {} -> {} en {} intento(s); no cambia ningún estado. Motivo: {}", r.ruta(),
                        r.estadoHttp() == null ? "sin respuesta" : "HTTP " + r.estadoHttp(), r.intentos(), r.motivo());
            }
        }
    }

    /** Hasta cuándo no se consulta por un {@code Retry-After} largo; null si no hay pausa. */
    public Instant noConsultarAntesDe() {
        return noConsultarAntesDe;
    }
}
