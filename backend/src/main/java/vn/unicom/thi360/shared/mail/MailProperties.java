package vn.unicom.thi360.shared.mail;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình gửi thư {@code thi360.mail.*}. Máy chủ, cổng, tài khoản SMTP nằm ở
 * {@code spring.mail.*}; ở đây là phần của ứng dụng. Giá trị sai làm ứng dụng không khởi động.
 *
 * @param from       địa chỉ người gửi ({@code THI360_MAIL_FROM}), có thể kèm tên hiển thị
 * @param tls        chế độ mã hóa SMTP ({@code THI360_SMTP_TLS}): {@code starttls} (mặc định), {@code tls}, {@code none}
 * @param connectTimeout thời gian chờ kết nối SMTP; số mili giây dương, vừa một {@code int}
 * @param readTimeout    thời gian chờ đọc, ghi SMTP; số mili giây dương, vừa một {@code int}
 * @param attemptTimeout hạn cứng của một lần gửi (kết nối, mọi lệnh, thân thư); số mili giây dương vừa
 *                       một {@code int} — dưới 1 ms đổi ra 0 ms và khóa mọi lần gửi
 * @param batchSize  số thư tối đa mỗi lượt, từ 1 đến {@value #MAX_BATCH_SIZE}
 * @param claimLease thời gian giữ chỗ một thư trong lúc gửi, ít nhất {@code attemptTimeout} cộng
 *                   {@link #SAFETY_MARGIN}; hết hạn mà chưa có kết quả thì thư được lấy lại
 * @param sentRetention thời gian giữ thư đã gửi trước khi xóa
 * @param pollDelay  nghỉ giữa hai lượt gửi của worker
 * @param cleanupCron lịch dọn thư đã gửi (cron Spring)
 * @param templatesLocation nơi chứa mẫu thư (vị trí tài nguyên Spring, kết thúc bằng dấu gạch chéo)
 */
@ConfigurationProperties("thi360.mail")
public record MailProperties(
        String from,
        SmtpTls tls,
        Duration connectTimeout,
        Duration readTimeout,
        Duration attemptTimeout,
        int batchSize,
        Duration claimLease,
        Duration sentRetention,
        Duration pollDelay,
        String cleanupCron,
        String templatesLocation) {

    public static final int MAX_BATCH_SIZE = 20;

    /** Mặc định dùng chung cho {@link MailProperties} và các chú thích {@code @Scheduled} của {@link MailWorker}. */
    static final String DEFAULT_POLL_DELAY = "PT5S";

    static final String DEFAULT_CLEANUP_CRON = "0 30 3 * * *";

    /**
     * Khoảng giữ lại cuối mỗi giữ chỗ để ghi kết quả và để lần gửi dừng trước khi worker khác lấy lại thư
     * (AD-19). Hạn của lần gửi là hạn giữ chỗ trừ khoảng này, nên giữ chỗ phải dài hơn {@code attemptTimeout}
     * ít nhất bằng khoảng này.
     */
    static final Duration SAFETY_MARGIN = Duration.ofSeconds(5);

    private static final Duration ONE_MILLI = Duration.ofMillis(1);
    private static final Duration MAX_INT_MILLIS = Duration.ofMillis(Integer.MAX_VALUE);

    public MailProperties {
        requireInProviderRange("thi360.mail.connect-timeout", connectTimeout);
        requireInProviderRange("thi360.mail.read-timeout", readTimeout);
        requireInProviderRange("thi360.mail.attempt-timeout", attemptTimeout);
        requireInProviderRange("thi360.mail.claim-lease", claimLease);
        requirePositive("thi360.mail.sent-retention", sentRetention);
        requirePositive("thi360.mail.poll-delay", pollDelay);
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "thi360.mail.batch-size must be between 1 and " + MAX_BATCH_SIZE + " (was " + batchSize + ")");
        }
        if (claimLease.compareTo(attemptTimeout.plus(SAFETY_MARGIN)) < 0) {
            throw new IllegalArgumentException(
                    "thi360.mail.claim-lease must be at least thi360.mail.attempt-timeout plus the " + SAFETY_MARGIN
                            + " safety margin (was " + claimLease + " with attempt-timeout " + attemptTimeout
                            + "), or a fresh claim could not start SMTP");
        }
    }

    /** Hàm dựng mà Spring Boot dùng khi gắn cấu hình: {@code tls} đọc qua {@link SmtpTls#parse}. */
    @ConstructorBinding
    public MailProperties(
            @DefaultValue("Thi 360 <no-reply@thi360.local>") String from,
            @DefaultValue("starttls") String tls,
            @DefaultValue("PT10S") Duration connectTimeout,
            @DefaultValue("PT30S") Duration readTimeout,
            @DefaultValue("PT2M") Duration attemptTimeout,
            @DefaultValue("20") int batchSize,
            @DefaultValue("PT5M") Duration claimLease,
            @DefaultValue("P30D") Duration sentRetention,
            @DefaultValue(DEFAULT_POLL_DELAY) Duration pollDelay,
            @DefaultValue(DEFAULT_CLEANUP_CRON) String cleanupCron,
            @DefaultValue("classpath:mail/templates/") String templatesLocation) {
        this(
                from,
                SmtpTls.parse(tls),
                connectTimeout,
                readTimeout,
                attemptTimeout,
                batchSize,
                claimLease,
                sentRetention,
                pollDelay,
                cleanupCron,
                templatesLocation);
    }

    private static void requirePositive(String name, Duration value) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be a positive duration (was " + value + ")");
        }
    }

    /** Thời gian chờ của JavaMail là số mili giây {@code int}: phải dương sau khi đổi, không tràn. */
    private static void requireInProviderRange(String name, Duration value) {
        if (value == null || value.compareTo(ONE_MILLI) < 0 || value.compareTo(MAX_INT_MILLIS) > 0) {
            throw new IllegalArgumentException(
                    name + " must be between 1 ms and " + Integer.MAX_VALUE + " ms (was " + value + ")");
        }
    }
}
