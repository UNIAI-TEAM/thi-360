package vn.unicom.thi360.commerce.order.expiry;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Worker định kỳ mỗi phút kiểm tra và đóng các đơn {@code PENDING_PAYMENT} đã quá {@code expires_at}
 * sau khi tra {@code querydr} cho các lần thanh toán còn mở (TM 8.6, plan B5).
 */
@Component
@Profile("worker")
@ConditionalOnProperty(name = "thi360.commerce.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class OrderExpiryWorker {

    private static final Logger log = LoggerFactory.getLogger(OrderExpiryWorker.class);

    private final OrderExpiryService orderExpiryService;

    public OrderExpiryWorker(OrderExpiryService orderExpiryService) {
        this.orderExpiryService = Objects.requireNonNull(orderExpiryService);
    }

    @Scheduled(
            fixedDelayString = "${thi360.commerce.order-expiry.interval:PT1M}",
            initialDelayString = "${thi360.commerce.order-expiry.interval:PT1M}")
    public void expireOrders() {
        try {
            orderExpiryService.expirePendingOrders();
        } catch (RuntimeException ex) {
            log.warn("Unexpected error in OrderExpiryWorker cycle", ex);
        }
    }
}
