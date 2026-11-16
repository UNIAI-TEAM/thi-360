package vn.unicom.thi360.questionbank.infrastructure;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionImportUploadRepository extends JpaRepository<QuestionImportUpload, UUID> {

    /**
     * Khóa dòng tệp tải lên (SELECT … FOR UPDATE) suốt transaction commit: commit thứ hai
     * chờ rồi thấy COMMITTED (spec 4.5, quyết định 8.10).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from QuestionImportUpload u where u.id = :id")
    Optional<QuestionImportUpload> findByIdForUpdate(@Param("id") UUID id);

    /** Tệp hết hạn ở trạng thái không còn được commit. */
    List<QuestionImportUpload> findByStatusInAndExpiresAtBefore(
            Collection<QuestionImportUpload.Status> statuses, Instant expiredBefore);
}
