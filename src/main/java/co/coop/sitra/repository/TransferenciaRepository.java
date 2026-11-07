package co.coop.sitra.repository;

import co.coop.sitra.model.Transferencia;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferenciaRepository extends JpaRepository<Transferencia, Long> {

    boolean existsByReferenciaExterna(String referenciaExterna);

    /** READ: solo las activas (borrado lógico) y solo las del cliente que pregunta. */
    List<Transferencia> findByClienteApi_IdAndActivoTrueOrderByIdDesc(Long clienteApiId);

    Optional<Transferencia> findByIdAndClienteApi_IdAndActivoTrue(Long id, Long clienteApiId);
}
