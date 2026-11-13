package vn.unicom.thi360.audit.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.audit.domain.SensitiveDataMasker;
import vn.unicom.thi360.audit.infrastructure.AuditOutboxPayload;
import vn.unicom.thi360.audit.infrastructure.AuditStoreRepository;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditOutcome;
import vn.unicom.thi360.shared.outbox.OutboxEvent;
import vn.unicom.thi360.shared.outbox.OutboxHandler;
import vn.unicom.thi360.shared.outbox.OutboxRelay;

@Component
public class AuditOutboxHandler implements OutboxHandler {

    private final OutboxRelay outboxRelay;
    private final AuditStoreRepository repository;
    private final ObjectMapper objectMapper;

    public AuditOutboxHandler(
            OutboxRelay outboxRelay,
            AuditStoreRepository repository,
            ObjectMapper objectMapper) {
        this.outboxRelay = outboxRelay;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void registerHandler() {
        outboxRelay.register(AuditRecorderImpl.AUDIT_OUTBOX_TOPIC, this);
    }

    @PreDestroy
    void unregisterHandler() {
        outboxRelay.unregister(AuditRecorderImpl.AUDIT_OUTBOX_TOPIC);
    }

    @Override
    public void handle(OutboxEvent event) throws Exception {
        AuditOutboxPayload raw = objectMapper.readValue(event.getPayload(), AuditOutboxPayload.class);
        UUID id = raw.id() != null ? raw.id() : event.getId();
        Instant occurredAt = raw.occurredAt() != null ? raw.occurredAt() : event.getCreatedAt();
        AuditActorType actorType = raw.actorType() != null ? raw.actorType() : AuditActorType.SYSTEM;
        AuditOutcome outcome = raw.outcome() != null ? raw.outcome() : AuditOutcome.SUCCESS;
        String traceId = (raw.traceId() != null && !raw.traceId().isBlank()) ? raw.traceId() : event.getTraceId();

        AuditOutboxPayload sanitized = new AuditOutboxPayload(
                id,
                occurredAt,
                raw.actorId(),
                raw.actorUsername(),
                raw.actorRoles(),
                actorType,
                raw.module(),
                raw.action(),
                raw.targetType(),
                raw.targetId(),
                raw.targetLabel(),
                SensitiveDataMasker.mask(raw.before()),
                SensitiveDataMasker.mask(raw.after()),
                raw.reason(),
                outcome,
                raw.errorCode(),
                traceId,
                raw.ip());

        repository.insertIdempotent(sanitized);
    }
}
