package vn.unicom.thi360.questionbank.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionImportRepository extends JpaRepository<QuestionImport, UUID> {

    /** Lịch sử nhập mới nhất trước, chặn ở 200 dòng (spec 4.5, quyết định 8.9). */
    List<QuestionImport> findTop200ByOrderByImportedAtDesc();
}
