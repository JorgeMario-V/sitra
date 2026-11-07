package co.coop.sitra.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** HU-02: saldo leído en vivo del núcleo (SITRA no lo guarda). */
public record SaldoResponse(String numeroCuenta, BigDecimal saldo, LocalDateTime consultadoEn) {
}
