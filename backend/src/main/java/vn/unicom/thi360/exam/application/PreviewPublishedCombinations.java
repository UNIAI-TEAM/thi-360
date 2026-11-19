package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamPartOption;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.OptionKind;
import vn.unicom.thi360.exam.domain.ParticipationMode;
import vn.unicom.thi360.exam.domain.PartParticipation;
import vn.unicom.thi360.shared.api.BusinessRuleException;

/** Adapts immutable published combination snapshots into ordinary solver options. */
final class PreviewPublishedCombinations {
    static ExamStructureDocument project(ExamStructureDocument document, JsonNode combinations) {
        if (combinations == null) return document;
        List<ExamPart> parts = new ArrayList<>();
        for (ExamPart part : document.parts()) {
            if (part.codeSource() == null) {
                parts.add(part);
                continue;
            }
            if (!combinations.isArray()) throw new BusinessRuleException("EXAM_STRUCTURE_INVALID");
            List<ExamPartOption> options = new ArrayList<>();
            for (JsonNode combination : combinations) {
                if (!part.id().equals(combination.path("partId").asText())) continue;
                String code = combination.path("code").asText();
                if (code.isBlank() || !combination.path("subjects").isArray()) {
                    throw new BusinessRuleException("EXAM_STRUCTURE_INVALID");
                }
                List<JsonNode> subjects = new ArrayList<>();
                combination.path("subjects").forEach(subjects::add);
                subjects.sort(Comparator.comparingInt(subject -> subject.path("position").asInt()));
                List<ExamSection> sections = new ArrayList<>();
                for (JsonNode subject : subjects) {
                    String subjectCode = subject.path("code").asText();
                    ExamSection section = part.subjectPool().stream()
                            .filter(candidate -> subjectCode.equals(candidate.subjectCode())).findFirst()
                            .orElseThrow(() -> new BusinessRuleException("EXAM_STRUCTURE_INVALID"));
                    sections.add(section);
                }
                if (sections.isEmpty()) throw new BusinessRuleException("EXAM_STRUCTURE_INVALID");
                options.add(new ExamPartOption(part.id() + ":" + code, OptionKind.SUBJECT, code,
                        combination.path("name").asText(code), null, null, sections, List.of(), true));
            }
            if (options.isEmpty()) throw new BusinessRuleException("EXAM_STRUCTURE_INVALID");
            parts.add(new ExamPart(part.id(), part.order(), part.name(), part.durationMinutes(), part.questionCount(),
                    part.questionCountOnPaper(), part.questionCountRequired(),
                    new PartParticipation(ParticipationMode.CHOICE, 1), part.maxPoints(),
                    part.sections(), options, List.of(), null, part.displaySegments()));
        }
        return new ExamStructureDocument(parts, document.examQuotas());
    }
}
