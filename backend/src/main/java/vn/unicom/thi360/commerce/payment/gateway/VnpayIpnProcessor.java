package vn.unicom.thi360.commerce.payment.gateway;

import java.util.Map;

/**
 * Cổng nội bộ chuyển thông số IPN đã ký từ {@link MockVnpayController} sang {@code VnpayIpnService}.
 */
public interface VnpayIpnProcessor {

    IpnReply process(Map<String, String> params, String clientIp);

    record IpnReply(String rspCode, String message) {
    }
}
