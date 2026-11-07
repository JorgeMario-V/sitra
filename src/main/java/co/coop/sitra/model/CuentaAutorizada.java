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
import java.time.LocalDateTime;

/**
 * Lista blanca: solo estas cuentas son visibles para la billetera.
 * NO guarda saldo: el saldo siempre se lee del núcleo (única fuente de verdad).
 */
@Entity
@Table(name = "cuenta_autorizada")
public class CuentaAutorizada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "asociado_id", nullable = false)
    private Asociado asociado;

    @Column(name = "numero_cuenta", nullable = false, unique = true, length = 30)
    private String numeroCuenta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoCuenta tipo;

    @Column(nullable = false)
    private Boolean activo = Boolean.TRUE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected CuentaAutorizada() {
        // requerido por JPA
    }

    public CuentaAutorizada(Asociado asociado, String numeroCuenta, TipoCuenta tipo) {
        this.asociado = asociado;
        this.numeroCuenta = numeroCuenta;
        this.tipo = tipo;
        this.createdAt = LocalDateTime.now().withNano(0);
        this.updatedAt = this.createdAt;
    }

    public Long getId() { return id; }
    public Asociado getAsociado() { return asociado; }
    public String getNumeroCuenta() { return numeroCuenta; }
    public TipoCuenta getTipo() { return tipo; }
    public Boolean getActivo() { return activo; }
}
