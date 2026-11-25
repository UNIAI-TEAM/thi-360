package vn.unicom.thi360.exam.application;

import java.util.List;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.OptionKind;
import vn.unicom.thi360.exam.domain.ParticipationMode;
import vn.unicom.thi360.exam.domain.PartParticipation;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.api.ValidationException;

/** Projects one section into a fixed, independently solvable preview structure. */
final class PreviewSectionTarget {
    record Selection(ExamStructureDocument document, boolean trim) {}

    static Selection select(ExamStructureDocument source, String sectionId, Integer requestedCount) {
        if (sectionId == null || sectionId.isBlank()) throw new ValidationException((String) null);
        for (ExamPart part : source.parts()) {
            for (ExamSection section : part.sections()) {
                if (sectionId.equals(section.id())) return fixed(part, section, requestedCount);
            }
            for (ExamSection section : part.subjectPool()) {
                if (sectionId.equals(section.id())) return fixed(part, section, requestedCount);
            }
            for (var option : part.options()) {
                for (ExamSection section : option.sections()) {
                    if (!sectionId.equals(section.id())) continue;
                    if (option.kind() != OptionKind.SUBJECT_CHOICE) return fixed(part, section, requestedCount);
                    if (requestedCount == null || (requestedCount != 16 && requestedCount != 17)
                            || option.countsByPick() == null || option.countsByPick().isEmpty()
                            || option.countsByPick().stream().mapToInt(Integer::intValue).max().orElseThrow() != 17) {
                        throw new ValidationException((String) null);
                    }
                    return project(part, section, 17, requestedCount == 16);
                }
            }
        }
        throw new NotFoundException("SECTION_NOT_FOUND", null);
    }

    private static Selection fixed(ExamPart part, ExamSection section, Integer requestedCount) {
        if (section.questionCount() == null || (requestedCount != null && !requestedCount.equals(section.questionCount()))) {
            throw new ValidationException((String) null);
        }
        return project(part, section, section.questionCount(), false);
    }

    private static Selection project(ExamPart part, ExamSection section, int count, boolean trim) {
        ExamSection projected = new ExamSection(section.id(), section.subjectCode(), count,
                section.knowledgeScope(), section.answerQuotas(), section.matrix(), section.choiceGroup(),
                section.blocks(), section.topicQuotas(), section.audioRequirement(), section.mapidMode(),
                section.mapidRules(), section.mixBlocks());
        ExamPart projectedPart = new ExamPart(part.id(), part.order(), part.name(), part.durationMinutes(),
                count, count, count, new PartParticipation(ParticipationMode.ALL, null), part.maxPoints(),
                List.of(projected), List.of(), List.of(), null, part.displaySegments());
        return new Selection(new ExamStructureDocument(List.of(projectedPart)), trim);
    }
}
