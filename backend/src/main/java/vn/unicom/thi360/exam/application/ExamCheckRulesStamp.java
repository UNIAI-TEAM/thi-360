package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Marks a saved whole-exam check result as evaluated under the current rule set. A saved result without the current
 * stamp predates the rules and cannot be published for exams the rules apply to ({@code RULES_CHANGED}). The stamp
 * lives inside the stored result, never reaches the API, and bumping {@link #CURRENT} invalidates every older result.
 */
final class ExamCheckRulesStamp {
    /** Key inside {@code exam_check_run.result}. */
    static final String KEY = "rulesStamp";
    /** FTM R-12: the set-count and short-answer rules of HSA and QDA. Change it when a rule changes. */
    static final String CURRENT = "FTM_R12";

    private ExamCheckRulesStamp() { }

    /** A copy of the result as it is stored. */
    static JsonNode stamped(JsonNode result) {
        if (!(result instanceof ObjectNode object)) return result;
        ObjectNode stored = object.deepCopy();
        stored.put(KEY, CURRENT);
        return stored;
    }

    /** The result as the API shows it. */
    static ObjectNode unstamped(ObjectNode stored) {
        stored.remove(KEY);
        return stored;
    }
}
