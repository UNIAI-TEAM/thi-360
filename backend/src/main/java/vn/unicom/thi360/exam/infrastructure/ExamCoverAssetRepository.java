package vn.unicom.thi360.exam.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.unicom.thi360.exam.domain.ExamCoverAsset;

public interface ExamCoverAssetRepository extends JpaRepository<ExamCoverAsset, UUID> {
    Optional<ExamCoverAsset> findByIdAndExamId(UUID id, UUID examId);
}
