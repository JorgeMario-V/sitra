package co.coop.sitra.service;

import co.coop.sitra.dto.SaldoResponse;
import co.coop.sitra.exception.SolicitudRechazadaException;
import co.coop.sitra.integration.NucleoConector;
import co.coop.sitra.integration.NucleoNoDisponibleException;
import co.coop.sitra.model.ClienteApi;
import co.coop.sitra.model.Operacion;
import co.coop.sitra.model.Resultado;
import co.coop.sitra.model.Rol;
import co.coop.sitra.repository.CuentaAutorizadaRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** HU-02: consulta de saldo. El saldo se lee en vivo del núcleo; SITRA nunca lo guarda. */
@Service
public class CuentaService {

    private final CuentaAutorizadaRepository cuentas;
    private final IdentificacionService identificacion;
    private final AuditoriaService auditoria;
    private final NucleoConector nucleo;

    public CuentaService(CuentaAutorizadaRepository cuentas, IdentificacionService identificacion,
                         AuditoriaService auditoria, NucleoConector nucleo) {
        this.cuentas = cuentas;
        this.identificacion = identificacion;
        this.auditoria = auditoria;
        this.nucleo = nucleo;
    }

    public SaldoResponse consultarSaldo(String clientId, String numeroCuenta, String ip) {
        ClienteApi cliente = identificacion.exigirRol(clientId, Rol.BILLETERA);
        if (cuentas.findByNumeroCuentaAndActivoTrue(numeroCuenta).isEmpty()) {
            auditoria.registrar(cliente, null, numeroCuenta, Operacion.CONSULTA_SALDO, Resultado.RECHAZADA, null, null,
                    "Cuenta fuera de la lista blanca", ip);
            throw new SolicitudRechazadaException(HttpStatus.NOT_FOUND,
                    "La cuenta no está en la lista de cuentas autorizadas");
        }
        BigDecimal saldo;
        try {
            saldo = nucleo.consultarSaldo(numeroCuenta);
        } catch (NucleoNoDisponibleException e) {
            auditoria.registrar(cliente, null, numeroCuenta, Operacion.CONSULTA_SALDO, Resultado.FALLIDA, null, null,
                    e.getMessage(), ip);
            throw new SolicitudRechazadaException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
        if (saldo == null) {
            auditoria.registrar(cliente, null, numeroCuenta, Operacion.CONSULTA_SALDO, Resultado.RECHAZADA, null, null,
                    "La cuenta no existe en el núcleo", ip);
            throw new SolicitudRechazadaException(HttpStatus.NOT_FOUND, "La cuenta no existe en el núcleo");
        }
        auditoria.registrar(cliente, null, numeroCuenta, Operacion.CONSULTA_SALDO, Resultado.EXITOSA, null, null,
                null, ip);
        return new SaldoResponse(numeroCuenta, saldo, LocalDateTime.now().withNano(0));
    }
}
