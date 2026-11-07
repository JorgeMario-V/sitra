package co.coop.sitra.controller;

import co.coop.sitra.dto.SolicitudToken;
import co.coop.sitra.dto.TokenResponse;
import co.coop.sitra.service.IdentificacionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Único punto abierto: el cliente cambia su clientId + secreto por un token (200) o recibe 401. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final IdentificacionService identificacion;

    public AuthController(IdentificacionService identificacion) {
        this.identificacion = identificacion;
    }

    @PostMapping("/token")
    public TokenResponse token(@RequestBody SolicitudToken solicitud) {
        return identificacion.emitirToken(solicitud.clientId(), solicitud.secreto());
    }
}
