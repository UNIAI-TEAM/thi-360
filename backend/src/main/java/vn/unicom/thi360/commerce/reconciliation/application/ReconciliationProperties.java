package vn.unicom.thi360.commerce.reconciliation.application;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.scheduling.support.CronExpression;

/**
 * Cấu hình lịch chạy báo cáo đối soát cuối ngày ({@code thi360.commerce.reconciliation.*}).
 */
@ConfigurationProperties(prefix = "thi360.commerce.reconciliation")
public record ReconciliationProperties(
        @DefaultValue("0 0 2 * * *") String cron,
        @DefaultValue("Asia/Ho_Chi_Minh") String zone
) {

    @ConstructorBinding
    public ReconciliationProperties {
        cron = (cron == null || cron.isBlank()) ? "0 0 2 * * *" : cron.trim();
        zone = (zone == null || zone.isBlank()) ? "Asia/Ho_Chi_Minh" : zone.trim();
        CronExpression.parse(cron);
        ZoneId.of(zone);
    }

    public ReconciliationProperties() {
        this("0 0 2 * * *", "Asia/Ho_Chi_Minh");
    }
}
