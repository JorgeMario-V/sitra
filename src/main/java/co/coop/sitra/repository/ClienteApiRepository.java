package co.coop.sitra.repository;

import co.coop.sitra.model.ClienteApi;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteApiRepository extends JpaRepository<ClienteApi, Long> {

    Optional<ClienteApi> findByClientIdAndActivoTrue(String clientId);
}
