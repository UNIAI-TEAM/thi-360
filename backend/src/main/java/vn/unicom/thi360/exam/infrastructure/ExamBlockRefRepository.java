package vn.unicom.thi360.exam.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.unicom.thi360.exam.domain.ExamBlockRef;

public interface ExamBlockRefRepository extends JpaRepository<ExamBlockRef, UUID> {
    List<ExamBlockRef> findByExamIdAndVersionNo(UUID examId, Integer versionNo);
    void deleteByExamIdAndVersionNo(UUID examId, Integer versionNo);
    List<ExamBlockRef> findBySubjectItemId(UUID subjectItemId);
    List<ExamBlockRef> findBySubjectItemIdAndVersionNoIsNull(UUID subjectItemId);
}
