package vn.unicom.thi360.attempt.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.shared.outbox.OutboxEvent;
import vn.unicom.thi360.shared.outbox.OutboxRelay;

/** Unit tests for {@link AttemptFinishedHandler} error handling (R1-49, R1-50). */
class AttemptFinishedHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void preservesRootGradingExceptionWhenMarkGradingErrorThrowsOnFinalRetry() {
        OutboxRelay relay = mock(OutboxRelay.class);
        AttemptGradingService gradingService = mock(AttemptGradingService.class);
        AttemptFinishedHandler handler = new AttemptFinishedHandler(relay, gradingService, MAPPER);

        UUID attemptId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                AttemptFinishedHandler.TOPIC_ATTEMPT_FINISHED,
                "{\"attemptId\":\"" + attemptId + "\"}",
                Instant.parse("2026-11-20T10:00:00Z"));
        for (int i = 0; i < OutboxRelay.DEFAULT_MAX_ATTEMPTS - 1; i++) {
            event.recordFailure("prior", Instant.parse("2026-11-20T10:00:01Z"), OutboxRelay.DEFAULT_MAX_ATTEMPTS, Duration.ofSeconds(2));
        }

        IllegalStateException rootEx = new IllegalStateException("Missing snapshot v1");
        RuntimeException markEx = new RuntimeException("Lock timeout during markGradingError");
        doThrow(rootEx).when(gradingService).gradeFinishedAttempt(attemptId);
        doThrow(markEx).when(gradingService).markGradingError(eq(attemptId), eq("Missing snapshot v1"));

        assertThatThrownBy(() -> handler.handle(event))
                .isSameAs(rootEx)
                .hasMessage("Missing snapshot v1")
                .satisfies(thrown -> assertThat(thrown.getSuppressed()).containsExactly(markEx));
    }

    @Test
    void doesNotMarkGradingErrorBeforeFinalRetry() {
        OutboxRelay relay = mock(OutboxRelay.class);
        AttemptGradingService gradingService = mock(AttemptGradingService.class);
        AttemptFinishedHandler handler = new AttemptFinishedHandler(relay, gradingService, MAPPER);

        UUID attemptId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                AttemptFinishedHandler.TOPIC_ATTEMPT_FINISHED,
                "{\"attemptId\":\"" + attemptId + "\"}",
                Instant.parse("2026-11-20T10:00:00Z"));

        IllegalStateException rootEx = new IllegalStateException("Transient failure");
        doThrow(rootEx).when(gradingService).gradeFinishedAttempt(attemptId);

        assertThatThrownBy(() -> handler.handle(event)).isSameAs(rootEx);
        verify(gradingService, never()).markGradingError(any(), any());
    }
}
