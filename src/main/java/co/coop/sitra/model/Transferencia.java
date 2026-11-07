package co.coop.sitra.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidad principal de la HU-01. Tabla transferencia.
 * Cada INSERT o UPDATE deja su fila en la bitácora por medio de los triggers de la BD (ADR-003).
 */
@Entity
@Table(name = "transferencia")
public class Transferencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cliente_api_id", nullable = false)
    private ClienteApi clienteApi;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cuenta_origen_id", nullable = false)
    private CuentaAutorizada cuentaOrigen;

    @Column(name = "cuenta_destino", nullable = false, length = 30)
    private String cuentaDestino;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoTransferencia estado;

    /** Código de operación de la billetera: llave de idempotencia (UNIQUE). */
    @Column(name = "referencia_externa", nullable = false, unique = true, length = 80)
    private String referenciaExterna;

    @Column(name = "ip_origen", length = 45)
    private String ipOrigen;

    @Column(name = "comprobante_nucleo", length = 80)
    private String comprobanteNucleo;

    @Column(name = "motivo_rechazo", length = 255)
    private String motivoRechazo;

    @Column(nullable = false)
    private Boolean activo = Boolean.TRUE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Transferencia() {
        // requerido por JPA
    }

    public static Transferencia nueva(ClienteApi cliente, CuentaAutorizada origen, String cuentaDestino,
                                      BigDecimal monto, String referenciaExterna, String ipOrigen) {
        Transferencia t = new Transferencia();
        t.clienteApi = cliente;
        t.cuentaOrigen = origen;
        t.cuentaDestino = cuentaDestino;
        t.monto = monto;
        t.referenciaExterna = referenciaExterna;
        t.ipOrigen = ipOrigen;
        t.estado = EstadoTransferencia.PENDIENTE;
        return t;
    }

    @PrePersist
    void alCrear() {
        LocalDateTime ahora = LocalDateTime.now().withNano(0);
        createdAt = ahora;
        updatedAt = ahora;
        if (estado == null) {
            estado = EstadoTransferencia.PENDIENTE;
        }
    }

    @PreUpdate
    void alActualizar() {
        updatedAt = LocalDateTime.now().withNano(0);
    }

    // ---- Reglas de estado (negocio dentro del modelo) ----

    public void confirmar(String comprobante) {
        estado = EstadoTransferencia.CONFIRMADA;
        comprobanteNucleo = comprobante;
        motivoRechazo = null;
    }

    public void rechazar(String motivo) {
        estado = EstadoTransferencia.RECHAZADA;
        motivoRechazo = recortar(motivo);
    }

    public void marcarFallida(String motivo) {
        estado = EstadoTransferencia.FALLIDA;
        motivoRechazo = recortar(motivo);
    }

    /** Solo se puede corregir y reintentar lo que NO movió dinero (ADR-004). */
    public boolean puedeReintentarse() {
        return estado == EstadoTransferencia.RECHAZADA || estado == EstadoTransferencia.FALLIDA;
    }

    /** Corrige los datos antes de volver a enviarla al núcleo (PUT). */
    public void corregir(String nuevaCuentaDestino, BigDecimal nuevoMonto, String nuevaIp) {
        if (!puedeReintentarse()) {
            throw new IllegalStateException("Solo se corrige una transferencia RECHAZADA o FALLIDA");
        }
        cuentaDestino = nuevaCuentaDestino;
        monto = nuevoMonto;
        ipOrigen = nuevaIp;
        estado = EstadoTransferencia.PENDIENTE;
        motivoRechazo = null;
    }

    /** ¿Ya se descontó dinero en el núcleo? Entonces anular exige un reverso. */
    public boolean movioDinero() {
        return estado == EstadoTransferencia.CONFIRMADA;
    }

    /** Borrado lógico: nunca se borra la fila. */
    public void anular() {
        estado = EstadoTransferencia.ANULADA;
        activo = Boolean.FALSE;
    }

    private static String recortar(String texto) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= 255 ? texto : texto.substring(0, 255);
    }

    public Long getId() { return id; }
    public ClienteApi getClienteApi() { return clienteApi; }
    public CuentaAutorizada getCuentaOrigen() { return cuentaOrigen; }
    public String getCuentaDestino() { return cuentaDestino; }
    public BigDecimal getMonto() { return monto; }
    public EstadoTransferencia getEstado() { return estado; }
    public String getReferenciaExterna() { return referenciaExterna; }
    public String getIpOrigen() { return ipOrigen; }
    public String getComprobanteNucleo() { return comprobanteNucleo; }
    public String getMotivoRechazo() { return motivoRechazo; }
    public Boolean getActivo() { return activo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
