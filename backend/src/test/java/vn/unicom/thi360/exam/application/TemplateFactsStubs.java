package vn.unicom.thi360.exam.application;

import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import vn.unicom.thi360.exam.domain.ExamTemplate;
import vn.unicom.thi360.exam.domain.ExamTemplateCheckFacts;

/** Stubs the keyed projection methods of {@link ExamTemplateService} from a mocked template row (FU-BE15-R1-5). */
final class TemplateFactsStubs {
    private TemplateFactsStubs() {
    }

    private static ExamTemplateCheckFacts facts(String code, ExamTemplate template) {
        return new ExamTemplateCheckFacts(code, template.isDefaultTemplate(), template.getPartCode(),
                template.getStructureYear(), template.isPublishable(), template.isFixedCounts(),
                template.isFixedBlockCounts(), template.isOmittedLocked());
    }

    /** The template an exam was loaded from: its flag columns, and its structure for the locks that apply. */
    static void provenance(ExamTemplateService templates, String code, ExamTemplate template) {
        // Read the mock first: a getter call inside thenReturn(...) would be an unfinished stubbing.
        ExamTemplateCheckFacts facts = facts(code, template);
        JsonNode structure = template.getStructure();
        when(templates.findCheckFacts(code)).thenReturn(facts);
        when(templates.readStructure(code)).thenReturn(structure);
    }

    /** The standard default subject template of a kind and subject that decides publishing. */
    static void standard(ExamTemplateService templates, String kind, String subject, String code, ExamTemplate template) {
        ExamTemplateCheckFacts facts = facts(code, template);
        JsonNode structure = template.getStructure();
        when(templates.findSubjectStandardFacts(kind, subject)).thenReturn(facts);
        when(templates.readStructure(code)).thenReturn(structure);
    }
}
