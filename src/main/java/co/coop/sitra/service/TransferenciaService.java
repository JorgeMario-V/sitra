package co.coop.sitra.service;

import co.coop.sitra.dto.ActualizarTransferenciaRequest;
import co.coop.sitra.dto.CrearTransferenciaRequest;
import co.coop.sitra.exception.SolicitudRechazadaException;
import co.coop.sitra.integration.NucleoConector;
import co.coop.sitra.integration.NucleoNoDisponibleException;
import co.coop.sitra.integration.ResultadoNucleo;
import co.coop.sitra.model.ClienteApi;
import co.coop.sitra.model.CuentaAutorizada;
import co.coop.sitra.model.Operacion;
import co.coop.sitra.model.Resultado;
import co.coop.sitra.model.Rol;
import co.coop.sitra.model.Transferencia;
import co.coop.sitra.repository.CuentaAutorizadaRepository;
import co.coop.sitra.repository.TransferenciaRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Capa de negocio de la HU-01: CRUD punta a punta de Transferencia.
 * Habla con el núcleo SOLO por la interfaz NucleoConector (ADR-001).
 */
@Service
public class TransferenciaService {

    private final TransferenciaRepository transferencias;
    private final CuentaAutorizadaRepository cuentas;
    private final IdentificacionService identificacion;
    private final AuditoriaService auditoria;
    private final NucleoConector nucleo;

    public TransferenciaService(TransferenciaRepository transferencias, CuentaAutorizadaRepository cuentas,
                                IdentificacionService identificacion, AuditoriaService auditoria,
                                NucleoConector nucleo) {
        this.transferencias = transferencias;
        this.cuentas = cuentas;
        this.identificacion = identificacion;
        this.auditoria = auditoria;
        this.nucleo = nucleo;
    }

    // =========================== CREATE ===========================
    /**
     * Registra la transferencia. Devuelve la transferencia con su estado final:
     * CONFIRMADA (201), RECHAZADA por el núcleo (422) o FALLIDA porque el núcleo no respondió (503).
     * Los rechazos previos (400, 404, 409) no crean transferencia, pero quedan en la bitácora.
     */
    @Transactional
    public Transferencia registrar(String clientId, CrearTransferenciaRequest datos, String ip) {
        ClienteApi cliente = identificacion.exigirRol(clientId, Rol.BILLETERA);
        String cuenta = datos.cuentaOrigen();
        if (vacio(cuenta) || vacio(datos.cuentaDestino()) || vacio(datos.referenciaExterna()) || datos.monto() == null) {
            throw rechazo(cliente, null, cuenta, Operacion.TRANSFERENCIA, datos.referenciaExterna(), datos.monto(),
                    HttpStatus.BAD_REQUEST, "Faltan datos: cuentaOrigen, cuentaDestino, monto y referenciaExterna son obligatorios", ip);
        }
        if (datos.monto().compareTo(BigDecimal.ZERO) <= 0) {
            throw rechazo(cliente, null, cuenta, Operacion.TRANSFERENCIA, datos.referenciaExterna(), datos.monto(),
                    HttpStatus.BAD_REQUEST, "El monto debe ser mayor que cero", ip);
        }
        if (transferencias.existsByReferenciaExterna(datos.referenciaExterna())) {
            throw rechazo(cliente, null, cuenta, Operacion.TRANSFERENCIA, datos.referenciaExterna(), datos.monto(),
                    HttpStatus.CONFLICT, "El código de operación ya existe: la transferencia no se repite", ip);
        }
        CuentaAutorizada origen = cuentas.findByNumeroCuentaAndActivoTrue(cuenta).orElse(null);
        if (origen == null) {
            throw rechazo(cliente, null, cuenta, Operacion.TRANSFERENCIA, datos.referenciaExterna(), datos.monto(),
                    HttpStatus.NOT_FOUND, "La cuenta no está en la lista de cuentas autorizadas", ip);
        }

        Transferencia t = Transferencia.nueva(cliente, origen, datos.cuentaDestino(), datos.monto(),
                datos.referenciaExterna(), ip);
        ejecutarEnNucleo(t);
        try {
            return transferencias.save(t);   // INSERT: el trigger escribe la bitácora
        } catch (DataIntegrityViolationException e) {
            // Dos peticiones iguales al mismo tiempo: la llave UNIQUE de la base de datos frena la segunda
            throw rechazo(cliente, null, cuenta, Operacion.TRANSFERENCIA, datos.referenciaExterna(), datos.monto(),
                    HttpStatus.CONFLICT, "La base de datos rechazó la operación (código de operación repetido)", ip);
        }
    }

    // =========================== READ ===========================
    @Transactional(readOnly = true)
    public List<Transferencia> listar(String clientId) {
        ClienteApi cliente = identificacion.exigirRol(clientId, Rol.BILLETERA);
        return transferencias.findByClienteApi_IdAndActivoTrueOrderByIdDesc(cliente.getId());
    }

