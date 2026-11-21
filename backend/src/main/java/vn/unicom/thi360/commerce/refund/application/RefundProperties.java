package vn.unicom.thi360.commerce.refund.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import vn.unicom.thi360.commerce.refund.domain.VnpayCode02Policy;

/**
 * Cấu hình hoàn tiền ({@code thi360.commerce.refund.*}, TM Q14, AD-116).
 */
@ConfigurationProperties(prefix = "thi360.commerce.refund")
public record RefundProperties(
        @DefaultValue("UNKNOWN") VnpayCode02Policy vnpayCode02Policy,
        @DefaultValue("127.0.0.1") String serverIp
) {

    @ConstructorBinding
    public RefundProperties {
        vnpayCode02Policy = vnpayCode02Policy == null ? VnpayCode02Policy.UNKNOWN : vnpayCode02Policy;
        serverIp = (serverIp == null || serverIp.isBlank()) ? "127.0.0.1" : serverIp.trim();
    }

    public RefundProperties() {
        this(VnpayCode02Policy.UNKNOWN, "127.0.0.1");
    }
}
