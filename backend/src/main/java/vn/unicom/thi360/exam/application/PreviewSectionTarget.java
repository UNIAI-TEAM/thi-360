package vn.unicom.thi360.exam.application;

import java.util.List;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamPartOption;
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
        throw new NotFoundException("SECTION_NOT_FOUND", "Exam section not found");
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
                section.blocks(), section.topicQuotas(), section.audioRequirement());
        ExamPart projectedPart = new ExamPart(part.id(), part.order(), part.name(), part.durationMinutes(),
                count, count, count, new PartParticipation(ParticipationMode.ALL, null), part.maxPoints(),
                List.of(projected), List.of(), List.of(), null, part.displaySegments());
        return new Selection(new ExamStructureDocument(List.of(projectedPart)), trim);
    }

    /**
     * Whether the section, projected alone, is the complete part a generated paper would hold: the part's own
     * sections and subject pool, plus the options its participation brings in, contribute no other section or block.
     * Only then are the part's display segments (counted from the part's first number) comparable to the projection.
     * A CHOICE part with {@code choose == 1} brings in exactly the option holding the section; with more picks, or when
     * the section is a common one, every option may take part. A subject-choice option picks a variable subset.
     */
    static boolean coversWholePart(ExamStructureDocument source, String sectionId) {
        for (ExamPart part : source.parts()) {
            ExamPartOption own = null;
            boolean found = part.sections().stream().anyMatch(section -> sectionId.equals(section.id()))
                    || part.subjectPool().stream().anyMatch(section -> sectionId.equals(section.id()));
            for (ExamPartOption option : part.options()) {
                if (option.sections().stream().anyMatch(section -> sectionId.equals(section.id()))) {
                    own = option;
                    found = true;
                }
            }
            if (!found) continue;
            boolean singleOption = own != null && part.participation() != null
                    && part.participation().mode() == ParticipationMode.CHOICE
                    && (part.participation().choose() == null || part.participation().choose() == 1);
            if (!part.subjectPool().isEmpty()) return false;
            int sections = part.sections().size();
            for (ExamPartOption option : part.options()) {
                if (singleOption && option != own) continue;
                if (option.kind() == OptionKind.SUBJECT_CHOICE || !option.blocks().isEmpty()) return false;
                sections += option.sections().size();
            }
            return sections == 1;
        }
        return false;
    }
}
