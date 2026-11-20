package vn.unicom.thi360.commerce.fulfillment.application;

import java.util.UUID;
import org.springframework.context.annotation.Fallback;
import org.springframework.stereotype.Component;

/**
 * Bản tạm trả {@code false} cho tới khi module hoàn tiền (CM-4a) cung cấp {@link RefundProgress} thật.
 */
@Component
@Fallback
public class ProvisionalRefundProgress implements RefundProgress {

    @Override
    public boolean hasNonRejectedRefund(UUID orderId) {
        return false;
    }
}
