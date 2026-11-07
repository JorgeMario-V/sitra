package co.coop.sitra.dto;

import java.math.BigDecimal;

/** PUT /api/v1/transferencias/{id}: corregir y reintentar una transferencia RECHAZADA o FALLIDA (ADR-004). */
public record ActualizarTransferenciaRequest(String cuentaDestino, BigDecimal monto) {
}
