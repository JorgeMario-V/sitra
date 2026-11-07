package co.coop.sitra.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Asociado de la cooperativa, dueño de las cuentas. Tabla asociado. */
@Entity
@Table(name = "asociado")
public class Asociado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String documento;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false)
    private Boolean activo = Boolean.TRUE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Asociado() {
        // requerido por JPA
    }

    public Asociado(String documento, String nombre) {
        this.documento = documento;
        this.nombre = nombre;
        this.createdAt = LocalDateTime.now().withNano(0);
        this.updatedAt = this.createdAt;
    }

    public Long getId() { return id; }
    public String getDocumento() { return documento; }
    public String getNombre() { return nombre; }
    public Boolean getActivo() { return activo; }
}
