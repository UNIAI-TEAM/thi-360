package vn.unicom.thi360.attempt.application;

import java.time.Instant;
import java.util.List;
import vn.unicom.thi360.attempt.api.FinalAnswerChange;
import vn.unicom.thi360.attempt.api.FinalAnswerResult;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptPart;

/**
 * Module-internal port invoked inside the closing transaction before closing a part or attempt so
 * {@code finalAnswers} sent with {@code finish} or {@code submit} are persisted atomically
 * (LT §6.3, plan B1, AC-14).
 *
 * <p>{@code targetPart} is non-null during {@code finishPart} and during sequential {@code submit};
 * it may be {@code null} for whole-attempt {@code FREE} navigation submit (AT-8).
 */
public interface FinalAnswerWriter {

    List<FinalAnswerResult> writeFinalAnswers(
            Attempt attempt,
            AttemptPart targetPart,
            List<FinalAnswerChange> changes,
            Instant receivedAt);
}
