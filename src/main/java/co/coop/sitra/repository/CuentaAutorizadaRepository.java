package co.coop.sitra.repository;

import co.coop.sitra.model.CuentaAutorizada;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CuentaAutorizadaRepository extends JpaRepository<CuentaAutorizada, Long> {

    /** Lista blanca: solo cuentas activas. */
    Optional<CuentaAutorizada> findByNumeroCuentaAndActivoTrue(String numeroCuenta);
}
