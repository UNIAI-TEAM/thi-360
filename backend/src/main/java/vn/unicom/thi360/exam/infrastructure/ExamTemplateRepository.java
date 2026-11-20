package vn.unicom.thi360.exam.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.unicom.thi360.exam.domain.ExamTemplate;
import vn.unicom.thi360.exam.domain.ExamTemplateCheckFacts;

public interface ExamTemplateRepository extends JpaRepository<ExamTemplate, String> {
    List<ExamTemplate> findByKindCodeAndScopeAndActiveTrue(String kindCode, String scope);
    List<ExamTemplate> findByKindCodeAndScopeAndPartCodeAndActiveTrue(String kindCode, String scope, String partCode);
    List<ExamTemplate> findByKindCodeAndScopeAndSubjectCodeAndActiveTrue(String kindCode, String scope, String subjectCode);

    /**
     * The flag columns of the active standard SUBJECT templates of one kind and subject, without the structure JSON
     * (FU-BE15-R1-5): what the exam check needs to pick the template that decides publishing.
     */
    @Query("select new vn.unicom.thi360.exam.domain.ExamTemplateCheckFacts(t.code, t.defaultTemplate, t.partCode, "
            + "t.structureYear, t.publishable, t.fixedCounts, t.fixedBlockCounts, t.omittedLocked) "
            + "from ExamTemplate t where t.kindCode = ?1 and t.scope = 'SUBJECT' and t.subjectCode = ?2 "
            + "and t.active = true and t.buildMode = 'STANDARD'")
    List<ExamTemplateCheckFacts> findSubjectStandardFacts(String kindCode, String subjectCode);

    /** The flag columns of one template, active or retired (provenance), without the structure JSON. */
    @Query("select new vn.unicom.thi360.exam.domain.ExamTemplateCheckFacts(t.code, t.defaultTemplate, t.partCode, "
            + "t.structureYear, t.publishable, t.fixedCounts, t.fixedBlockCounts, t.omittedLocked) "
            + "from ExamTemplate t where t.code = ?1")
    Optional<ExamTemplateCheckFacts> findCheckFacts(String code);

    /** Only the structure column, read when a lock of the template applies to the exam being checked. */
    @Query("select t.structure from ExamTemplate t where t.code = ?1")
    Optional<JsonNode> findStructureByCode(String code);
}
