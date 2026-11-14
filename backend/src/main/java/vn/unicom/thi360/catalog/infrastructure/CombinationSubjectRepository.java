package vn.unicom.thi360.catalog.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.unicom.thi360.catalog.domain.CombinationSubject;
import vn.unicom.thi360.catalog.domain.CombinationSubjectId;

public interface CombinationSubjectRepository extends JpaRepository<CombinationSubject, CombinationSubjectId> {

    List<CombinationSubject> findByIdCombinationVersionIdOrderByPositionAsc(UUID combinationVersionId);

    void deleteAllByIdCombinationVersionId(UUID combinationVersionId);
}
