package vn.unicom.thi360.questionbank.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.unicom.thi360.questionbank.domain.QuestionTag;

public interface QuestionTagRepository extends JpaRepository<QuestionTag, QuestionTag.PK> {

    List<QuestionTag> findByQuestionId(UUID questionId);

    List<QuestionTag> findByQuestionIdIn(Collection<UUID> questionIds);

    void deleteByQuestionId(UUID questionId);
}
