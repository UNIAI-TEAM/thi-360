package vn.unicom.thi360.commerce.payment.application;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import vn.unicom.thi360.commerce.payment.gateway.VnpayProperties;
import vn.unicom.thi360.shared.security.ExemptIpMatcher;

/**
 * Kiểm tra IP nguồn của VNPAY IPN dựa trên {@code thi360.vnpay.ipn-allowed-ips} (TM 8.4, AC-27).
 * Danh sách rỗng nghĩa là không lọc theo IP; giá trị không phải IP/CIDR hợp lệ sẽ chặn khởi động.
 */
@Component
public class VnpayIpnIpAllowlist {

    private final ExemptIpMatcher matcher;
    private final boolean enabled;

    public VnpayIpnIpAllowlist(VnpayProperties properties) {
        List<String> entries = parseAndValidate(properties == null ? List.of() : properties.ipnAllowedIps());
        if (entries.isEmpty()) {
            this.enabled = false;
            this.matcher = null;
        } else {
            this.enabled = true;
            this.matcher = new ExemptIpMatcher(entries);
        }
    }

    public boolean isAllowed(String clientIp) {
        if (!enabled) {
            return true;
        }
        return StringUtils.hasText(clientIp) && matcher.matches(clientIp.trim());
    }

    public boolean isEnabled() {
        return enabled;
    }

    private static List<String> parseAndValidate(List<String> configured) {
        List<String> normalized = new ArrayList<>();
        if (configured != null) {
            for (String item : configured) {
                if (!StringUtils.hasText(item)) {
                    continue;
                }
                for (String token : item.split(",")) {
                    String trimmed = token.trim();
                    if (!trimmed.isEmpty()) {
                        ExemptIpMatcher.requireIpOrCidr(trimmed);
                        normalized.add(trimmed);
                    }
                }
            }
        }
        return List.copyOf(normalized);
    }
}
