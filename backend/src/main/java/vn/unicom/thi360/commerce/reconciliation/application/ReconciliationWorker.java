package vn.unicom.thi360.commerce.reconciliation.application;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Worker lập báo cáo đối soát cuối ngày lúc 02:00 giờ Việt Nam (TM 8.4, plan B5).
 */
@Component
@Profile("worker")
@ConditionalOnProperty(name = "thi360.commerce.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class ReconciliationWorker {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationWorker.class);

    private final ReconciliationService reconciliationService;

    public ReconciliationWorker(ReconciliationService reconciliationService) {
        this.reconciliationService = Objects.requireNonNull(reconciliationService);
    }

    @Scheduled(
            cron = "${thi360.commerce.reconciliation.cron:0 0 2 * * *}",
            zone = "${thi360.commerce.reconciliation.zone:Asia/Ho_Chi_Minh}")
    public void runDailyReconciliation() {
        try {
            reconciliationService.generateDailyReport();
        } catch (RuntimeException ex) {
            log.error("Daily reconciliation report generation failed", ex);
        }
    }
}
