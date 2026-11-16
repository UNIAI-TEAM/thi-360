package vn.unicom.thi360.questionbank.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.unicom.thi360.questionbank.domain.QuestionKnowledge;

public interface QuestionKnowledgeRepository
        extends JpaRepository<QuestionKnowledge, QuestionKnowledge.PK> {

    List<QuestionKnowledge> findByQuestionId(UUID questionId);

    List<QuestionKnowledge> findByQuestionIdIn(Collection<UUID> questionIds);

    void deleteByQuestionId(UUID questionId);
}
