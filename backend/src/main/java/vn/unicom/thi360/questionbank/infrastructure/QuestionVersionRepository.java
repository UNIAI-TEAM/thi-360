package vn.unicom.thi360.questionbank.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.unicom.thi360.questionbank.domain.QuestionVersion;

public interface QuestionVersionRepository extends JpaRepository<QuestionVersion, QuestionVersion.PK> {

    List<QuestionVersion> findByQuestionIdOrderByVersionNo(UUID questionId);

    Optional<QuestionVersion> findByQuestionIdAndVersionNo(UUID questionId, Integer versionNo);

    /** Bản chụp của nhiều câu ở cùng một version_no (ví dụ các câu con của một bộ). */
    List<QuestionVersion> findByQuestionIdInAndVersionNo(Collection<UUID> questionIds, Integer versionNo);
}
