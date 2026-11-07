package co.coop.sitra.integration;

import java.math.BigDecimal;

/**
 * Única puerta hacia el núcleo legado (ADR-001). El servicio solo conoce esta interfaz:
 * no sabe si habla con el núcleo real o con el simulador.
 * Todas las operaciones lanzan NucleoNoDisponibleException si el núcleo no responde.
 */
public interface NucleoConector {

    /** Pide al núcleo ejecutar (debitar) la transferencia. */
    ResultadoNucleo ejecutarTransferencia(String numeroCuenta, BigDecimal monto);

    /** Pide al núcleo devolver el dinero de una transferencia confirmada (anulación). */
    ResultadoNucleo reversarTransferencia(String numeroCuenta, BigDecimal monto, String comprobante);

    /** Saldo leído en vivo del núcleo: SITRA nunca lo guarda. Devuelve null si la cuenta no existe. */
    BigDecimal consultarSaldo(String numeroCuenta);
}
