package co.coop.sitra.dto;

import java.math.BigDecimal;

/** POST /api/v1/transferencias (HU-01). */
public record CrearTransferenciaRequest(String cuentaOrigen, String cuentaDestino, BigDecimal monto,
                                        String referenciaExterna) {
}
