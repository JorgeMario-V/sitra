package co.coop.sitra.dto;

import co.coop.sitra.model.Transferencia;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferenciaResponse(Long id, String cuentaOrigen, String cuentaDestino, BigDecimal monto,
                                    String estado, String referenciaExterna, String comprobanteNucleo,
                                    String motivoRechazo, LocalDateTime creadaEn, LocalDateTime actualizadaEn) {

    public static TransferenciaResponse de(Transferencia t) {
        return new TransferenciaResponse(t.getId(), t.getCuentaOrigen().getNumeroCuenta(), t.getCuentaDestino(),
                t.getMonto(), t.getEstado().name(), t.getReferenciaExterna(), t.getComprobanteNucleo(),
                t.getMotivoRechazo(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
