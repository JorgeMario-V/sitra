package co.coop.sitra.service;

import co.coop.sitra.dto.RegistroAuditoriaResponse;
import co.coop.sitra.dto.VerificacionResponse;
import co.coop.sitra.exception.SolicitudRechazadaException;
import co.coop.sitra.model.ClienteApi;
import co.coop.sitra.model.Operacion;
import co.coop.sitra.model.RegistroAuditoria;
import co.coop.sitra.model.Resultado;
import co.coop.sitra.model.Rol;
import co.coop.sitra.model.Transferencia;
import co.coop.sitra.repository.RegistroAuditoriaRepository;
import co.coop.sitra.repository.VerificacionFila;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bitácora (HU-03). Las transferencias guardadas las registra la base de datos con triggers
 * (ADR-003); este servicio registra lo que la base de datos no ve: intentos rechazados antes de
 * crear la transferencia, consultas de saldo y consultas del auditor.
 */
@Service
public class AuditoriaService {

    private final RegistroAuditoriaRepository bitacora;
    private final IdentificacionService identificacion;

    public AuditoriaService(RegistroAuditoriaRepository bitacora, IdentificacionService identificacion) {
        this.bitacora = bitacora;
        this.identificacion = identificacion;
    }

    /**
     * Escribe una fila en su PROPIA transacción (REQUIRES_NEW): aunque la operación principal se
     * deshaga por el rechazo, esta fila queda guardada.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(ClienteApi cliente, Transferencia transferencia, String numeroCuenta, Operacion operacion,
                          Resultado resultado, String referenciaExterna, BigDecimal monto, String motivo,
                          String ipOrigen) {
        bitacora.save(RegistroAuditoria.de(cliente, transferencia, numeroCuenta, operacion, resultado,
                referenciaExterna, monto, motivo, ipOrigen));
    }

    /** HU-03: consulta por cuenta y rango de fechas. Si no se dan fechas: últimos 30 días. */
    @Transactional
    public List<RegistroAuditoriaResponse> consultar(String clientId, String cuenta, LocalDate desde,
                                                     LocalDate hasta, String ipOrigen) {
        ClienteApi auditor = identificacion.exigirRol(clientId, Rol.AUDITOR);
        LocalDate fin = hasta != null ? hasta : LocalDate.now();
        LocalDate inicio = desde != null ? desde : fin.minusDays(30);
        if (inicio.isAfter(fin)) {
            throw new SolicitudRechazadaException(HttpStatus.BAD_REQUEST, "La fecha 'desde' es posterior a 'hasta'");
        }
        List<RegistroAuditoria> filas = (cuenta == null || cuenta.isBlank())
                ? bitacora.findByOcurridoEnBetweenOrderByIdAsc(inicio.atStartOfDay(), fin.atTime(LocalTime.MAX))
                : bitacora.findByNumeroCuentaAndOcurridoEnBetweenOrderByIdAsc(cuenta, inicio.atStartOfDay(),
                        fin.atTime(LocalTime.MAX));
        List<RegistroAuditoriaResponse> respuesta = filas.stream().map(RegistroAuditoriaResponse::de).toList();
        // También queda registrado quién consultó la bitácora
        registrar(auditor, null, cuenta == null || cuenta.isBlank() ? "*" : cuenta, Operacion.CONSULTA_AUDITORIA,
                Resultado.EXITOSA, null, null, "Consulta del " + inicio + " al " + fin, ipOrigen);
        return respuesta;
    }

    /** HU-03: verifica que nadie haya alterado ni borrado filas de la bitácora. */
    @Transactional(readOnly = true)
    public VerificacionResponse verificar(String clientId) {
        identificacion.exigirRol(clientId, Rol.AUDITOR);
        List<VerificacionFila> filas = bitacora.verificarCadena();
        List<Long> alteradas = new ArrayList<>();
        List<Long> cadenaRota = new ArrayList<>();
        for (VerificacionFila fila : filas) {
            if (!"OK".equals(fila.getHuella())) {
                alteradas.add(fila.getId());
            }
            if (!"OK".equals(fila.getCadena())) {
                cadenaRota.add(fila.getId());
            }
        }
        return new VerificacionResponse(alteradas.isEmpty() && cadenaRota.isEmpty(), filas.size(), alteradas,
                cadenaRota);
    }
}
