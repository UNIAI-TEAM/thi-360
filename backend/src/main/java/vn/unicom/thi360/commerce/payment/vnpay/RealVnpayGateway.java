package vn.unicom.thi360.commerce.payment.vnpay;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayProperties;

/**
 * Bản cài đặt thật {@link VnpayGateway} cho chế độ {@code SANDBOX} và {@code PRODUCTION} (TM 8.4).
 * Hỗ trợ dựng URL {@code pay}, kiểm {@code verify}, gọi POST JSON {@code querydr} và {@code refund}.
 */
@Component
@Conditional(VnpayRealModeCondition.class)
public class RealVnpayGateway implements VnpayGateway {

    private static final Logger log = LoggerFactory.getLogger(RealVnpayGateway.class);
    private static final ZoneId GMT_PLUS_7 = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNP_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Pattern FOURTEEN_DIGITS = Pattern.compile("\\d{14}");
    private static final long MAX_SCALED_AMOUNT = 999_999_999_999L;
    private static final int MAX_ORDER_INFO_LENGTH = 255;
    private static final int MAX_REQUEST_ID_LENGTH = 32;
    private static final String REDACTED = "[REDACTED]";
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final VnpayProperties properties;
    private final VnpayClientProperties clientProperties;
    private final VnpayRealSigner signer;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final HttpClient httpClient;

    @Autowired
    public RealVnpayGateway(
            VnpayProperties properties,
            VnpayClientProperties clientProperties,
            VnpayRealSigner signer,
            ObjectMapper objectMapper,
            Clock clock) {
        this(
                properties,
                clientProperties,
                signer,
                objectMapper,
                clock,
                HttpClient.newBuilder()
                        .connectTimeout(Objects.requireNonNull(clientProperties, "clientProperties must not be null")
                                .connectTimeout())
                        .build());
    }

    public RealVnpayGateway(
            VnpayProperties properties,
            VnpayClientProperties clientProperties,
            VnpayRealSigner signer,
            ObjectMapper objectMapper,
            Clock clock,
            HttpClient httpClient) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clientProperties = Objects.requireNonNull(clientProperties, "clientProperties must not be null");
        this.signer = Objects.requireNonNull(signer, "signer must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    @Override
    public String pay(PayRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        long scaledAmount = scaleAndValidateAmount(request.amountVnd());
        String txnRef = requireNonBlankText(request.txnRef(), "txnRef must not be blank");
        String returnUrl = requireNonBlankText(request.returnUrl(), "returnUrl must not be blank");
        String payUrl = requirePayUrl();
        String tmnCode = requireTmnCode();
        String secret = requireHashSecret();

        ZonedDateTime nowGmt7 = clock.instant().atZone(GMT_PLUS_7);
        String createDate = resolveOrValidateTimestamp(request.createDate(), nowGmt7, "createDate");
        String expireDate = resolveOrValidateTimestamp(request.expireDate(), nowGmt7.plusMinutes(15), "expireDate");
        String orderInfo = resolveOrderInfo(
                request.orderInfo(),
                "Thanh toan don " + (StringUtils.hasText(request.orderCode()) ? request.orderCode().trim() : txnRef));
        String locale = "en".equalsIgnoreCase(trimToEmpty(request.locale())) ? "en" : "vn";
        String ipAddr = StringUtils.hasText(request.ipAddr()) ? request.ipAddr().trim() : "127.0.0.1";

        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", Long.toString(scaledAmount));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", orderInfo);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", locale);
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", ipAddr);
        params.put("vnp_CreateDate", createDate);
        params.put("vnp_ExpireDate", expireDate);

        String secureHash = signer.signPay(params, secret);
        String queryData = signer.buildPaySignData(params);
        String separator = resolveQuerySeparator(payUrl);
        return payUrl + separator + queryData + "&vnp_SecureHash=" + secureHash;
    }

    @Override
    public boolean verify(Map<String, String> params) {
        return signer.verifyPay(params, requireHashSecret());
    }

