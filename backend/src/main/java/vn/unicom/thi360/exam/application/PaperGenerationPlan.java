package vn.unicom.thi360.exam.application;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamPartOption;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.OptionKind;
import vn.unicom.thi360.exam.domain.ParticipationMode;
import vn.unicom.thi360.exam.spi.PaperGenerationException;
import vn.unicom.thi360.exam.spi.PaperGenerationException.Reason;
import vn.unicom.thi360.exam.spi.PaperSolverPort.Selection;

/**
 * Validates a generation request against the published snapshot and turns it into what the shared path resolver
 * takes: the structure reduced to the requested parts with only the chosen options built, the part ids in structure
 * order, the chosen option ids and the chosen subjects. Everything here runs before any source read, so a bad request
 * never reaches the solver.
 *
 * <p>A selection is valid when it names, per choice part, exactly the part's {@code choose} options, hands each chosen
 * subject-choice option exactly its {@code chooseSubjects} subjects of its own sections, and the chosen options lie on
 * one path the version published.
 */
final class PaperGenerationPlan {
    /**
     * @param document     requested parts in structure order; options that were not chosen are emptied so they are
     *                     never built (an unchosen option may be unpublished or incomplete)
     * @param partIds      ids of the requested parts, in structure order
     * @param pathId       chosen option ids joined as the resolver reads them, or {@code default}
     * @param subjectsByOption chosen subjects of each chosen subject-choice option, keyed by option id and validated
     *                     per selection: two parts that offer the same subject do not share a pick
     */
    record Plan(ExamStructureDocument document, List<String> partIds, String pathId,
            Map<String, List<String>> subjectsByOption) {}

    private PaperGenerationPlan() {}

    static Plan of(ExamStructureDocument document, List<String> partCodes, List<Selection> selections,
            List<String> publishedPathIds) {
        if (partCodes == null || partCodes.isEmpty()) throw failed("partCodes is empty");
        Set<String> requested = new LinkedHashSet<>();
        for (String code : partCodes) {
            if (code == null || code.isBlank()) throw failed("part code is blank");
            if (!requested.add(code)) throw failed("part code repeated: " + code);
        }
        Map<String, ExamPart> byId = new HashMap<>();
        for (ExamPart part : document.parts()) byId.put(part.id(), part);
        for (String code : requested) {
            if (!byId.containsKey(code)) throw failed("part is not in the version: " + code);
        }

        Map<String, Selection> bySelection = new HashMap<>();
        for (Selection selection : selections == null ? List.<Selection>of() : selections) {
            if (selection == null || selection.partCode() == null) throw failed("selection without a part");
            if (!requested.contains(selection.partCode())) {
                throw failed("selection for a part that is not requested: " + selection.partCode());
            }
            if (bySelection.put(selection.partCode(), selection) != null) {
                throw failed("two selections for part " + selection.partCode());
            }
        }

        List<ExamPart> parts = new ArrayList<>();
        List<String> partIds = new ArrayList<>();
        List<String> optionIds = new ArrayList<>();
        Map<String, List<String>> subjectsByOption = new LinkedHashMap<>();
        for (ExamPart part : document.parts().stream().sorted(Comparator.comparing(ExamPart::order,
                Comparator.nullsLast(Comparator.naturalOrder()))).toList()) {
            if (!requested.contains(part.id())) continue;
            partIds.add(part.id());
            Selection selection = bySelection.get(part.id());
            if (!isChoice(part)) {
                if (selection != null) throw failed("part " + part.id() + " has no choice");
                parts.add(part);
                continue;
            }
            if (selection == null) throw failed("part " + part.id() + " needs a selection");
            List<ExamPartOption> chosen = chosenOptions(part, selection);
            subjectsByOption.putAll(subjectCodes(part, chosen, selection));
            Set<String> chosenIds = new HashSet<>();
            chosen.forEach(option -> {
                chosenIds.add(option.id());
                optionIds.add(option.id());
            });
            parts.add(onlyChosen(part, chosenIds));
        }
        requirePublished(optionIds, publishedPathIds);
        return new Plan(new ExamStructureDocument(parts, document.examQuotas()), List.copyOf(partIds),
                optionIds.isEmpty() ? "default" : String.join("+", optionIds), Map.copyOf(subjectsByOption));
    }

    private static boolean isChoice(ExamPart part) {
        return part.participation() != null && part.participation().mode() == ParticipationMode.CHOICE;
    }

