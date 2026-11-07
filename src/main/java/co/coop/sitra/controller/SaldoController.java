package co.coop.sitra.controller;

import co.coop.sitra.dto.SaldoResponse;
import co.coop.sitra.security.FiltroJwt;
import co.coop.sitra.service.CuentaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HU-02: la billetera consulta el saldo de una cuenta de la lista blanca. */
@RestController
@RequestMapping("/api/v1/cuentas")
public class SaldoController {

    private final CuentaService servicio;

    public SaldoController(CuentaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/{numeroCuenta}/saldo")
    public SaldoResponse saldo(@RequestAttribute(FiltroJwt.ATRIBUTO_CLIENTE) String clientId,
                               @PathVariable String numeroCuenta, HttpServletRequest peticion) {
        return servicio.consultarSaldo(clientId, numeroCuenta, peticion.getRemoteAddr());
    }
}
