package vn.unicom.thi360.commerce.payment.vnpay;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import vn.unicom.thi360.commerce.payment.vnpay.VnpaySigningProperties.PayEncodingRule;
import vn.unicom.thi360.commerce.payment.vnpay.VnpaySigningProperties.VnpayHashAlgorithm;

/**
 * Bộ ký và kiểm chữ ký cho cả ba nhóm giao dịch VNPAY 2.1.0 ({@code pay}, {@code querydr}, {@code refund}),
 * đọc quy tắc mã hóa và thuật toán băm từ {@link VnpaySigningProperties} (TM 8.4, AC-1, AC-23).
 */
@Component
public class VnpayRealSigner {

    private static final String SECURE_HASH_PARAM = "vnp_SecureHash";
    private static final String SECURE_HASH_TYPE_PARAM = "vnp_SecureHashType";

    private static final List<String> QUERYDR_REQUEST_FIELDS = List.of(
            "vnp_RequestId",
            "vnp_Version",
            "vnp_Command",
            "vnp_TmnCode",
            "vnp_TxnRef",
            "vnp_TransactionDate",
            "vnp_CreateDate",
            "vnp_IpAddr",
            "vnp_OrderInfo"
    );

    private static final List<String> QUERYDR_RESPONSE_FIELDS = List.of(
            "vnp_ResponseId",
            "vnp_Command",
            "vnp_ResponseCode",
            "vnp_Message",
            "vnp_TmnCode",
            "vnp_TxnRef",
            "vnp_Amount",
            "vnp_BankCode",
            "vnp_PayDate",
            "vnp_TransactionNo",
            "vnp_TransactionType",
            "vnp_TransactionStatus",
            "vnp_OrderInfo",
            "vnp_PromotionCode",
            "vnp_PromotionAmount"
    );

    private static final List<String> REFUND_REQUEST_FIELDS = List.of(
            "vnp_RequestId",
            "vnp_Version",
            "vnp_Command",
            "vnp_TmnCode",
            "vnp_TransactionType",
            "vnp_TxnRef",
            "vnp_Amount",
            "vnp_TransactionNo",
            "vnp_TransactionDate",
            "vnp_CreateBy",
            "vnp_CreateDate",
            "vnp_IpAddr",
            "vnp_OrderInfo"
    );

    private static final List<String> REFUND_RESPONSE_FIELDS = List.of(
            "vnp_ResponseId",
            "vnp_Command",
            "vnp_ResponseCode",
            "vnp_Message",
            "vnp_TmnCode",
            "vnp_TxnRef",
            "vnp_Amount",
            "vnp_BankCode",
            "vnp_PayDate",
            "vnp_TransactionNo",
            "vnp_TransactionType",
            "vnp_TransactionStatus",
            "vnp_OrderInfo"
    );

    private final VnpaySigningProperties signingProperties;

    public VnpayRealSigner(VnpaySigningProperties signingProperties) {
        this.signingProperties = Objects.requireNonNull(signingProperties, "signingProperties must not be null");
    }

