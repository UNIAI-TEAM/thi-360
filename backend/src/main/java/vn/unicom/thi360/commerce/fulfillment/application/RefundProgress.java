package vn.unicom.thi360.commerce.fulfillment.application;

import java.util.UUID;

/**
 * Cổng kiểm tra đơn hàng có yêu cầu hoàn tiền chưa bị từ chối trước khi cấp quyền (BS / TM 8.5).
 */
public interface RefundProgress {

    boolean hasNonRejectedRefund(UUID orderId);
}
