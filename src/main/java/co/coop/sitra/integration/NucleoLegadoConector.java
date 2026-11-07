package co.coop.sitra.integration;

import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Conector real del núcleo legado. Deuda técnica declarada en el ADR-001: el protocolo del núcleo
 * está por confirmar con el cliente (archivo, base de datos o servicio). Cuando se confirme, se
 * implementa SOLO esta clase, con una espera máxima de 5 segundos (AC-02), y se activa con
 * SITRA_NUCLEO_MODO=legado. Mientras tanto responde como un núcleo no disponible (503).
 */
@Component
@ConditionalOnProperty(name = "sitra.nucleo.modo", havingValue = "legado")
public class NucleoLegadoConector implements NucleoConector {

    private static final String PENDIENTE = "Conector legado pendiente: protocolo del núcleo por confirmar con el cliente";

    @Override
    public ResultadoNucleo ejecutarTransferencia(String numeroCuenta, BigDecimal monto) {
        throw new NucleoNoDisponibleException(PENDIENTE);
    }

    @Override
    public ResultadoNucleo reversarTransferencia(String numeroCuenta, BigDecimal monto, String comprobante) {
        throw new NucleoNoDisponibleException(PENDIENTE);
    }

    @Override
    public BigDecimal consultarSaldo(String numeroCuenta) {
        throw new NucleoNoDisponibleException(PENDIENTE);
    }
}
