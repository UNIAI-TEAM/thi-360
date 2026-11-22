package vn.unicom.thi360.attempt.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.unicom.thi360.attempt.domain.EssayGrade;
import vn.unicom.thi360.attempt.domain.EssayGradeState;

@Repository
public interface EssayGradeRepository extends JpaRepository<EssayGrade, UUID> {

    Optional<EssayGrade> findByAttemptItemIdAndSupersededByIsNull(UUID attemptItemId);

    List<EssayGrade> findByAttemptItemIdAndSupersededByIsNotNullOrderByClaimedAtAscIdAsc(UUID attemptItemId);

    @Query("""
            SELECT g FROM EssayGrade g
            JOIN AttemptItem i ON i.id = g.attemptItemId
            WHERE i.attemptId = :attemptId
              AND g.supersededBy IS NULL
              AND g.state = :state
            """)
    List<EssayGrade> findActiveByAttemptIdAndState(
            @Param("attemptId") UUID attemptId, @Param("state") EssayGradeState state);

    @Query("""
            SELECT g FROM EssayGrade g
            WHERE g.attemptItemId IN :itemIds
              AND g.supersededBy IS NULL
            """)
    List<EssayGrade> findActiveByAttemptItemIdIn(@Param("itemIds") Collection<UUID> itemIds);
}
