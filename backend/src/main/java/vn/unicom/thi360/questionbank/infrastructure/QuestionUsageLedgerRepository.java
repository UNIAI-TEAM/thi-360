package vn.unicom.thi360.questionbank.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.questionbank.domain.QuestionUsageLedger;

public interface QuestionUsageLedgerRepository
        extends JpaRepository<QuestionUsageLedger, QuestionUsageLedger.PK> {

    boolean existsByQuestionId(UUID questionId);

    List<QuestionUsageLedger> findByQuestionIdOrderByFirstAtDesc(UUID questionId);

    @Query("select count(distinct l.examId) from QuestionUsageLedger l where l.questionId = :questionId")
    long countDistinctExamsByQuestionId(@Param("questionId") UUID questionId);
}
