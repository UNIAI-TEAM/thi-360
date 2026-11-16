package vn.unicom.thi360.exam.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import vn.unicom.thi360.exam.api.ExamPreviewController.PathChoice;
import vn.unicom.thi360.exam.api.ExamPreviewController.PathRef;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamPartOption;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.solver.*;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.api.NotFoundException;

/** Resolves one requested path and one seeded allocation of each subject choice. */
final class PreviewPathResolver {
    record Resolved(SolvePath path, PathRef pathRef) {}

    static Resolved resolve(ExamStructureDocument document, List<SolvePart> parts,
            String pathId, List<String> subjectCodes, long seed, SubjectCountAllocator allocator) {
        String[] ids = "default".equals(pathId) ? new String[0] : pathId.split("\\+", -1);
        int pos = 0;
        List<ChosenOption> chosen = new ArrayList<>();
        List<PathChoice> names = new ArrayList<>();
        List<SectionOnPath> sections = new ArrayList<>();
        List<SubjectChoice> choices = new ArrayList<>();
        List<List<SubjectPick>> picks = new ArrayList<>();
        for (SolvePart part : parts) {
            if (part instanceof SolvePart.Fixed fixed) {
                sections.addAll(fixed.sections());
                continue;
            }
            SolvePart.Choice choicePart = (SolvePart.Choice) part;
            sections.addAll(choicePart.commonSections());
            int previous = -1;
            for (int k = 0; k < choicePart.choose(); k++) {
                if (pos >= ids.length) throw missing(pathId);
                String id = ids[pos++];
                int index = -1;
                for (int j = previous + 1; j < choicePart.options().size(); j++) {
                    SolveOption option = choicePart.options().get(j);
                    String optionId = option instanceof SolveOption.Sections s ? s.optionId()
                            : ((SubjectChoice) option).optionId();
                    if (id.equals(optionId)) { index = j; break; }
                }
                if (index < 0) throw missing(pathId);
                previous = index;
                SolveOption option = choicePart.options().get(index);
                chosen.add(new ChosenOption(choicePart.partId(), id));
                names.add(name(document, choicePart.partId(), id));
                if (option instanceof SolveOption.Sections normal) {
                    sections.addAll(normal.sections());
                } else {
                    SubjectChoice subjectChoice = (SubjectChoice) option;
                    if (subjectCodes == null || subjectCodes.size() != subjectChoice.choose()
                            || subjectCodes.stream().anyMatch(code -> code == null || code.isBlank())
                            || new HashSet<>(subjectCodes).size() != subjectCodes.size()) {
                        throw new BusinessRuleException("PREVIEW_SUBJECTS_INVALID");
                    }
                    Set<String> wanted = Set.copyOf(subjectCodes);
                    List<SectionOnPath> selected = subjectChoice.sections().stream()
                            .filter(s -> wanted.contains(s.subjectCode())).toList();
                    if (selected.size() != subjectChoice.choose()) throw new BusinessRuleException("PREVIEW_SUBJECTS_INVALID");
                    int[] counts = allocator.select(subjectChoice, selected, seed);
                    List<Integer> list = new ArrayList<>(counts.length);
                    for (int count : counts) list.add(count);
                    choices.add(subjectChoice);
                    picks.add(List.of(new SubjectPick(id, selected, list)));
                }
            }
        }
        if (pos != ids.length) throw missing(pathId);
        return new Resolved(new SolvePath(0, chosen, sections, choices, picks), new PathRef(pathId, names));
    }

    private static PathChoice name(ExamStructureDocument document, String partId, String optionId) {
        ExamPart part = document.parts().stream().filter(p -> partId.equals(p.id())).findFirst().orElseThrow();
        ExamPartOption option = part.options().stream().filter(o -> optionId.equals(o.id())).findFirst().orElseThrow();
        return new PathChoice(partId, optionId, option.kind() == null ? null : option.kind().name(),
                option.code(), option.name() == null ? option.code() : option.name());
    }

    private static NotFoundException missing(String pathId) {
        return new NotFoundException("PATH_NOT_FOUND", "Path not found: " + pathId);
    }
}
