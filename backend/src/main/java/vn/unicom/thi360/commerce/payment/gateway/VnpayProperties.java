package vn.unicom.thi360.commerce.payment.gateway;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình cổng thanh toán VNPAY ({@code thi360.vnpay.*}).
 */
@ConfigurationProperties(prefix = "thi360.vnpay")
public record VnpayProperties(
        String mode,
        String tmnCode,
        String hashSecret,
        String payUrl,
        String apiUrl,
        List<String> ipnAllowedIps,
        String mockSecret,
        String mockTmnCode,
        String mockPayUrl
) {
    public VnpayProperties {
        ipnAllowedIps = ipnAllowedIps == null ? List.of() : List.copyOf(ipnAllowedIps);
    }
}
