package vn.unicom.thi360.commerce.reconciliation.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình lịch chạy báo cáo đối soát cuối ngày ({@code thi360.commerce.reconciliation.*}).
 */
@ConfigurationProperties(prefix = "thi360.commerce.reconciliation")
public record ReconciliationProperties(
        @DefaultValue("0 0 2 * * *") String cron,
        @DefaultValue("Asia/Ho_Chi_Minh") String zone
) {

    public ReconciliationProperties {
        cron = (cron == null || cron.isBlank()) ? "0 0 2 * * *" : cron.trim();
        zone = (zone == null || zone.isBlank()) ? "Asia/Ho_Chi_Minh" : zone.trim();
    }

    public ReconciliationProperties() {
        this("0 0 2 * * *", "Asia/Ho_Chi_Minh");
    }
}
