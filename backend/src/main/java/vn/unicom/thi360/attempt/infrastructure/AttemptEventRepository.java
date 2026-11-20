package vn.unicom.thi360.attempt.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.unicom.thi360.attempt.domain.AttemptEvent;

@Repository
public interface AttemptEventRepository extends JpaRepository<AttemptEvent, UUID> {

    List<AttemptEvent> findByAttemptIdOrderByAtAscIdAsc(UUID attemptId);
}
