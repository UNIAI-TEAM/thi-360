package vn.unicom.thi360.shared.mail;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình gửi thư {@code thi360.mail.*}. Máy chủ, cổng, tài khoản SMTP nằm ở
 * {@code spring.mail.*}; ở đây là phần của ứng dụng.
 *
 * @param from       địa chỉ người gửi ({@code THI360_MAIL_FROM}), có thể kèm tên hiển thị
 * @param tls        chế độ mã hóa SMTP ({@code THI360_SMTP_TLS}); giá trị lạ làm ứng dụng không khởi động
 * @param connectTimeout thời gian chờ kết nối SMTP
 * @param readTimeout    thời gian chờ đọc, ghi SMTP
 * @param batchSize  số thư lấy mỗi lượt
 * @param claimLease thời gian giữ chỗ một thư trong lúc gửi; hết hạn mà chưa có kết quả thì thư được lấy lại
 * @param sentRetention thời gian giữ thư đã gửi trước khi xóa
 * @param templatesLocation nơi chứa mẫu thư (vị trí tài nguyên Spring, kết thúc bằng dấu gạch chéo)
 */
@ConfigurationProperties("thi360.mail")
public record MailProperties(
        @DefaultValue("Thi 360 <no-reply@thi360.local>") String from,
        @DefaultValue("starttls") SmtpTls tls,
        @DefaultValue("PT10S") Duration connectTimeout,
        @DefaultValue("PT30S") Duration readTimeout,
        @DefaultValue("20") int batchSize,
        @DefaultValue("PT5M") Duration claimLease,
        @DefaultValue("P30D") Duration sentRetention,
        @DefaultValue("classpath:mail/templates/") String templatesLocation) {
}
