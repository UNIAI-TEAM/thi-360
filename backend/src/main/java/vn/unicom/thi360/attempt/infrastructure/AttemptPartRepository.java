package vn.unicom.thi360.attempt.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.unicom.thi360.attempt.domain.AttemptPart;

@Repository
public interface AttemptPartRepository extends JpaRepository<AttemptPart, UUID> {

    Optional<AttemptPart> findByIdAndAttemptId(UUID id, UUID attemptId);

    List<AttemptPart> findByAttemptIdOrderByOrdAsc(UUID attemptId);

    List<AttemptPart> findByAttemptIdInOrderByOrdAsc(Collection<UUID> attemptIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM AttemptPart p WHERE p.attemptId = :attemptId ORDER BY p.ord ASC")
    List<AttemptPart> findByAttemptIdOrderByOrdAscForUpdate(@Param("attemptId") UUID attemptId);
}