    @Override
    public QueryDrResult queryDr(QueryDrRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String txnRef = requireNonBlankText(request.txnRef(), "txnRef must not be blank");
        String transactionDate = validateTimestamp14(
                requireNonBlankText(request.transactionDate(), "transactionDate must not be blank"),
                "transactionDate");
        String requestId = resolveRequestId(request.requestId());
        String createDate = resolveOrValidateTimestamp(
                request.createDate(), clock.instant().atZone(GMT_PLUS_7), "createDate");
        String ipAddr = StringUtils.hasText(request.ipAddr()) ? request.ipAddr().trim() : clientProperties.serverIp();
        String orderInfo = resolveOrderInfo(request.orderInfo(), "Kiem tra ket qua GD " + txnRef);
        String tmnCode = requireTmnCode();
        String secret = requireHashSecret();

        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("vnp_RequestId", requestId);
        payload.put("vnp_Version", "2.1.0");
        payload.put("vnp_Command", "querydr");
        payload.put("vnp_TmnCode", tmnCode);
        payload.put("vnp_TxnRef", txnRef);
        payload.put("vnp_OrderInfo", orderInfo);
        payload.put("vnp_TransactionDate", transactionDate);
        payload.put("vnp_CreateDate", createDate);
        payload.put("vnp_IpAddr", ipAddr);
        if (StringUtils.hasText(request.transactionNo())) {
            payload.put("vnp_TransactionNo", request.transactionNo().trim());
        }
        payload.put("vnp_SecureHash", signer.signQueryDrRequest(payload, secret));

        log.debug("Calling VNPAY querydr for txnRef={} requestId={}", txnRef, requestId);
        Map<String, Object> parsed = postJsonCommand("querydr", payload);
        boolean signatureValid = signer.verifyQueryDrResponse(parsed, secret);
        Map<String, Object> sanitizedRaw = sanitizeRawMap(parsed, secret);

        if (!signatureValid) {
            log.warn("VNPAY querydr response failed checksum verification for txnRef={} requestId={}",
                    txnRef, requestId);
            return new QueryDrResult(
                    false, null, null, null, null, null, null, null, null, null, null, null, null, null, sanitizedRaw);
        }

        return new QueryDrResult(
                true,
                asTrimmedString(parsed.get("vnp_ResponseId")),
                asTrimmedString(parsed.get("vnp_ResponseCode")),
                asTrimmedString(parsed.get("vnp_Message")),
                asTrimmedString(parsed.get("vnp_TxnRef")),
                parseScaledAmountVnd(parsed.get("vnp_Amount")),
                asTrimmedString(parsed.get("vnp_BankCode")),
                asTrimmedString(parsed.get("vnp_PayDate")),
                asTrimmedString(parsed.get("vnp_TransactionNo")),
                asTrimmedString(parsed.get("vnp_TransactionType")),
                asTrimmedString(parsed.get("vnp_TransactionStatus")),
                asTrimmedString(parsed.get("vnp_OrderInfo")),
                asTrimmedString(parsed.get("vnp_PromotionCode")),
                asTrimmedString(parsed.get("vnp_PromotionAmount")),
                sanitizedRaw);
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        long scaledAmount = scaleAndValidateAmount(request.amountVnd());
        String transactionType = requireNonBlankText(
                request.transactionType(), "transactionType must not be blank");
        if (!"02".equals(transactionType) && !"03".equals(transactionType)) {
            throw new IllegalArgumentException("vnp_TransactionType must be '02' or '03'");
        }
        String txnRef = requireNonBlankText(request.txnRef(), "txnRef must not be blank");
        String transactionDate = validateTimestamp14(
                requireNonBlankText(request.transactionDate(), "transactionDate must not be blank"),
                "transactionDate");
        String createBy = requireNonBlankText(request.createBy(), "createBy must not be blank");
        String requestId = resolveRequestId(request.requestId());
        String createDate = resolveOrValidateTimestamp(
                request.createDate(), clock.instant().atZone(GMT_PLUS_7), "createDate");
        String ipAddr = StringUtils.hasText(request.ipAddr()) ? request.ipAddr().trim() : clientProperties.serverIp();
        String orderInfo = resolveOrderInfo(request.orderInfo(), "Hoan tien giao dich " + txnRef);
        String transactionNo = request.transactionNo() == null ? "" : request.transactionNo().trim();
        String tmnCode = requireTmnCode();
        String secret = requireHashSecret();

        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("vnp_RequestId", requestId);
        payload.put("vnp_Version", "2.1.0");
        payload.put("vnp_Command", "refund");
        payload.put("vnp_TmnCode", tmnCode);
        payload.put("vnp_TransactionType", transactionType);
        payload.put("vnp_TxnRef", txnRef);
        payload.put("vnp_Amount", Long.toString(scaledAmount));
        payload.put("vnp_TransactionNo", transactionNo);
        payload.put("vnp_TransactionDate", transactionDate);
        payload.put("vnp_CreateBy", createBy);
        payload.put("vnp_CreateDate", createDate);
        payload.put("vnp_IpAddr", ipAddr);
        payload.put("vnp_OrderInfo", orderInfo);
        payload.put("vnp_SecureHash", signer.signRefundRequest(payload, secret));

        log.debug("Calling VNPAY refund for txnRef={} requestId={} type={}", txnRef, requestId, transactionType);
        Map<String, Object> parsed = postJsonCommand("refund", payload);
        boolean signatureValid = signer.verifyRefundResponse(parsed, secret);
        Map<String, Object> sanitizedRaw = sanitizeRawMap(parsed, secret);

        if (!signatureValid) {
            log.warn("VNPAY refund response failed checksum verification for txnRef={} requestId={}",
                    txnRef, requestId);
            return new RefundResult(
                    false, null, null, null, null, null, null, null, null, null, null, null, sanitizedRaw);
        }

        return new RefundResult(
                true,
                asTrimmedString(parsed.get("vnp_ResponseId")),
                asTrimmedString(parsed.get("vnp_ResponseCode")),
                asTrimmedString(parsed.get("vnp_Message")),
                asTrimmedString(parsed.get("vnp_TxnRef")),
                parseScaledAmountVnd(parsed.get("vnp_Amount")),
                asTrimmedString(parsed.get("vnp_BankCode")),
                asTrimmedString(parsed.get("vnp_PayDate")),
                asTrimmedString(parsed.get("vnp_TransactionNo")),
                asTrimmedString(parsed.get("vnp_TransactionType")),
                asTrimmedString(parsed.get("vnp_TransactionStatus")),
                asTrimmedString(parsed.get("vnp_OrderInfo")),
                sanitizedRaw);
    }

