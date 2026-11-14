package com.churoviaje.backend.servicios;

import com.churoviaje.backend.dominio.EstadoCombustible;
import com.churoviaje.backend.dominio.EstadoCombustibleRepository;
import com.churoviaje.backend.dominio.Surtidor;
import com.churoviaje.backend.dominio.SurtidorRepository;
import com.churoviaje.backend.dominio.catalogo.CatalogoRepository;
import com.churoviaje.backend.dominio.catalogo.TipoCombustible;
import com.churoviaje.backend.dominio.catalogo.ValorFila;
import com.churoviaje.backend.dominio.monitoreo.AvisoRecibido;
import com.churoviaje.backend.dominio.monitoreo.CatalogoDeAvisos;
import com.churoviaje.backend.dominio.monitoreo.ResultadoAnalisis;
import com.churoviaje.backend.dto.CatalogoMonitoreo;
import com.churoviaje.backend.dto.ResultadoMonitoreo;
import com.churoviaje.backend.errores.ExcepcionRechazo;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El monitoreo de WhatsApp del lado del backend (CU-06 pasos 5 a 7): entrega el catálogo que el servicio le pasa al
 * LLM y recibe el resultado de cada lote. Solo lo llama el servicio con su credencial (RN-06.14, SecurityConfig).
 */
@Service
public class MonitoreoServicio {

    private static final Logger LOG = LoggerFactory.getLogger(MonitoreoServicio.class);

    private final SurtidorRepository surtidores;
    private final CatalogoRepository catalogo;
    private final EstadoCombustibleRepository estados;
    private final Clock reloj;

    public MonitoreoServicio(SurtidorRepository surtidores, CatalogoRepository catalogo,
            EstadoCombustibleRepository estados, Clock reloj) {
        this.surtidores = surtidores;
        this.catalogo = catalogo;
        this.estados = estados;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public CatalogoMonitoreo catalogo() {
        List<CatalogoMonitoreo.SurtidorMonitoreo> lista = surtidores.todos().stream()
                .map(s -> new CatalogoMonitoreo.SurtidorMonitoreo(s.nombre(), s.alias()))
                .toList();
        List<String> combustibles = catalogo.tiposDeCombustible().stream()
                .filter(TipoCombustible::seleccionable) // «Desconocido» es un valor fijo de RN-06.8
                .map(TipoCombustible::nombre)
                .sorted()
                .toList();
        List<String> filas = Arrays.stream(ValorFila.values()).map(ValorFila::name).toList();
        return new CatalogoMonitoreo(lista, combustibles, filas);
    }

    /**
     * Valida cada aviso del lote (RN-06.15) y guarda los válidos como datos de WhatsApp, cada uno si es más reciente
     * que el que hay (RN-06.16, RN-06.17). Todo el lote va en una transacción.
     */
    @Transactional
    public ResultadoMonitoreo registrar(JsonNode cuerpo) {
        JsonNode avisos = cuerpo == null ? null : cuerpo.get("avisos");
        if (avisos == null || !avisos.isArray()) {
            throw invalida("El resultado no trae la lista de avisos.");
        }
        if (avisos.size() > ResultadoAnalisis.MAXIMO_AVISOS) {
            throw invalida("Un lote tiene como máximo " + ResultadoAnalisis.MAXIMO_AVISOS + " avisos.");
        }
        List<AvisoRecibido> recibidos = new ArrayList<>(avisos.size());
        for (JsonNode aviso : avisos) {
            recibidos.add(leer(aviso));
        }
        Instant ahora = reloj.instant();
        List<Surtidor> todos = surtidores.todos();
        ResultadoAnalisis resultado = ResultadoAnalisis.validar(recibidos,
                new CatalogoDeAvisos(todos, catalogo.tiposDeCombustible()), ahora);
        int cambiados = 0;
        for (EstadoCombustible estado : resultado.estados()) {
            if (estados.guardarSiEsMasReciente(estado, ahora)) {
                cambiados++;
            }
        }
        // Sin el texto de los mensajes ni nada de quien los envió (RNF-11).
        LOG.info("Lote de WhatsApp {}: {} avisos, {} aceptados, {} descartados, {} datos cambiados",
                cuerpo.path("lote").asText("sin fecha"), avisos.size(), resultado.aceptados(),
                resultado.descartados().size(), cambiados);
        return ResultadoMonitoreo.de(resultado);
    }

    private static AvisoRecibido leer(JsonNode aviso) {
        if (aviso == null || !aviso.isObject()) {
            return AvisoRecibido.SIN_FORMATO;
        }
        return new AvisoRecibido(texto(aviso, "surtidor"), texto(aviso, "combustible"), texto(aviso, "estado"),
                texto(aviso, "fila"), texto(aviso, "fecha"));
    }

    private static String texto(JsonNode aviso, String campo) {
        JsonNode valor = aviso.get(campo);
        return valor != null && valor.isTextual() ? valor.asText() : null;
    }

    private static ExcepcionRechazo invalida(String mensaje) {
        return new ExcepcionRechazo(HttpStatus.BAD_REQUEST, "PETICION_INVALIDA", mensaje);
    }
}
