package vn.unicom.thi360.exam.application;

import java.util.LinkedHashSet;
import java.util.Set;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.api.NotFoundException;

/** A check targets the entire exam, one part, or the solver unit containing one section. */
public record ExamCheckScope(Kind kind, String targetId) {
    public enum Kind { EXAM, PART, SECTION }

    public static ExamCheckScope from(String scope, String partId, String sectionId) {
        Kind kind;
        try { kind = scope == null ? Kind.EXAM : Kind.valueOf(scope); }
        catch (IllegalArgumentException ex) { throw new BusinessRuleException("EXAM_STRUCTURE_INVALID"); }
        String target = switch (kind) {
            case EXAM -> null;
            case PART -> partId;
            case SECTION -> sectionId;
        };
        if (kind != Kind.EXAM && (target == null || target.isBlank())) {
            throw new BusinessRuleException("EXAM_STRUCTURE_INVALID");
        }
        return new ExamCheckScope(kind, target);
    }

    Set<String> sections(ExamStructureDocument document) {
        if (kind == Kind.EXAM) return Set.of();
        Set<String> found = new LinkedHashSet<>();
        boolean targetExists = false;
        for (ExamPart part : document.parts()) {
            if (kind == Kind.PART && targetId.equals(part.id())) {
                targetExists = true;
                collect(found, part.sections());
                collect(found, part.subjectPool());
                for (var option : part.options()) {
                    collect(found, option.sections());
                    if (!option.blocks().isEmpty()) found.add(option.id() + ":blocks");
                }
            }
            if (kind == Kind.SECTION) {
                Set<String> available = new LinkedHashSet<>();
                collect(available, part.sections());
                collect(available, part.subjectPool());
                for (var option : part.options()) {
                    collect(available, option.sections());
                    if (!option.blocks().isEmpty()) available.add(option.id() + ":blocks");
                }
                if (available.contains(targetId)) { found.add(targetId); targetExists = true; }
            }
        }
        if (!targetExists) throw new NotFoundException("Exam check target not found");
        return Set.copyOf(found);
    }

    private static void collect(Set<String> ids, java.util.List<ExamSection> sections) {
        for (var section : sections) ids.add(section.id());
    }
}
