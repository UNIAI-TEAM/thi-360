package vn.unicom.thi360.exam.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.unicom.thi360.exam.domain.ExamTemplate;

public interface ExamTemplateRepository extends JpaRepository<ExamTemplate, String> {
    List<ExamTemplate> findByKindCodeAndScopeAndActiveTrue(String kindCode, String scope);
    List<ExamTemplate> findByKindCodeAndScopeAndPartCodeAndActiveTrue(String kindCode, String scope, String partCode);
    List<ExamTemplate> findByKindCodeAndScopeAndSubjectCodeAndActiveTrue(String kindCode, String scope, String subjectCode);
}
