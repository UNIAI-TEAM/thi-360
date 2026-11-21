package vn.unicom.thi360.commerce.payment.vnpay;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình quy tắc mã hóa URL và thuật toán băm chữ ký VNPAY ({@code thi360.vnpay.signing.*}).
 * Cho phép đổi cấu hình khi đối chiếu sandbox/production mà không sửa mã (TM 8.4, AC-23).
 */
@ConfigurationProperties(prefix = "thi360.vnpay.signing")
public record VnpaySigningProperties(
        PayEncodingRule payEncoding,
        VnpayHashAlgorithm payHashAlgorithm,
        VnpayHashAlgorithm queryDrHashAlgorithm,
        VnpayHashAlgorithm refundHashAlgorithm
) {

    public VnpaySigningProperties {
        payEncoding = payEncoding == null ? PayEncodingRule.JAVA_URL_ENCODER : payEncoding;
        payHashAlgorithm = payHashAlgorithm == null ? VnpayHashAlgorithm.HMAC_SHA512 : payHashAlgorithm;
        queryDrHashAlgorithm = queryDrHashAlgorithm == null ? VnpayHashAlgorithm.HMAC_SHA512 : queryDrHashAlgorithm;
        refundHashAlgorithm = refundHashAlgorithm == null ? VnpayHashAlgorithm.HMAC_SHA512 : refundHashAlgorithm;
    }

    /** Quy tắc mã hóa khóa và giá trị trong chuỗi ký {@code pay} / {@code return} / {@code ipn}. */
    public enum PayEncodingRule {
        /** {@link java.net.URLEncoder} UTF-8: khoảng trắng thành {@code +}, giữ {@code * - . _}, {@code ~} thành {@code %7E}. */
        JAVA_URL_ENCODER,
        /** RFC 3986: {@code *} thành {@code %2A}, giữ nguyên {@code ~}, khoảng trắng thành {@code %20}. */
        RFC_3986,
        /** Python {@code urllib.parse.quote_plus}: {@code *} thành {@code %2A}, giữ nguyên {@code ~}, khoảng trắng thành {@code +}. */
        PYTHON_QUOTE_PLUS,
        /** Giữ nguyên chuỗi gốc, không mã hóa URL. */
        RAW
    }

    /** Thuật toán băm chữ ký VNPAY 2.1.0, kết quả dạng hex chữ thường. */
    public enum VnpayHashAlgorithm {
        HMAC_SHA512,
        HMAC_SHA256,
        SHA256,
        SHA512
    }
}
