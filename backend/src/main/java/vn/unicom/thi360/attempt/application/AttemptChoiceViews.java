package vn.unicom.thi360.attempt.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import vn.unicom.thi360.attempt.api.AttemptChoiceSummary;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptPart;

/** Maps persisted attempt and part choice maps into typed {@link AttemptChoiceSummary} entries. */
final class AttemptChoiceViews {

    private AttemptChoiceViews() {}

    static Map<String, AttemptChoiceSummary> merge(Attempt attempt, List<AttemptPart> parts) {
        Map<String, AttemptChoiceSummary> merged = new LinkedHashMap<>();
        if (attempt != null && attempt.getChoices() != null) {
            for (Map.Entry<String, Object> entry : attempt.getChoices().entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) {
                    continue;
                }
                AttemptChoiceSummary summary = AttemptChoiceSummary.fromRaw(entry.getValue());
                if (summary != null) {
                    merged.put(entry.getKey(), summary);
                }
            }
        }
        if (parts != null) {
            for (AttemptPart part : parts) {
                if (part == null || part.getPartCode() == null || part.getPartCode().isBlank()) {
                    continue;
                }
                AttemptChoiceSummary summary = AttemptChoiceSummary.fromRaw(part.getChoice());
                if (summary != null) {
                    merged.put(part.getPartCode(), summary);
                }
            }
        }
        return merged.isEmpty() ? Map.of() : Map.copyOf(merged);
    }
}
