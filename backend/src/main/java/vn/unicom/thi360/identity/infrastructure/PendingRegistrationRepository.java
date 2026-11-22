package vn.unicom.thi360.identity.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.identity.domain.PendingRegistration;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PendingRegistration p WHERE p.id = :id")
    Optional<PendingRegistration> findByIdForUpdate(@Param("id") UUID id);
}