    /** Dựng chuỗi {@code key=value&...} sắp tăng dần theo tên cho {@code pay} / {@code return} / {@code ipn}. */
    public String buildPaySignData(Map<String, String> params) {
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
        PayEncodingRule rule = signingProperties.payEncoding();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(encodePayComponent(entry.getKey(), rule))
                    .append('=')
                    .append(encodePayComponent(entry.getValue(), rule));
        }
        return sb.toString();
    }

    /** Ký tham số {@code pay} / {@code return} / {@code ipn}. */
    public String signPay(Map<String, String> params, String secret) {
        requireSecret(secret);
        String signData = buildPaySignData(params);
        return computeHash(signData, secret, signingProperties.payHashAlgorithm());
    }

    /** Kiểm tra {@code vnp_SecureHash} của {@code pay} / {@code return} / {@code ipn} theo thời gian hằng. */
    public boolean verifyPay(Map<String, String> params, String secret) {
        if (params == null || params.isEmpty() || !StringUtils.hasText(secret)) {
            return false;
        }
        String provided = params.get(SECURE_HASH_PARAM);
        if (!StringUtils.hasText(provided)) {
            return false;
        }
        String expected = signPay(params, secret);
        return constantTimeHexEquals(expected, provided);
    }

    /** Dựng chuỗi nối {@code |} gồm 9 trường yêu cầu {@code querydr} theo TM 8.4. */
    public String buildQueryDrRequestSignData(Map<String, String> fields) {
        return joinPipeFields(fields, QUERYDR_REQUEST_FIELDS);
    }

    /** Ký yêu cầu {@code querydr}. */
    public String signQueryDrRequest(Map<String, String> fields, String secret) {
        requireSecret(secret);
        return computeHash(
                buildQueryDrRequestSignData(fields),
                secret,
                signingProperties.queryDrHashAlgorithm());
    }

    /** Dựng chuỗi nối {@code |} gồm 15 trường phản hồi {@code querydr} theo TM 8.4. */
    public String buildQueryDrResponseSignData(Map<String, ?> fields) {
        return joinPipeFields(fields, QUERYDR_RESPONSE_FIELDS);
    }

    /** Kiểm tra {@code vnp_SecureHash} trong phản hồi {@code querydr}. */
    public boolean verifyQueryDrResponse(Map<String, ?> fields, String secret) {
        return verifyPipeResponse(fields, QUERYDR_RESPONSE_FIELDS, signingProperties.queryDrHashAlgorithm(), secret);
    }

    /** Dựng chuỗi nối {@code |} gồm 13 trường yêu cầu {@code refund} theo TM 8.4. */
    public String buildRefundRequestSignData(Map<String, String> fields) {
        return joinPipeFields(fields, REFUND_REQUEST_FIELDS);
    }

    /** Ký yêu cầu {@code refund}. */
    public String signRefundRequest(Map<String, String> fields, String secret) {
        requireSecret(secret);
        return computeHash(
                buildRefundRequestSignData(fields),
                secret,
                signingProperties.refundHashAlgorithm());
    }

    /** Dựng chuỗi nối {@code |} gồm 13 trường phản hồi {@code refund} theo TM 8.4. */
    public String buildRefundResponseSignData(Map<String, ?> fields) {
        return joinPipeFields(fields, REFUND_RESPONSE_FIELDS);
    }

    /** Kiểm tra {@code vnp_SecureHash} trong phản hồi {@code refund}. */
    public boolean verifyRefundResponse(Map<String, ?> fields, String secret) {
        return verifyPipeResponse(fields, REFUND_RESPONSE_FIELDS, signingProperties.refundHashAlgorithm(), secret);
    }

    private static boolean verifyPipeResponse(
            Map<String, ?> fields,
            List<String> fieldOrder,
            VnpayHashAlgorithm algorithm,
            String secret) {
        if (fields == null || fields.isEmpty() || !StringUtils.hasText(secret)) {
            return false;
        }
        Object rawHash = fields.get(SECURE_HASH_PARAM);
        if (rawHash == null) {
            return false;
        }
        String provided = String.valueOf(rawHash);
        if (!StringUtils.hasText(provided)) {
            return false;
        }
        String signData = joinPipeFields(fields, fieldOrder);
        String expected = computeHash(signData, secret, algorithm);
        return constantTimeHexEquals(expected, provided);
    }

    private static String joinPipeFields(Map<String, ?> fields, List<String> fieldOrder) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fieldOrder.size(); i++) {
            if (i > 0) {
                sb.append('|');
            }
            Object raw = fields == null ? null : fields.get(fieldOrder.get(i));
            sb.append(Objects.toString(raw, ""));
        }
        return sb.toString();
    }

    private static String encodePayComponent(String value, PayEncodingRule rule) {
        return switch (rule) {
            case JAVA_URL_ENCODER -> URLEncoder.encode(value, StandardCharsets.UTF_8);
            case RFC_3986 -> URLEncoder.encode(value, StandardCharsets.UTF_8)
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~");
            case PYTHON_QUOTE_PLUS -> URLEncoder.encode(value, StandardCharsets.UTF_8)
                    .replace("*", "%2A")
                    .replace("%7E", "~");
            case RAW -> value;
        };
    }

    private static String computeHash(String data, String secret, VnpayHashAlgorithm algorithm) {
        if (data == null) {
            throw new IllegalArgumentException("Sign data must not be null");
        }
        requireSecret(secret);
        return switch (algorithm) {
            case HMAC_SHA512 -> hmacHex("HmacSHA512", data, secret);
            case HMAC_SHA256 -> hmacHex("HmacSHA256", data, secret);
            case SHA256 -> digestHex("SHA-256", secret + data);
            case SHA512 -> digestHex("SHA-512", secret + data);
        };
    }

    private static String hmacHex(String jcaAlgorithm, String data, String secret) {
        try {
            Mac mac = Mac.getInstance(jcaAlgorithm);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), jcaAlgorithm));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("Failed to compute " + jcaAlgorithm + " signature");
        }
    }

    private static String digestHex(String jcaAlgorithm, String input) {
        try {
            MessageDigest md = MessageDigest.getInstance(jcaAlgorithm);
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Failed to compute " + jcaAlgorithm + " digest");
        }
    }

    private static boolean constantTimeHexEquals(String expectedLowerHex, String providedHex) {
        String normalizedProvided = providedHex.trim().toLowerCase(Locale.ROOT);
        return MessageDigest.isEqual(
                expectedLowerHex.getBytes(StandardCharsets.US_ASCII),
                normalizedProvided.getBytes(StandardCharsets.US_ASCII));
    }

    private static void requireSecret(String secret) {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalArgumentException("VNPAY hash secret must not be blank");
        }
    }
}
