package co.coop.sitra.dto;

import co.coop.sitra.model.RegistroAuditoria;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Una fila de la bitácora: quién, qué, sobre qué cuenta, con qué resultado, desde dónde y cuándo. */
public record RegistroAuditoriaResponse(Long id, String quien, Long transferenciaId, String numeroCuenta,
                                        String operacion, String resultado, String referenciaExterna,
                                        BigDecimal monto, String motivo, String ipOrigen,
                                        LocalDateTime ocurridoEn, String hashActual) {

    public static RegistroAuditoriaResponse de(RegistroAuditoria r) {
        return new RegistroAuditoriaResponse(r.getId(), r.getClienteApi().getClientId(),
                r.getTransferencia() == null ? null : r.getTransferencia().getId(), r.getNumeroCuenta(),
                r.getOperacion().name(), r.getResultado().name(), r.getReferenciaExterna(), r.getMonto(),
                r.getMotivo(), r.getIpOrigen(), r.getOcurridoEn(), r.getHashActual());
    }
}
