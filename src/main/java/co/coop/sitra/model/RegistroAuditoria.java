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
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;

/**
 * Bitácora de auditoría (evidencia). Tabla registro_auditoria.
 * La base de datos impide modificarla o borrarla (triggers de V2) y calcula sola la huella.
 * {@code @Immutable} hace que JPA tampoco intente actualizarla. Sin campo "activo": no se borra.
 */
@Entity
@Immutable
@Table(name = "registro_auditoria")
public class RegistroAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Quién. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "cliente_api_id", nullable = false)
    private ClienteApi clienteApi;

    /** Vacío cuando el intento no alcanzó a crear la transferencia. */
    @ManyToOne
    @JoinColumn(name = "transferencia_id")
    private Transferencia transferencia;

    /** Sobre qué cuenta. */
    @Column(name = "numero_cuenta", nullable = false, length = 30)
    private String numeroCuenta;

    /** Qué. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Operacion operacion;

    /** Con qué resultado. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Resultado resultado;

    @Column(name = "referencia_externa", length = 80)
    private String referenciaExterna;

    @Column(precision = 14, scale = 2)
    private BigDecimal monto;

    @Column(length = 255)
    private String motivo;

    /** Desde dónde. */
    @Column(name = "ip_origen", nullable = false, length = 45)
    private String ipOrigen;

    /** Cuándo. */
    @Column(name = "ocurrido_en", nullable = false)
    private LocalDateTime ocurridoEn;

    /** Las huellas las calcula el trigger trg_bitacora_huella: Java no las escribe. */
    @Column(name = "hash_anterior", insertable = false, updatable = false)
    private String hashAnterior;

    @Column(name = "hash_actual", insertable = false, updatable = false)
    private String hashActual;

    protected RegistroAuditoria() {
        // requerido por JPA
    }

    public static RegistroAuditoria de(ClienteApi cliente, Transferencia transferencia, String numeroCuenta,
                                       Operacion operacion, Resultado resultado, String referenciaExterna,
                                       BigDecimal monto, String motivo, String ipOrigen) {
        RegistroAuditoria r = new RegistroAuditoria();
        r.clienteApi = cliente;
        r.transferencia = transferencia;
        r.numeroCuenta = recortar(numeroCuenta == null || numeroCuenta.isBlank() ? "SIN-CUENTA" : numeroCuenta, 30);
        r.operacion = operacion;
        r.resultado = resultado;
        r.referenciaExterna = recortar(referenciaExterna, 80);
        r.monto = monto;
        r.motivo = recortar(motivo, 255);
        r.ipOrigen = ipOrigen == null || ipOrigen.isBlank() ? "local" : recortar(ipOrigen, 45);
        r.ocurridoEn = LocalDateTime.now().withNano(0);
        return r;
    }

    private static String recortar(String texto, int maximo) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }

    public Long getId() { return id; }
    public ClienteApi getClienteApi() { return clienteApi; }
    public Transferencia getTransferencia() { return transferencia; }
    public String getNumeroCuenta() { return numeroCuenta; }
    public Operacion getOperacion() { return operacion; }
    public Resultado getResultado() { return resultado; }
    public String getReferenciaExterna() { return referenciaExterna; }
    public BigDecimal getMonto() { return monto; }
    public String getMotivo() { return motivo; }
    public String getIpOrigen() { return ipOrigen; }
    public LocalDateTime getOcurridoEn() { return ocurridoEn; }
    public String getHashAnterior() { return hashAnterior; }
    public String getHashActual() { return hashActual; }
}
