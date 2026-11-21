package vn.unicom.thi360.commerce.payment.vnpay;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Cấu hình kết nối HTTP và IP máy chủ gọi API VNPAY ({@code thi360.vnpay.client.*}).
 */
@ConfigurationProperties(prefix = "thi360.vnpay.client")
public record VnpayClientProperties(
        Duration connectTimeout,
        Duration readTimeout,
        String serverIp
) {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(10);
    private static final String DEFAULT_SERVER_IP = "127.0.0.1";

    public VnpayClientProperties {
        connectTimeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : requirePositive(connectTimeout, "connectTimeout");
        readTimeout = readTimeout == null ? DEFAULT_READ_TIMEOUT : requirePositive(readTimeout, "readTimeout");
        serverIp = StringUtils.hasText(serverIp) ? serverIp.trim() : DEFAULT_SERVER_IP;
    }

    private static Duration requirePositive(Duration duration, String fieldName) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("thi360.vnpay.client." + fieldName + " must be positive");
        }
        return duration;
    }
}
