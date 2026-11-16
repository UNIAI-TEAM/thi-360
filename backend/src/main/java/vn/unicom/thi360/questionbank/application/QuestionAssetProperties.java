package vn.unicom.thi360.questionbank.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Giới hạn tải lên tài nguyên câu hỏi (spec 4.2, plan B5 — đặt trong
 * {@code thi360.questionbank.assets.*}): cỡ tệp theo loại, giới hạn tần suất theo người
 * (quyết định lane 8.1) và tiền tố khóa object storage (quyết định 8.2).
 */
@ConfigurationProperties(prefix = "thi360.questionbank.assets")
public record QuestionAssetProperties(
        @DefaultValue("5MB") DataSize maxImageSize,
        @DefaultValue("20MB") DataSize maxAttachmentSize,
        @DefaultValue("60") int uploadPerUserLimit,
        @DefaultValue("PT10M") Duration uploadPerUserWindow,
        @DefaultValue("questions/") String keyPrefix) {
}
