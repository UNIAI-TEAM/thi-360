package vn.unicom.thi360.commerce.payment.gateway;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Bean dự phòng khi {@code thi360.vnpay.mode} khác {@code MOCK} và chưa có bản cài đặt thật (CM-3b).
 * Giúp context khởi động sạch ở {@code SANDBOX} để kiểm chứng {@code /mock-vnpay} trả 404 (AC-18).
 */
@Component
@ConditionalOnMissingBean(VnpayGateway.class)
public class UnsupportedVnpayGateway implements VnpayGateway {

    @Override
    public String pay(PayRequest request) {
        throw new UnsupportedOperationException("VNPAY real gateway is not configured in this build slice");
    }

    @Override
    public boolean verify(Map<String, String> params) {
        return false;
    }

    @Override
    public QueryDrResult queryDr(QueryDrRequest request) {
        throw new UnsupportedOperationException("VNPAY real gateway is not configured in this build slice");
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        throw new UnsupportedOperationException("VNPAY real gateway is not configured in this build slice");
    }
}
