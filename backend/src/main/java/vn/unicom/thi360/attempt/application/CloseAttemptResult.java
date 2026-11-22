package vn.unicom.thi360.attempt.application;

import java.util.List;
import vn.unicom.thi360.attempt.api.FinalAnswerResult;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptPart;

/** Result of invoking the shared attempt close procedure (LT §6.3). */
public record CloseAttemptResult(
        Attempt attempt,
        List<AttemptPart> parts,
        List<FinalAnswerResult> results,
        boolean closedNow) {

    public CloseAttemptResult {
        parts = parts == null ? List.of() : List.copyOf(parts);
        results = results == null ? List.of() : List.copyOf(results);
    }
}