    private Map<String, Object> postJsonCommand(String command, Map<String, String> payload) {
        URI apiUri = URI.create(requireApiUrl());
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(payload);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to serialize VNPAY " + command + " request payload", ex);
        }

        HttpRequest httpRequest = HttpRequest.newBuilder(apiUri)
                .timeout(clientProperties.readTimeout())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException ex) {
            throw new VnpayTransportException(true, null, "VNPAY " + command + " timed out", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new VnpayTransportException(false, null, "VNPAY " + command + " interrupted", ex);
        } catch (IOException ex) {
            throw new VnpayTransportException(false, null, "VNPAY " + command + " network error", ex);
        }

        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new VnpayTransportException(false, statusCode, "VNPAY " + command + " HTTP " + statusCode);
        }

        String rawBody = response.body();
        if (!StringUtils.hasText(rawBody)) {
            throw new VnpayTransportException(false, statusCode, "VNPAY " + command + " empty response");
        }

        try {
            JsonNode tree = objectMapper.readTree(rawBody);
            if (tree == null || !tree.isObject()) {
                throw new VnpayTransportException(false, statusCode, "VNPAY " + command + " non-JSON response");
            }
            return objectMapper.convertValue(tree, MAP_TYPE);
        } catch (IOException | IllegalArgumentException ex) {
            throw new VnpayTransportException(false, statusCode, "VNPAY " + command + " non-JSON response");
        }
    }

    private static long scaleAndValidateAmount(long amountVnd) {
        if (amountVnd <= 0) {
            throw new IllegalArgumentException("amountVnd must be positive");
        }
        long scaledAmount;
        try {
            scaledAmount = Math.multiplyExact(amountVnd, 100L);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("vnp_Amount overflow", ex);
        }
        if (scaledAmount > MAX_SCALED_AMOUNT) {
            throw new IllegalArgumentException("vnp_Amount exceeds 12 digits");
        }
        return scaledAmount;
    }

    private static String resolveOrValidateTimestamp(String raw, ZonedDateTime fallback, String fieldName) {
        if (!StringUtils.hasText(raw)) {
            return VNP_TIME_FMT.format(fallback);
        }
        return validateTimestamp14(raw.trim(), fieldName);
    }

    private static String validateTimestamp14(String value, String fieldName) {
        if (!FOURTEEN_DIGITS.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " must match yyyyMMddHHmmss (14 digits)");
        }
        return value;
    }

    private static String resolveOrderInfo(String rawOrderInfo, String fallback) {
        String resolved = StringUtils.hasText(rawOrderInfo) ? rawOrderInfo.trim() : fallback;
        if (!StringUtils.hasText(resolved)) {
            throw new IllegalArgumentException("vnp_OrderInfo must not be blank");
        }
        if (resolved.length() > MAX_ORDER_INFO_LENGTH) {
            throw new IllegalArgumentException("vnp_OrderInfo exceeds 255 characters");
        }
        return resolved;
    }

    private static String resolveRequestId(String rawRequestId) {
        if (!StringUtils.hasText(rawRequestId)) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        String trimmed = rawRequestId.trim();
        if (trimmed.length() > MAX_REQUEST_ID_LENGTH) {
            throw new IllegalArgumentException("vnp_RequestId exceeds 32 characters");
        }
        return trimmed;
    }

    private static String resolveQuerySeparator(String payUrl) {
        if (!payUrl.contains("?")) {
            return "?";
        }
        return (payUrl.endsWith("?") || payUrl.endsWith("&")) ? "" : "&";
    }

    private static Long parseScaledAmountVnd(Object rawAmount) {
        String text = asTrimmedString(rawAmount);
        if (text == null) {
            return null;
        }
        try {
            long scaled = Long.parseLong(text);
            return (scaled > 0 && scaled % 100L == 0) ? (scaled / 100L) : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String asTrimmedString(Object value) {
        if (value == null) {
            return null;
        }
        String trimmed = String.valueOf(value).trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Map<String, Object> sanitizeRawMap(Map<?, ?> source, String secret) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String cleanKey = redactSecret(String.valueOf(entry.getKey()), secret);
            Object cleanValue = sanitizeValue(entry.getValue(), secret);
            if (cleanValue != null) {
                sanitized.put(cleanKey, cleanValue);
            }
        }
        return sanitized.isEmpty() ? Map.of() : Map.copyOf(sanitized);
    }

    private static Object sanitizeValue(Object value, String secret) {
        if (value == null) {
            return null;
        }
        if (value instanceof String str) {
            return redactSecret(str, secret);
        }
        if (value instanceof Map<?, ?> nestedMap) {
            return sanitizeRawMap(nestedMap, secret);
        }
        if (value instanceof List<?> nestedList) {
            List<Object> cleanList = new ArrayList<>(nestedList.size());
            for (Object item : nestedList) {
                Object cleanItem = sanitizeValue(item, secret);
                if (cleanItem != null) {
                    cleanList.add(cleanItem);
                }
            }
            return List.copyOf(cleanList);
        }
        return value;
    }

    private static String redactSecret(String text, String secret) {
        if (text == null || !StringUtils.hasText(secret)) {
            return text;
        }
        return text.contains(secret) ? text.replace(secret, REDACTED) : text;
    }

    private String requireTmnCode() {
        if (!StringUtils.hasText(properties.tmnCode())) {
            throw new IllegalStateException("THI360_VNPAY_TMN_CODE is not configured");
        }
        return properties.tmnCode().trim();
    }

    private String requireHashSecret() {
        if (!StringUtils.hasText(properties.hashSecret())) {
            throw new IllegalStateException("THI360_VNPAY_HASH_SECRET is not configured");
        }
        return properties.hashSecret().trim();
    }

    private String requirePayUrl() {
        if (!StringUtils.hasText(properties.payUrl())) {
            throw new IllegalStateException("THI360_VNPAY_PAY_URL is not configured");
        }
        return properties.payUrl().trim();
    }

    private String requireApiUrl() {
        if (!StringUtils.hasText(properties.apiUrl())) {
            throw new IllegalStateException("THI360_VNPAY_API_URL is not configured");
        }
        return properties.apiUrl().trim();
    }

    private static String requireNonBlankText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
