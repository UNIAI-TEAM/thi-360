package vn.unicom.thi360.exam.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.exam.domain.ExamKindProfile;

public interface ExamKindProfileRepository extends JpaRepository<ExamKindProfile, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ExamKindProfile p where p.examItemId = :examItemId")
    Optional<ExamKindProfile> findByExamItemIdForUpdate(@Param("examItemId") UUID examItemId);
}
