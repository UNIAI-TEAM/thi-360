package vn.unicom.thi360.questionbank.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình của worker dọn tệp mồ côi (plan D/BE-2b): tuổi tối thiểu của một tài nguyên
 * không có ref để bị xóa và số dòng tối đa mỗi lần chạy. Scheduling tắt ở profile
 * {@code test} qua {@code thi360.questionbank.assets.cleanup.scheduling.enabled}
 * (application.yml).
 */
@ConfigurationProperties(prefix = "thi360.questionbank.assets.cleanup")
public record QuestionAssetCleanupProperties(
        @DefaultValue("PT24H") Duration orphanAge,
        @DefaultValue("500") int batchSize) {
}
