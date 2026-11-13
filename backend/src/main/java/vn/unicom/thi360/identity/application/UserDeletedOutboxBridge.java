package vn.unicom.thi360.identity.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.identity.spi.UserDeleted;
import vn.unicom.thi360.shared.outbox.OutboxEvent;
import vn.unicom.thi360.shared.outbox.OutboxRelay;

@Component
public class UserDeletedOutboxBridge implements SmartInitializingSingleton {

    private final OutboxRelay outboxRelay;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public UserDeletedOutboxBridge(
            OutboxRelay outboxRelay,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher) {
        this.outboxRelay = outboxRelay;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void afterSingletonsInstantiated() {
        outboxRelay.register(UserDeleted.OUTBOX_TOPIC, this::handle);
    }

    private void handle(OutboxEvent outboxEvent) throws Exception {
        UserDeleted payload = objectMapper.readValue(outboxEvent.getPayload(), UserDeleted.class);
        eventPublisher.publishEvent(payload);
    }
}
