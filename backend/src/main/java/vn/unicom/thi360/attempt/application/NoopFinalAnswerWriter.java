package vn.unicom.thi360.attempt.application;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.attempt.api.FinalAnswerChange;
import vn.unicom.thi360.attempt.api.FinalAnswerResult;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptPart;

/**
 * Temporary no-op {@link FinalAnswerWriter} for AT-2a (plan B1, AC-15).
 *
 * <p>Standalone {@code @Component} so AT-3 can replace it cleanly with the real {@code AnswerService}
 * implementation.
 */
@Component
public class NoopFinalAnswerWriter implements FinalAnswerWriter {

    @Override
    public List<FinalAnswerResult> writeFinalAnswers(
            Attempt attempt,
            AttemptPart targetPart,
            List<FinalAnswerChange> changes,
            Instant receivedAt) {
        return List.of();
    }
}