    @Transactional(readOnly = true)
    public Transferencia obtener(String clientId, Long id) {
        ClienteApi cliente = identificacion.exigirRol(clientId, Rol.BILLETERA);
        return buscarPropia(cliente, id);
    }

    // =========================== UPDATE ===========================
    /**
     * Corregir y reintentar (ADR-004): solo una transferencia que NO movió dinero (RECHAZADA o FALLIDA).
     * Conserva su código de operación, así el reintento no se confunde con una operación nueva.
     */
    @Transactional
    public Transferencia actualizar(String clientId, Long id, ActualizarTransferenciaRequest datos, String ip) {
        ClienteApi cliente = identificacion.exigirRol(clientId, Rol.BILLETERA);
        Transferencia t = buscarPropia(cliente, id);
        String cuenta = t.getCuentaOrigen().getNumeroCuenta();
        if (!t.puedeReintentarse()) {
            throw rechazo(cliente, t, cuenta, Operacion.TRANSFERENCIA, t.getReferenciaExterna(), t.getMonto(),
                    HttpStatus.CONFLICT, "Solo se corrige y reintenta una transferencia RECHAZADA o FALLIDA; esta está "
                            + t.getEstado(), ip);
        }
        if (datos == null || vacio(datos.cuentaDestino()) || datos.monto() == null
                || datos.monto().compareTo(BigDecimal.ZERO) <= 0) {
            throw rechazo(cliente, t, cuenta, Operacion.TRANSFERENCIA, t.getReferenciaExterna(),
                    datos == null ? null : datos.monto(), HttpStatus.BAD_REQUEST,
                    "Para corregir se necesitan cuentaDestino y un monto mayor que cero", ip);
        }
        t.corregir(datos.cuentaDestino(), datos.monto(), ip);
        ejecutarEnNucleo(t);
        return transferencias.save(t);   // UPDATE: el trigger escribe la bitácora
    }

    // =========================== DELETE (lógico) ===========================
    /**
     * Anula la transferencia (borrado lógico: estado ANULADA y activo = false).
     * Si ya había movido dinero, primero pide al núcleo el reverso; si el núcleo no responde,
     * no se anula nada (503) y la transferencia sigue CONFIRMADA.
     */
    @Transactional
    public void anular(String clientId, Long id, String ip) {
        ClienteApi cliente = identificacion.exigirRol(clientId, Rol.BILLETERA);
        Transferencia t = buscarPropia(cliente, id);
        String cuenta = t.getCuentaOrigen().getNumeroCuenta();
        if (t.movioDinero()) {
            ResultadoNucleo reverso;
            try {
                reverso = nucleo.reversarTransferencia(cuenta, t.getMonto(), t.getComprobanteNucleo());
            } catch (NucleoNoDisponibleException e) {
                auditoria.registrar(cliente, t, cuenta, Operacion.ANULACION, Resultado.FALLIDA,
                        t.getReferenciaExterna(), t.getMonto(), e.getMessage(), ip);
                throw new SolicitudRechazadaException(HttpStatus.SERVICE_UNAVAILABLE,
                        "El núcleo no respondió: la transferencia sigue CONFIRMADA");
            }
            if (!reverso.aprobada()) {
                throw rechazo(cliente, t, cuenta, Operacion.ANULACION, t.getReferenciaExterna(), t.getMonto(),
                        HttpStatus.CONFLICT, "El núcleo no aceptó el reverso: " + reverso.motivo(), ip);
            }
        }
        t.anular();
        transferencias.save(t);          // UPDATE: el trigger registra la ANULACION
    }

    // =========================== apoyo ===========================
    private void ejecutarEnNucleo(Transferencia t) {
        try {
            ResultadoNucleo r = nucleo.ejecutarTransferencia(t.getCuentaOrigen().getNumeroCuenta(), t.getMonto());
            if (r.aprobada()) {
                t.confirmar(r.comprobante());
            } else {
                t.rechazar(r.motivo());
            }
        } catch (NucleoNoDisponibleException e) {
            t.marcarFallida(e.getMessage());   // AC-02: nunca queda a medias
        }
    }

    private Transferencia buscarPropia(ClienteApi cliente, Long id) {
        return transferencias.findByIdAndClienteApi_IdAndActivoTrue(id, cliente.getId())
                .orElseThrow(() -> new SolicitudRechazadaException(HttpStatus.NOT_FOUND,
                        "No existe una transferencia activa con id " + id));
    }

    /** Deja el intento RECHAZADO en la bitácora (transacción propia) y devuelve el error HTTP. */
    private SolicitudRechazadaException rechazo(ClienteApi cliente, Transferencia t, String cuenta, Operacion operacion,
                                                String referencia, BigDecimal monto, HttpStatus estado,
                                                String motivo, String ip) {
        auditoria.registrar(cliente, t, cuenta, operacion, Resultado.RECHAZADA, referencia, monto, motivo, ip);
        return new SolicitudRechazadaException(estado, motivo);
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
