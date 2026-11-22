package vn.unicom.thi360.commerce.payment.gateway;

import java.util.Map;

/**
 * Bean dự phòng khi {@code thi360.vnpay.mode} khác {@code MOCK} và chưa có bản cài đặt thật (CM-3b).
 * Được đăng ký qua {@link VnpayConfiguration} để đánh giá {@code @ConditionalOnMissingBean} sau khi quét component.
 */
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
