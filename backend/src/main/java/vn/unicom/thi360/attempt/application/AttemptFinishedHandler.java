package vn.unicom.thi360.attempt.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Objects;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.shared.outbox.OutboxEvent;
import vn.unicom.thi360.shared.outbox.OutboxHandler;
import vn.unicom.thi360.shared.outbox.OutboxRelay;

/**
 * Outbox consumer for {@code attempt.finished} events (LT §9.1, AC-16..AC-20).
 */
@Component
public class AttemptFinishedHandler implements OutboxHandler {

    public static final String TOPIC_ATTEMPT_FINISHED = "attempt.finished";

    private final OutboxRelay outboxRelay;
    private final AttemptGradingService attemptGradingService;
    private final ObjectMapper objectMapper;

    public AttemptFinishedHandler(
            OutboxRelay outboxRelay,
            AttemptGradingService attemptGradingService,
            ObjectMapper objectMapper) {
        this.outboxRelay = Objects.requireNonNull(outboxRelay);
        this.attemptGradingService = Objects.requireNonNull(attemptGradingService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @PostConstruct
    void registerHandler() {
        outboxRelay.register(TOPIC_ATTEMPT_FINISHED, this);
    }

    @PreDestroy
    void unregisterHandler() {
        outboxRelay.unregister(TOPIC_ATTEMPT_FINISHED);
    }

    @Override
    public void handle(OutboxEvent event) throws Exception {
        AttemptFinishedEvent payload =
                objectMapper.readValue(event.getPayload(), AttemptFinishedEvent.class);
        if (payload == null || payload.attemptId() == null) {
            return;
        }
        try {
            attemptGradingService.gradeFinishedAttempt(payload.attemptId());
        } catch (Exception ex) {
            if (event.getAttempts() + 1 >= OutboxRelay.DEFAULT_MAX_ATTEMPTS) {
                attemptGradingService.markGradingError(
                        payload.attemptId(),
                        ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
            }
            throw ex;
        }
    }
}
