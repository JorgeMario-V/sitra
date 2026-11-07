package co.coop.sitra.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Quién usa SITRA: la billetera externa o el auditor. Tabla cliente_api. */
@Entity
@Table(name = "cliente_api")
public class ClienteApi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(name = "client_id", nullable = false, unique = true, length = 80)
    private String clientId;

    /** Hash BCrypt del secreto: el secreto nunca se guarda. */
    @Column(name = "secret_hash", nullable = false)
    private String secretHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    @Column(nullable = false)
    private Boolean activo = Boolean.TRUE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ClienteApi() {
        // requerido por JPA
    }

    public ClienteApi(String nombre, String clientId, String secretHash, Rol rol) {
        this.nombre = nombre;
        this.clientId = clientId;
        this.secretHash = secretHash;
        this.rol = rol;
        this.createdAt = LocalDateTime.now().withNano(0);
        this.updatedAt = this.createdAt;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getClientId() { return clientId; }
    public String getSecretHash() { return secretHash; }
    public Rol getRol() { return rol; }
    public Boolean getActivo() { return activo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
