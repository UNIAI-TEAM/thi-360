package vn.unicom.thi360.commerce.payment.gateway;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Bản cài giả lập {@link VnpayGateway} cho chế độ {@code MOCK} (TM 8.4, plan CM-3a).
 */
@Component
@Conditional(MockVnpayModeCondition.class)
public class MockVnpayGateway implements VnpayGateway {

    public static final String DEFAULT_MOCK_TMN_CODE = "DEV-MOCK";
    public static final String DEFAULT_MOCK_SECRET = "dev-only-mock-vnpay-signing-key";
    private static final ZoneId GMT_PLUS_7 = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNP_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final VnpayProperties properties;
    private final Clock clock;

    public MockVnpayGateway(VnpayProperties properties, Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public String pay(PayRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.amountVnd() <= 0) {
            throw new IllegalArgumentException("amountVnd must be positive");
        }
        long scaledAmount = Math.multiplyExact(request.amountVnd(), 100L);
        String locale = "en".equalsIgnoreCase(request.locale()) ? "en" : "vn";
        String orderInfo = StringUtils.hasText(request.orderInfo())
                ? request.orderInfo()
                : "Thanh toan don " + request.orderCode();

        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", resolveTmnCode());
        params.put("vnp_Amount", Long.toString(scaledAmount));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", request.txnRef());
        params.put("vnp_OrderInfo", orderInfo);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", locale);
        params.put("vnp_ReturnUrl", request.returnUrl());
        params.put("vnp_IpAddr", StringUtils.hasText(request.ipAddr()) ? request.ipAddr() : "127.0.0.1");
        params.put("vnp_CreateDate", request.createDate());
        params.put("vnp_ExpireDate", request.expireDate());

        String secureHash = signParams(params);
        String baseUrl = StringUtils.hasText(properties.mockPayUrl()) ? properties.mockPayUrl().trim() : "/mock-vnpay";
        return appendQuery(baseUrl, params, secureHash);
    }

    @Override
    public boolean verify(Map<String, String> params) {
        return VnpaySigner.verifyPayParams(params, resolveSecret());
    }

    @Override
    public QueryDrResult queryDr(QueryDrRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String payDate = VNP_TIME_FMT.format(clock.instant().atZone(GMT_PLUS_7));
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("vnp_ResponseId", request.requestId());
        raw.put("vnp_Command", "querydr");
        raw.put("vnp_ResponseCode", "00");
        raw.put("vnp_Message", "Mock QueryDr Success");
        raw.put("vnp_TmnCode", resolveTmnCode());
        raw.put("vnp_TxnRef", request.txnRef());
        raw.put("vnp_TransactionStatus", "01");
        raw.put("vnp_PayDate", payDate);
        raw.put("vnp_OrderInfo", request.orderInfo());
        return new QueryDrResult(
                true,
                request.requestId(),
                "00",
                "Mock QueryDr Success",
                request.txnRef(),
                null,
                "NCB",
                payDate,
                request.transactionNo(),
                "01",
                "01",
                request.orderInfo(),
                "",
                "",
                raw);
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String payDate = VNP_TIME_FMT.format(clock.instant().atZone(GMT_PLUS_7));
        String mockTxnNo = StringUtils.hasText(request.transactionNo()) ? request.transactionNo() : "90000001";
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("vnp_ResponseId", request.requestId());
        raw.put("vnp_Command", "refund");
        raw.put("vnp_ResponseCode", "00");
        raw.put("vnp_Message", "Mock Refund Success");
        raw.put("vnp_TmnCode", resolveTmnCode());
        raw.put("vnp_TxnRef", request.txnRef());
        raw.put("vnp_Amount", Long.toString(Math.multiplyExact(request.amountVnd(), 100L)));
        raw.put("vnp_BankCode", "NCB");
        raw.put("vnp_PayDate", payDate);
        raw.put("vnp_TransactionNo", mockTxnNo);
        raw.put("vnp_TransactionType", request.transactionType());
        raw.put("vnp_TransactionStatus", "05");
        raw.put("vnp_OrderInfo", request.orderInfo());
        return new RefundResult(
                true,
                request.requestId(),
                "00",
                "Mock Refund Success",
                request.txnRef(),
                request.amountVnd(),
                "NCB",
                payDate,
                mockTxnNo,
                request.transactionType(),
                "05",
                request.orderInfo(),
                raw);
    }

    /** Ký bộ tham số bằng khóa giả lập để trang {@code /mock-vnpay} tạo IPN và URL quay lại hợp lệ. */
    public String signParams(Map<String, String> params) {
        return VnpaySigner.signPayParams(params, resolveSecret());
    }

    public String resolveTmnCode() {
        if (StringUtils.hasText(properties.mockTmnCode())) {
            return properties.mockTmnCode().trim();
        }
        return DEFAULT_MOCK_TMN_CODE;
    }

    public String resolveSecret() {
        if (StringUtils.hasText(properties.mockSecret())) {
            return properties.mockSecret().trim();
        }
        return DEFAULT_MOCK_SECRET;
    }

    static String appendQuery(String baseUrl, Map<String, String> params, String secureHash) {
        String signData = VnpaySigner.buildPaySignData(params);
        String separator = baseUrl.contains("?") ? "&" : "?";
        StringBuilder url = new StringBuilder(baseUrl);
        if (!signData.isEmpty()) {
            url.append(separator).append(signData).append('&');
        } else {
            url.append(separator);
        }
        url.append("vnp_SecureHash=").append(URLEncoder.encode(secureHash, StandardCharsets.UTF_8));
        return url.toString();
    }
}
