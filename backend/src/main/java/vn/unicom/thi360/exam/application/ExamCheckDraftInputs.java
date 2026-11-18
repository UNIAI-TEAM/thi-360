package vn.unicom.thi360.exam.application;

import java.util.List;
import java.util.Set;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamPartOption;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;

/** Keeps incomplete section identities for path planning, never for solving those units. */
final class ExamCheckDraftInputs {
    private ExamCheckDraftInputs() {}

    static ExamStructureDocument maskSections(ExamStructureDocument document, Set<String> invalid) {
        if (invalid.isEmpty()) return document;
        List<ExamPart> parts = document.parts().stream().map(part -> {
            List<ExamPartOption> options = part.options().stream().map(option -> new ExamPartOption(
                    option.id(), option.kind(), option.code(), option.name(), option.chooseSubjects(),
                    option.countsByPick(), sections(option.sections(), invalid), option.blocks(),
                    option.expertConfirmed())).toList();
            return new ExamPart(part.id(), part.order(), part.name(), part.durationMinutes(), part.questionCount(),
                    part.questionCountOnPaper(), part.questionCountRequired(), part.participation(), part.maxPoints(),
                    sections(part.sections(), invalid), options, sections(part.subjectPool(), invalid), part.codeSource());
        }).toList();
        return new ExamStructureDocument(parts, document.examQuotas());
    }

    private static List<ExamSection> sections(List<ExamSection> sections, Set<String> invalid) {
        return sections.stream().filter(section -> !invalid.contains(section.id())
                        || section.id() != null && section.subjectCode() != null)
                .map(section -> invalid.contains(section.id())
                        ? new ExamSection(section.id(), section.subjectCode(), section.questionCount(),
                                null, null, null, section.choiceGroup(), List.of()) : section)
                .toList();
    }
}
