package vn.unicom.thi360.commerce.payment.vnpay;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Worker định kỳ mỗi 5 phút tra cứu kết quả các lần thanh toán {@code CREATED}/{@code UNKNOWN}
 * qua VNPAY {@code querydr} (TM 8.4, plan B5).
 */
@Component
@Profile("worker")
@ConditionalOnProperty(name = "thi360.commerce.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class QueryDrWorker {

    private static final Logger log = LoggerFactory.getLogger(QueryDrWorker.class);

    private final QueryDrService queryDrService;

    public QueryDrWorker(QueryDrService queryDrService) {
        this.queryDrService = Objects.requireNonNull(queryDrService);
    }

    @Scheduled(
            fixedDelayString = "${thi360.commerce.querydr.interval:PT5M}",
            initialDelayString = "${thi360.commerce.querydr.interval:PT5M}")
    public void pollPendingPayments() {
        try {
            queryDrService.runQueryCycle();
        } catch (RuntimeException ex) {
            log.warn("Unexpected error in QueryDrWorker cycle", ex);
        }
    }
}
