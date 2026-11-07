package co.coop.sitra.controller;

import co.coop.sitra.dto.ActualizarTransferenciaRequest;
import co.coop.sitra.dto.CrearTransferenciaRequest;
import co.coop.sitra.dto.TransferenciaResponse;
import co.coop.sitra.model.Transferencia;
import co.coop.sitra.security.FiltroJwt;
import co.coop.sitra.service.TransferenciaService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Capa de presentación de la HU-01: CRUD punta a punta de Transferencia.
 * Solo recibe la petición y traduce el resultado a código HTTP; las reglas están en el servicio.
 */
@RestController
@RequestMapping("/api/v1/transferencias")
public class TransferenciaController {

    private final TransferenciaService servicio;

    public TransferenciaController(TransferenciaService servicio) {
        this.servicio = servicio;
    }

    /** CREATE: 201 CONFIRMADA · 422 RECHAZADA por el núcleo · 503 FALLIDA (núcleo sin respuesta). */
    @PostMapping
    public ResponseEntity<TransferenciaResponse> registrar(@RequestAttribute(FiltroJwt.ATRIBUTO_CLIENTE) String clientId,
                                                           @RequestBody CrearTransferenciaRequest datos,
                                                           HttpServletRequest peticion) {
        Transferencia t = servicio.registrar(clientId, datos, peticion.getRemoteAddr());
        return ResponseEntity.status(codigoSegunEstado(t, HttpStatus.CREATED)).body(TransferenciaResponse.de(t));
    }

    /** READ: solo las activas del cliente que pregunta. */
    @GetMapping
    public List<TransferenciaResponse> listar(@RequestAttribute(FiltroJwt.ATRIBUTO_CLIENTE) String clientId) {
        return servicio.listar(clientId).stream().map(TransferenciaResponse::de).toList();
    }

    @GetMapping("/{id}")
    public TransferenciaResponse obtener(@RequestAttribute(FiltroJwt.ATRIBUTO_CLIENTE) String clientId,
                                         @PathVariable Long id) {
        return TransferenciaResponse.de(servicio.obtener(clientId, id));
    }

    /** UPDATE: corregir y reintentar una RECHAZADA o FALLIDA (ADR-004). 200 · 422 · 503 · 409. */
    @PutMapping("/{id}")
    public ResponseEntity<TransferenciaResponse> actualizar(@RequestAttribute(FiltroJwt.ATRIBUTO_CLIENTE) String clientId,
                                                            @PathVariable Long id,
                                                            @RequestBody ActualizarTransferenciaRequest datos,
                                                            HttpServletRequest peticion) {
        Transferencia t = servicio.actualizar(clientId, id, datos, peticion.getRemoteAddr());
        return ResponseEntity.status(codigoSegunEstado(t, HttpStatus.OK)).body(TransferenciaResponse.de(t));
    }

    /** DELETE lógico: 204 y la fila queda con activo = false y estado ANULADA. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> anular(@RequestAttribute(FiltroJwt.ATRIBUTO_CLIENTE) String clientId,
                                       @PathVariable Long id, HttpServletRequest peticion) {
        servicio.anular(clientId, id, peticion.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    private static HttpStatus codigoSegunEstado(Transferencia t, HttpStatus siConfirmada) {
        return switch (t.getEstado()) {
            case RECHAZADA -> HttpStatus.UNPROCESSABLE_ENTITY;
            case FALLIDA -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> siConfirmada;
        };
    }
}
