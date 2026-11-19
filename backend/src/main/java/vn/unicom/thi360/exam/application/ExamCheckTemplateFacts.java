package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.domain.Exam;
import vn.unicom.thi360.exam.domain.ExamStructureCodec;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.ExamTemplate;

/** Reads the template flags the check rules need; the template structure is parsed only for fixed-count templates. */
@Component
public class ExamCheckTemplateFacts {
    private final CatalogLookup catalog;
    private final ExamTemplateService templates;
    private final ObjectMapper mapper;

    public ExamCheckTemplateFacts(CatalogLookup catalog, ExamTemplateService templates, ObjectMapper mapper) {
        this.catalog = catalog;
        this.templates = templates;
        this.mapper = mapper;
    }

    /**
     * The standard default subject template of the exam's kind and subject decides whether publishing is allowed
     * (D11), whatever template the exam was loaded from. Counts are locked only by the template the exam was loaded
     * from. Only the templates of the exam's own subject are read, and that row is reused when it is also the
     * loaded template.
     */
    ExamCheckRules.TemplateFacts of(Exam exam) {
        String unpublishable = null;
        ExamTemplate standard = null;
        if (exam.getKindItemId() != null && exam.getSubjectItemId() != null) {
            var kind = catalog.findItem(exam.getKindItemId());
            var subject = catalog.findItem(exam.getSubjectItemId());
            if (kind.isPresent() && subject.isPresent()) {
                ExamTemplate best = templates.findBestMatch(kind.get().code(), "SUBJECT", null,
                        subject.get().code(), null);
                if (best != null && best.isDefaultTemplate() && best.getPartCode() == null) {
                    standard = best;
                    if (!standard.isPublishable()) unpublishable = subject.get().code();
                }
            }
        }
        return template(exam, standard, unpublishable);
    }

    /**
     * The facts that mark a section in the section states: every lock of the loaded template (H27, H42, H43) and no
     * publish ban, so the exam-level ban marks no section.
     */
    ExamCheckRules.TemplateFacts forSections(Exam exam) {
        return template(exam, null, null);
    }

    /**
     * Row lock (H42) counts only for a {@code SUBJECT} exam, so a {@code PART} or review exam of the same template
     * may change counts. The omitted lock (H43) and the scale rule (H49) apply to every scope. The template is parsed
     * once and only when one of its flags is on.
     */
    private ExamCheckRules.TemplateFacts template(Exam exam, ExamTemplate known, String unpublishable) {
        String code = exam.getTemplateCode();
        ExamTemplate loaded = null;
        if (code != null && !code.isBlank()) {
            loaded = known != null && code.trim().equals(known.getCode()) ? known : templates.findProvenance(code);
        }
        boolean blocks = loaded != null && loaded.isFixedBlockCounts() && "SUBJECT".equals(exam.getScope());
        boolean omitted = loaded != null && loaded.isOmittedLocked();
        ExamStructureDocument structure = loaded != null && (loaded.isFixedCounts() || blocks || omitted)
                ? new ExamStructureCodec(mapper).read(loaded.getStructure()) : null;
        return new ExamCheckRules.TemplateFacts(unpublishable,
                loaded != null && loaded.isFixedCounts() ? structure : null, blocks ? structure : null,
                omitted ? structure : null);
    }
}
