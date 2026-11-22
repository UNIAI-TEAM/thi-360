package vn.unicom.thi360.commerce.storefront.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.commerce.bundle.application.BundleEventService;
import vn.unicom.thi360.exam.spi.ExamEventTopics;
import vn.unicom.thi360.shared.outbox.OutboxEvent;
import vn.unicom.thi360.shared.outbox.OutboxHandler;
import vn.unicom.thi360.shared.outbox.OutboxRelay;

/**
 * Receiver of {@code ExamPublished} and {@code ExamDiscontinued} (TM 3, plan CM-1). It trusts only the exam id of the
 * event: the row is built from {@code PublishedExamPort.findServing}, so the event body cannot put anything on the
 * storefront. {@link OutboxRelay} keeps one handler per topic: CM-6 extends this class, it does not register again.
 */
@Component
public class StorefrontEventHandler implements OutboxHandler {

    private final OutboxRelay relay;
    private final StorefrontProjectionService projection;
    private final BundleEventService bundleEvents;
    private final ObjectMapper mapper;

    public StorefrontEventHandler(
            OutboxRelay relay,
            StorefrontProjectionService projection,
            BundleEventService bundleEvents,
            ObjectMapper mapper) {
        this.relay = relay;
        this.projection = projection;
        this.bundleEvents = bundleEvents;
        this.mapper = mapper;
    }

    @PostConstruct
    void register() {
        relay.register(ExamEventTopics.PUBLISHED, this);
        relay.register(ExamEventTopics.DISCONTINUED, this);
    }

    @PreDestroy
    void unregister() {
        relay.unregister(ExamEventTopics.PUBLISHED);
        relay.unregister(ExamEventTopics.DISCONTINUED);
    }

    @Override
    public void handle(OutboxEvent event) throws Exception {
        ExamEventTopics.Payload payload = mapper.readValue(event.getPayload(), ExamEventTopics.Payload.class);
        projection.project(payload.examId());
        bundleEvents.onExamEvent(event.getTopic(), payload.examId(), payload.versionNo());
    }
}
