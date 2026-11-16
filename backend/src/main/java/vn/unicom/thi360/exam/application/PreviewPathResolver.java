package vn.unicom.thi360.exam.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        List<SubjectChoice> subjectChoices = new ArrayList<>();
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
                    subjectChoices.add((SubjectChoice) option);
                }
            }
        }
        if (pos != ids.length) throw missing(pathId);
        List<List<SubjectPick>> picks = picks(subjectChoices, subjectCodes, seed, allocator);
        return new Resolved(new SolvePath(0, chosen, sections, subjectChoices, picks),
                new PathRef(pathId, names));
    }

    /**
     * The flat {@code subjectCodes} list is a union over every {@link SubjectChoice} on the path:
     * each code must belong to exactly one choice's sections and each choice must receive exactly
     * its {@code chooseSubjects} codes; anything else is {@code PREVIEW_SUBJECTS_INVALID}.
     */
    private static List<List<SubjectPick>> picks(List<SubjectChoice> choices,
            List<String> subjectCodes, long seed, SubjectCountAllocator allocator) {
        List<String> codes = subjectCodes == null ? List.of() : subjectCodes;
        if (codes.stream().anyMatch(code -> code == null || code.isBlank())
                || new HashSet<>(codes).size() != codes.size()) {
            throw new BusinessRuleException("PREVIEW_SUBJECTS_INVALID");
        }
        Map<String, Integer> owners = new HashMap<>();
        for (int i = 0; i < choices.size(); i++) {
            for (SectionOnPath section : choices.get(i).sections()) {
                owners.merge(section.subjectCode(), i, (first, other) -> first.equals(other) ? first : -1);
            }
        }
        List<Set<String>> wanted = new ArrayList<>(choices.size());
        for (int i = 0; i < choices.size(); i++) wanted.add(new HashSet<>());
        for (String code : codes) {
            Integer owner = owners.get(code);
            if (owner == null || owner < 0) throw new BusinessRuleException("PREVIEW_SUBJECTS_INVALID");
            wanted.get(owner).add(code);
        }
        List<List<SubjectPick>> picks = new ArrayList<>(choices.size());
        for (int i = 0; i < choices.size(); i++) {
            SubjectChoice choice = choices.get(i);
            Set<String> wantedForChoice = wanted.get(i);
            List<SectionOnPath> selected = choice.sections().stream()
                    .filter(s -> wantedForChoice.contains(s.subjectCode())).toList();
            if (selected.size() != choice.choose()) throw new BusinessRuleException("PREVIEW_SUBJECTS_INVALID");
            int[] counts = allocator.select(choice, selected, seed);
            List<Integer> list = new ArrayList<>(counts.length);
            for (int count : counts) list.add(count);
            picks.add(List.of(new SubjectPick(choice.optionId(), selected, list)));
        }
        return picks;
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