    /** The options of the selection in option order, as the resolver reads them. */
    private static List<ExamPartOption> chosenOptions(ExamPart part, Selection selection) {
        Integer choose = part.participation().choose();
        if (choose == null || choose < 1) throw failed("part " + part.id() + " has no choice count");
        List<String> codes = selection.optionCodes();
        if (codes.size() != choose) {
            throw failed("part " + part.id() + " needs " + choose + " option(s), got " + codes.size());
        }
        Set<Integer> indexes = new HashSet<>();
        for (String code : codes) {
            if (code == null || code.isBlank()) throw failed("option code is blank in part " + part.id());
            int index = indexOf(part, code);
            if (index < 0) throw failed("option " + code + " is not in part " + part.id());
            if (!indexes.add(index)) throw failed("option " + code + " repeated in part " + part.id());
        }
        List<ExamPartOption> chosen = new ArrayList<>();
        indexes.stream().sorted().forEach(index -> chosen.add(part.options().get(index)));
        return chosen;
    }

    /**
     * The subjects of one selection, by the option that chose them. They must be distinct, belong to exactly one
     * chosen subject-choice option of the same part, and fill each such option to its {@code chooseSubjects}; a part
     * with no such option takes none. Ownership is decided per part, so a subject that another part also offers
     * stays with the option that chose it.
     */
    private static Map<String, List<String>> subjectCodes(ExamPart part, List<ExamPartOption> chosen,
            Selection selection) {
        List<String> codes = selection.subjectCodes();
        Set<String> distinct = new HashSet<>();
        for (String code : codes) {
            if (code == null || code.isBlank()) throw failed("subject code is blank in part " + part.id());
            if (!distinct.add(code)) throw failed("subject " + code + " repeated in part " + part.id());
        }
        List<ExamPartOption> choices = chosen.stream().filter(option -> option.kind() == OptionKind.SUBJECT_CHOICE).toList();
        if (choices.isEmpty()) {
            if (!codes.isEmpty()) throw failed("part " + part.id() + " takes no subjects");
            return Map.of();
        }
        List<List<String>> received = new ArrayList<>();
        for (int i = 0; i < choices.size(); i++) received.add(new ArrayList<>());
        for (String code : codes) {
            int owner = -1;
            for (int i = 0; i < choices.size(); i++) {
                if (choices.get(i).sections().stream().anyMatch(section -> code.equals(section.subjectCode()))) {
                    if (owner >= 0) throw failed("subject " + code + " is ambiguous in part " + part.id());
                    owner = i;
                }
            }
            if (owner < 0) throw failed("subject " + code + " is not in the choice of part " + part.id());
            received.get(owner).add(code);
        }
        for (int i = 0; i < choices.size(); i++) {
            Integer wanted = choices.get(i).chooseSubjects();
            if (wanted == null || wanted < 1) throw failed("option " + choices.get(i).id() + " has no subject count");
            if (received.get(i).size() != wanted) {
                throw failed("option " + choices.get(i).id() + " needs " + wanted + " subject(s), got "
                        + received.get(i).size());
            }
        }
        Map<String, List<String>> byOption = new LinkedHashMap<>();
        for (int i = 0; i < choices.size(); i++) byOption.put(choices.get(i).id(), List.copyOf(received.get(i)));
        return byOption;
    }

    /** The part with every option but the chosen ones emptied: an option nobody chose is not built or solved. */
    private static ExamPart onlyChosen(ExamPart part, Set<String> chosenIds) {
        List<ExamPartOption> options = new ArrayList<>(part.options().size());
        for (ExamPartOption option : part.options()) {
            options.add(chosenIds.contains(option.id()) ? option
                    : new ExamPartOption(option.id(), OptionKind.SUBJECT, option.code(), option.name(), null, null,
                            List.<ExamSection>of(), List.of(), option.expertConfirmed()));
        }
        return new ExamPart(part.id(), part.order(), part.name(), part.durationMinutes(), part.questionCount(),
                part.questionCountOnPaper(), part.questionCountRequired(), part.participation(), part.maxPoints(),
                part.sections(), options, part.subjectPool(), part.codeSource(), part.displaySegments());
    }

    /**
     * The chosen options must lie on one path this version published ({@code published_path_ids}), not merely be valid
     * options of the structure: publication may admit only some of them. A request with no choice at all (parts every
     * student takes) needs the version to have published at least one path.
     */
    private static void requirePublished(List<String> optionIds, List<String> publishedPathIds) {
        if (publishedPathIds == null || publishedPathIds.isEmpty()) throw failed("the version has no published path");
        for (String path : publishedPathIds) {
            if (new HashSet<>(Arrays.asList(path.split("\\+", -1))).containsAll(optionIds)) return;
        }
        throw failed("the selection is not on a path this version published");
    }

    private static int indexOf(ExamPart part, String code) {
        List<ExamPartOption> options = part.options();
        for (int i = 0; i < options.size(); i++) {
            if (code.equals(options.get(i).code())) return i;
        }
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).code() == null && code.equals(options.get(i).id())) return i;
        }
        return -1;
    }

    static PaperGenerationException failed(String message) {
        return new PaperGenerationException(Reason.GENERATION_FAILED, message);
    }
}
