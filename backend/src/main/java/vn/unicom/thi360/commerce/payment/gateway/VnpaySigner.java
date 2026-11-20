package vn.unicom.thi360.commerce.payment.gateway;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.util.StringUtils;

/**
 * Ký và kiểm chữ ký VNPAY 2.1.0 theo đúng quy tắc đã đo trên sandbox ({@code vectors.json}):
 * sắp xếp tên tham số tăng dần, bỏ giá trị rỗng, bỏ {@code vnp_SecureHash} và {@code vnp_SecureHashType},
 * mã hóa tên và giá trị theo {@link URLEncoder#encode(String, java.nio.charset.Charset)} UTF-8,
 * nối bằng {@code &amp;} và băm HMAC-SHA512 ra hex chữ thường.
 */
public final class VnpaySigner {

    private static final String HMAC_SHA512 = "HmacSHA512";
    private static final String SECURE_HASH_PARAM = "vnp_SecureHash";
    private static final String SECURE_HASH_TYPE_PARAM = "vnp_SecureHashType";

    private VnpaySigner() {
    }

    /** Dựng chuỗi {@code key=value&...} dùng để ký cho URL thanh toán, IPN và trang quay lại. */
    public static String buildPaySignData(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (!StringUtils.hasText(key) || value == null || value.isEmpty()) {
                continue;
            }
            if (SECURE_HASH_PARAM.equals(key) || SECURE_HASH_TYPE_PARAM.equals(key)) {
                continue;
            }
            sorted.put(key, value);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    /** Ký tham số {@code pay} / {@code return} / {@code ipn} bằng HMAC-SHA512 hex chữ thường. */
    public static String signPayParams(Map<String, String> params, String secret) {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalArgumentException("VNPAY hash secret must not be blank");
        }
        String signData = buildPaySignData(params);
        return hmacSha512Hex(signData, secret);
    }

    /** Kiểm tra {@code vnp_SecureHash} theo so sánh hằng thời gian, không phân biệt hoa/thường hex. */
    public static boolean verifyPayParams(Map<String, String> params, String secret) {
        if (params == null || params.isEmpty() || !StringUtils.hasText(secret)) {
            return false;
        }
        String provided = params.get(SECURE_HASH_PARAM);
        if (!StringUtils.hasText(provided)) {
            return false;
        }
        String normalizedProvided = provided.trim().toLowerCase(Locale.ROOT);
        String expected = signPayParams(params, secret);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                normalizedProvided.getBytes(StandardCharsets.US_ASCII));
    }

    /** Tính HMAC-SHA512 dạng hex chữ thường cho chuỗi dữ liệu bất kỳ (dùng chung cho {@code querydr}/{@code refund}). */
    public static String hmacSha512Hex(String data, String secret) {
        if (data == null || !StringUtils.hasText(secret)) {
            throw new IllegalArgumentException("data and secret must not be null/blank");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA512);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA512));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("Failed to compute HMAC-SHA512 signature", ex);
        }
    }
}
