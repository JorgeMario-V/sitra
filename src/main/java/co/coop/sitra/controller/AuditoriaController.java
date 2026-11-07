package co.coop.sitra.controller;

import co.coop.sitra.dto.RegistroAuditoriaResponse;
import co.coop.sitra.dto.VerificacionResponse;
import co.coop.sitra.security.FiltroJwt;
import co.coop.sitra.service.AuditoriaService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HU-03: el auditor consulta y verifica la bitácora (solo rol AUDITOR; la billetera recibe 403). */
@RestController
@RequestMapping("/api/v1/auditoria")
public class AuditoriaController {

    private final AuditoriaService servicio;

    public AuditoriaController(AuditoriaService servicio) {
        this.servicio = servicio;
    }

    /** Ejemplo: /api/v1/auditoria?cuenta=AH-1001&desde=2026-11-01&hasta=2026-11-30 */
    @GetMapping
    public List<RegistroAuditoriaResponse> consultar(
            @RequestAttribute(FiltroJwt.ATRIBUTO_CLIENTE) String clientId,
            @RequestParam(name = "cuenta", required = false) String cuenta,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            HttpServletRequest peticion) {
        return servicio.consultar(clientId, cuenta, desde, hasta, peticion.getRemoteAddr());
    }

    /** Recalcula la cadena de huellas: integra = true si nadie alteró ni borró filas. */
    @GetMapping("/verificacion")
    public VerificacionResponse verificar(@RequestAttribute(FiltroJwt.ATRIBUTO_CLIENTE) String clientId) {
        return servicio.verificar(clientId);
    }
}
