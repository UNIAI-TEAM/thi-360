package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.attempt.api.FinalAnswerChange;
import vn.unicom.thi360.attempt.api.FinalAnswerResult;
import vn.unicom.thi360.attempt.application.NoopFinalAnswerWriter;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptPart;

/** Unit tests for the temporary AT-2a {@link NoopFinalAnswerWriter} (plan B1, AC-15). */
class NoopFinalAnswerWriterTest {

    @Test
    void returnsEmptyResultsAndPerformsNoSideEffects() {
        NoopFinalAnswerWriter writer = new NoopFinalAnswerWriter();
        Attempt attempt = mock(Attempt.class);
        AttemptPart part = mock(AttemptPart.class);
        List<FinalAnswerChange> changes =
                List.of(
                        new FinalAnswerChange(
                                UUID.randomUUID(),
                                1L,
                                new AnswerValue.SingleChoice("opt-1")));

        List<FinalAnswerResult> results =
                writer.writeFinalAnswers(attempt, part, changes, Instant.parse("2026-11-20T10:00:00Z"));

        assertThat(results).isEmpty();
        verifyNoInteractions(attempt, part);
    }
}
