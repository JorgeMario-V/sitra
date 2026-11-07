package co.coop.sitra.repository;

import co.coop.sitra.model.RegistroAuditoria;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RegistroAuditoriaRepository extends JpaRepository<RegistroAuditoria, Long> {

    /** HU-03: por cuenta y rango de fechas (usa el índice idx_auditoria_cuenta_fecha). */
    List<RegistroAuditoria> findByNumeroCuentaAndOcurridoEnBetweenOrderByIdAsc(
            String numeroCuenta, LocalDateTime desde, LocalDateTime hasta);

    List<RegistroAuditoria> findByOcurridoEnBetweenOrderByIdAsc(LocalDateTime desde, LocalDateTime hasta);

    /**
     * HU-03: verificación de integridad. Recalcula en MariaDB la huella de cada fila con la
     * misma fórmula del trigger trg_bitacora_huella (V4) y revisa que la cadena no esté rota.
     */
    @Query(nativeQuery = true, value = """
            SELECT r.id AS id,
                   IF(r.hash_anterior = COALESCE(LAG(r.hash_actual) OVER (ORDER BY r.id), REPEAT('0', 64)),
                      'OK', 'ROTA') AS cadena,
                   IF(r.hash_actual = SHA2(CONCAT_WS('|', r.hash_anterior, r.cliente_api_id,
                        COALESCE(r.transferencia_id, '-'), r.numero_cuenta, r.operacion, r.resultado,
                        COALESCE(r.referencia_externa, '-'), COALESCE(r.monto, '-'), COALESCE(r.motivo, '-'),
                        r.ip_origen, r.ocurrido_en), 256),
                      'OK', 'ALTERADO') AS huella
            FROM registro_auditoria r
            ORDER BY r.id
            """)
    List<VerificacionFila> verificarCadena();
}
