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
        return new ExamCheckRules.TemplateFacts(unpublishable, locked(exam, standard));
    }

    /** Structure of the loaded template when it has {@code fixed_counts = true}; null otherwise. */
    ExamStructureDocument locked(Exam exam) {
        return locked(exam, null);
    }

    private ExamStructureDocument locked(Exam exam, ExamTemplate known) {
        String code = exam.getTemplateCode();
        if (code == null || code.isBlank()) return null;
        ExamTemplate loaded = known != null && code.trim().equals(known.getCode()) ? known : templates.findProvenance(code);
        return loaded != null && loaded.isFixedCounts()
                ? new ExamStructureCodec(mapper).read(loaded.getStructure()) : null;
    }
}
