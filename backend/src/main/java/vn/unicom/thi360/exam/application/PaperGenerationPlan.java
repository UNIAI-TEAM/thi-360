package vn.unicom.thi360.exam.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamPartOption;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.ParticipationMode;
import vn.unicom.thi360.exam.spi.PaperGenerationException;
import vn.unicom.thi360.exam.spi.PaperGenerationException.Reason;
import vn.unicom.thi360.exam.spi.PaperSolverPort.Selection;

/**
 * Validates a generation request against the published snapshot and turns it into what the shared path resolver
 * takes: the structure reduced to the requested parts, the chosen option ids in part order, and the chosen subjects.
 * Everything here runs before any source read, so a bad request never reaches the solver.
 */
final class PaperGenerationPlan {
    /** @param document requested parts only, in structure order; {@code pathId} and {@code subjectCodes} for the resolver */
    record Plan(ExamStructureDocument document, List<String> partIds, String pathId, List<String> subjectCodes) {}

    private PaperGenerationPlan() {}

    static Plan of(ExamStructureDocument document, List<String> partCodes, List<Selection> selections) {
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
        List<String> optionIds = new ArrayList<>();
        List<String> subjectCodes = new ArrayList<>();
        for (ExamPart part : document.parts().stream().sorted(Comparator.comparing(ExamPart::order,
                Comparator.nullsLast(Comparator.naturalOrder()))).toList()) {
            if (!requested.contains(part.id())) continue;
            parts.add(part);
            Selection selection = bySelection.get(part.id());
            if (!isChoice(part)) {
                if (selection != null) throw failed("part " + part.id() + " has no choice");
                continue;
            }
            if (selection == null) throw failed("part " + part.id() + " needs a selection");
            optionIds.addAll(optionIds(part, selection));
            subjectCodes.addAll(selection.subjectCodes());
        }
        return new Plan(new ExamStructureDocument(parts, document.examQuotas()), List.copyOf(requested),
                optionIds.isEmpty() ? "default" : String.join("+", optionIds), List.copyOf(subjectCodes));
    }

    private static boolean isChoice(ExamPart part) {
        return part.participation() != null && part.participation().mode() == ParticipationMode.CHOICE;
    }

    /** Option ids of the selection in option order, as the resolver reads them. */
    private static List<String> optionIds(ExamPart part, Selection selection) {
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
        List<String> ids = new ArrayList<>();
        indexes.stream().sorted().forEach(index -> ids.add(part.options().get(index).id()));
        return ids;
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
