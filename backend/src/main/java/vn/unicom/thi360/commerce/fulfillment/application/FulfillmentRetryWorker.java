package vn.unicom.thi360.commerce.fulfillment.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.infrastructure.PurchaseOrderRepository;

/**
 * Worker tự động thử cấp quyền lại cho đơn {@code PAID} trong cửa sổ 30 phút kể từ lúc thu tiền (TM 8.5, AC-39).
 */
@Component
@Profile("worker")
@ConditionalOnProperty(name = "thi360.commerce.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class FulfillmentRetryWorker {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentRetryWorker.class);
    private static final Duration RETRY_WINDOW = Duration.ofMinutes(30);
    private static final int BATCH_SIZE = 20;

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final FulfillmentService fulfillmentService;
    private final Clock clock;

    public FulfillmentRetryWorker(
            PurchaseOrderRepository purchaseOrderRepository,
            FulfillmentService fulfillmentService,
            Clock clock) {
        this.purchaseOrderRepository = Objects.requireNonNull(purchaseOrderRepository);
        this.fulfillmentService = Objects.requireNonNull(fulfillmentService);
        this.clock = Objects.requireNonNull(clock);
    }

    @Scheduled(fixedDelayString = "${thi360.commerce.fulfillment-retry.interval:PT1M}")
    public void retryPendingFulfillments() {
        Instant cutoff = clock.instant().minus(RETRY_WINDOW);
        List<UUID> orderIds = purchaseOrderRepository.findPaidIdsSince(
                OrderState.PAID, cutoff, PageRequest.of(0, BATCH_SIZE));
        for (UUID orderId : orderIds) {
            try {
                fulfillmentService.fulfill(orderId);
            } catch (RuntimeException ex) {
                log.warn("Unexpected error retrying fulfillment for order {}", orderId, ex);
            }
        }
    }
}
