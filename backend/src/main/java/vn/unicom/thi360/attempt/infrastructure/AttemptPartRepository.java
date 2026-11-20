package vn.unicom.thi360.attempt.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.unicom.thi360.attempt.domain.AttemptPart;

@Repository
public interface AttemptPartRepository extends JpaRepository<AttemptPart, UUID> {

    Optional<AttemptPart> findByIdAndAttemptId(UUID id, UUID attemptId);

    List<AttemptPart> findByAttemptIdOrderByOrdAsc(UUID attemptId);
}
