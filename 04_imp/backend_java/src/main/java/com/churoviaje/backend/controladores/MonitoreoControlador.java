package com.churoviaje.backend.controladores;

import com.churoviaje.backend.dto.CatalogoMonitoreo;
import com.churoviaje.backend.dto.ResultadoMonitoreo;
import com.churoviaje.backend.servicios.MonitoreoServicio;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /monitoreo/*} (CU-06): solo con la credencial del servicio de WhatsApp/LLM. Un JWT de conductor responde 403
 * y sin credencial, 401 (RN-06.14, D-13, SecurityConfig).
 */
@RestController
@RequestMapping("/monitoreo")
public class MonitoreoControlador {

    private final MonitoreoServicio monitoreo;

    public MonitoreoControlador(MonitoreoServicio monitoreo) {
        this.monitoreo = monitoreo;
    }

    @GetMapping("/surtidores")
    public CatalogoMonitoreo catalogo() {
        return monitoreo.catalogo();
    }

    /** Siempre 200 con lo aceptado y lo descartado; el cuerpo lo valida el servicio aviso por aviso (RN-06.15). */
    @PostMapping("/resultados")
    public ResultadoMonitoreo resultados(@RequestBody JsonNode cuerpo) {
        return monitoreo.registrar(cuerpo);
    }
}
