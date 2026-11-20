package vn.unicom.thi360.commerce.payment.gateway;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.HtmlUtils;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.security.ClientIpResolver;

/**
 * Trang cổng thanh toán giả lập {@code /mock-vnpay} (TM 8.4, plan CM-3a AC-17, AC-18).
 * Dùng hoàn toàn liên kết {@code GET} (không có {@code POST} form) để không vướng {@code CsrfFilter} khi
 * cùng host có cookie đăng nhập; không xuất hiện trong OpenAPI ({@link Hidden}).
 */
@Controller
@Hidden
@Conditional(MockVnpayPageCondition.class)
public class MockVnpayController {

    private static final ZoneId GMT_PLUS_7 = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNP_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final AtomicLong MOCK_TXN_SEQ = new AtomicLong(1000L);

    private final MockVnpayGateway mockGateway;
    private final ObjectProvider<VnpayIpnProcessor> ipnProcessorProvider;
    private final ClientIpResolver clientIpResolver;
    private final String webUrl;
    private final Clock clock;

    public MockVnpayController(
            MockVnpayGateway mockGateway,
            ObjectProvider<VnpayIpnProcessor> ipnProcessorProvider,
            ClientIpResolver clientIpResolver,
            @Value("${thi360.web-url:http://localhost:3000}") String webUrl,
            Clock clock) {
        this.mockGateway = mockGateway;
        this.ipnProcessorProvider = ipnProcessorProvider;
        this.clientIpResolver = clientIpResolver;
        this.webUrl = webUrl;
        this.clock = clock;
    }

    @GetMapping(value = "/mock-vnpay", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> renderMockPage(@RequestParam Map<String, String> params) {
        Map<String, String> vnpParams = extractVnpParams(params);
        if (!mockGateway.verify(vnpParams)) {
            throw new BusinessRuleException("VNPAY_SIGNATURE_INVALID");
        }
        validateReturnUrl(vnpParams.get("vnp_ReturnUrl"));

        String txnRef = vnpParams.getOrDefault("vnp_TxnRef", "");
        String orderInfo = vnpParams.getOrDefault("vnp_OrderInfo", "");
        String amountText = formatAmountVnd(vnpParams.get("vnp_Amount"));

        String successHref = buildActionUrl("success", vnpParams);
        String failedHref = buildActionUrl("failed", vnpParams);
        String noResponseHref = buildActionUrl("no_response", vnpParams);

        Map<String, String> successIpn = buildCallbackParams(
                vnpParams, "00", "00", "NCB", "ATM", nextMockTransactionNo(), true);
        Map<String, String> failedIpn = buildCallbackParams(vnpParams, "24", "02", "VNPAY", "QRCODE", "0", false);
        String successIpnUrl = MockVnpayGateway.appendQuery(
                "/api/v1/public/payments/vnpay/ipn", successIpn, mockGateway.signParams(successIpn));
        String failedIpnUrl = MockVnpayGateway.appendQuery(
                "/api/v1/public/payments/vnpay/ipn", failedIpn, mockGateway.signParams(failedIpn));

        String html = """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                  <meta charset="UTF-8">
                  <title>Mock VNPAY Gateway</title>
                </head>
                <body>
                  <main>
                    <h1>Mock VNPAY Gateway</h1>
                    <p>Mã giao dịch: <strong data-mock-txn-ref="%s">%s</strong></p>
                    <p>Nội dung: <span>%s</span></p>
                    <p>Số tiền: <strong>%s VND</strong></p>
                    <div>
                      <a href="%s" data-mock-action="success" data-ipn-url="%s">Thành công</a>
                      <a href="%s" data-mock-action="failed" data-ipn-url="%s">Thất bại</a>
                      <a href="%s" data-mock-action="no_response">Không trả lời</a>
                    </div>
                  </main>
                </body>
                </html>
                """.formatted(
                HtmlUtils.htmlEscape(txnRef),
                HtmlUtils.htmlEscape(txnRef),
                HtmlUtils.htmlEscape(orderInfo),
                HtmlUtils.htmlEscape(amountText),
                HtmlUtils.htmlEscape(successHref),
                HtmlUtils.htmlEscape(successIpnUrl),
                HtmlUtils.htmlEscape(failedHref),
                HtmlUtils.htmlEscape(failedIpnUrl),
                HtmlUtils.htmlEscape(noResponseHref));

        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(html);
    }

    @GetMapping("/mock-vnpay/action")
    public ResponseEntity<Void> executeMockAction(
            @RequestParam(required = false) String outcome,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        Map<String, String> vnpParams = extractVnpParams(params);
        if (!mockGateway.verify(vnpParams)) {
            throw new BusinessRuleException("VNPAY_SIGNATURE_INVALID");
        }
        String returnUrl = validateReturnUrl(vnpParams.get("vnp_ReturnUrl"));
        String normalizedOutcome = outcome == null ? "" : outcome.trim().toLowerCase();

        Map<String, String> returnParams;
        switch (normalizedOutcome) {
            case "success" -> {
                String txnNo = nextMockTransactionNo();
                Map<String, String> ipnParams = buildCallbackParams(
                        vnpParams, "00", "00", "NCB", "ATM", txnNo, true);
                signAndDispatchIpn(ipnParams, request);
                returnParams = buildCallbackParams(vnpParams, "00", "00", "NCB", "ATM", txnNo, true);
            }
            case "failed" -> {
                Map<String, String> ipnParams = buildCallbackParams(
                        vnpParams, "24", "02", "VNPAY", "QRCODE", "0", false);
                signAndDispatchIpn(ipnParams, request);
                returnParams = buildCallbackParams(vnpParams, "24", "02", "VNPAY", "QRCODE", "0", false);
            }
            case "no_response" -> {
                // Bỏ qua IPN: thanh toán giữ CREATED; chuyển hướng về vnp_ReturnUrl để mô phỏng khách đóng trình duyệt/timeout.
                returnParams = buildCallbackParams(vnpParams, "24", "01", "VNPAY", "QRCODE", "0", false);
            }
            default -> throw invalid("outcome", "INVALID_OUTCOME", "Unsupported mock VNPAY outcome");
        }

        String redirectTarget = MockVnpayGateway.appendQuery(
                returnUrl, returnParams, mockGateway.signParams(returnParams));
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirectTarget)
                .build();
    }

    private void signAndDispatchIpn(Map<String, String> ipnParams, HttpServletRequest request) {
        Map<String, String> signed = new TreeMap<>(ipnParams);
        signed.put("vnp_SecureHash", mockGateway.signParams(signed));
        VnpayIpnProcessor processor = ipnProcessorProvider.getIfAvailable();
        if (processor != null) {
            processor.process(signed, clientIpResolver.resolve(request));
        }
    }

    private Map<String, String> buildCallbackParams(
            Map<String, String> requestParams,
            String responseCode,
            String transactionStatus,
            String bankCode,
            String cardType,
            String transactionNo,
            boolean includeBankTranNo) {
        Map<String, String> result = new TreeMap<>();
        result.put("vnp_Amount", requestParams.getOrDefault("vnp_Amount", "0"));
        result.put("vnp_BankCode", bankCode);
        if (includeBankTranNo) {
            result.put("vnp_BankTranNo", "VNP" + transactionNo);
        }
        result.put("vnp_CardType", cardType);
        result.put("vnp_OrderInfo", requestParams.getOrDefault("vnp_OrderInfo", ""));
        result.put("vnp_PayDate", VNP_TIME_FMT.format(clock.instant().atZone(GMT_PLUS_7)));
        result.put("vnp_ResponseCode", responseCode);
        result.put("vnp_TmnCode", requestParams.getOrDefault("vnp_TmnCode", mockGateway.resolveTmnCode()));
        result.put("vnp_TransactionNo", transactionNo);
        result.put("vnp_TransactionStatus", transactionStatus);
        result.put("vnp_TxnRef", requestParams.getOrDefault("vnp_TxnRef", ""));
        return result;
    }

    private String nextMockTransactionNo() {
        long millisTail = clock.millis() % 1_000_000_000L;
        long seq = MOCK_TXN_SEQ.incrementAndGet() % 1000L;
        return String.format("%d%03d", 900000000L + millisTail, seq);
    }

    private static Map<String, String> extractVnpParams(Map<String, String> params) {
        Map<String, String> vnp = new TreeMap<>();
        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (entry.getKey() != null && entry.getKey().startsWith("vnp_")) {
                    vnp.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return vnp;
    }

    private static String buildActionUrl(String outcome, Map<String, String> vnpParams) {
        StringBuilder sb = new StringBuilder("/mock-vnpay/action?outcome=")
                .append(URLEncoder.encode(outcome, StandardCharsets.UTF_8));
        for (Map.Entry<String, String> entry : vnpParams.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            sb.append('&')
                    .append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private String validateReturnUrl(String rawReturnUrl) {
        if (!StringUtils.hasText(rawReturnUrl)) {
            throw invalid("vnp_ReturnUrl", "REQUIRED", "vnp_ReturnUrl is required");
        }
        String candidate = rawReturnUrl.trim();
        if (candidate.startsWith("/") && !candidate.startsWith("//") && !candidate.contains("\\")) {
            try {
                URI rel = URI.create(candidate);
                if (rel.getRawFragment() != null) {
                    throw invalid("vnp_ReturnUrl", "INVALID", "Fragment is not allowed in vnp_ReturnUrl");
                }
                String decodedPath = rel.getPath();
                String normalizedPath = rel.normalize().getPath();
                if (decodedPath == null
                        || normalizedPath == null
                        || decodedPath.contains("..")
                        || normalizedPath.contains("..")
                        || !(normalizedPath.equals("/checkout/result") || normalizedPath.startsWith("/checkout/result/"))) {
                    throw invalid("vnp_ReturnUrl", "INVALID", "Relative vnp_ReturnUrl must target /checkout/result");
                }
                return candidate;
            } catch (IllegalArgumentException ex) {
                throw invalid("vnp_ReturnUrl", "INVALID", "Invalid vnp_ReturnUrl");
            }
        }
        URI candidateUri;
        URI allowedBase;
        try {
            candidateUri = URI.create(candidate);
            allowedBase = URI.create(webUrl.trim());
        } catch (IllegalArgumentException ex) {
            throw invalid("vnp_ReturnUrl", "INVALID", "Malformed vnp_ReturnUrl");
        }
        if (candidateUri.getRawFragment() != null) {
            throw invalid("vnp_ReturnUrl", "INVALID", "Fragment is not allowed in vnp_ReturnUrl");
        }
        if (candidateUri.getScheme() == null || candidateUri.getHost() == null) {
            throw invalid("vnp_ReturnUrl", "INVALID", "vnp_ReturnUrl must include scheme and host");
        }
        if (!candidateUri.getScheme().equalsIgnoreCase(allowedBase.getScheme())
                || !candidateUri.getHost().equalsIgnoreCase(allowedBase.getHost())
                || effectivePort(candidateUri) != effectivePort(allowedBase)) {
            throw invalid("vnp_ReturnUrl", "INVALID", "vnp_ReturnUrl origin is not allowed");
        }
        return candidate;
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() > 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static String formatAmountVnd(String rawVnpAmount) {
        if (!StringUtils.hasText(rawVnpAmount)) {
            return "0";
        }
        try {
            long scaled = Long.parseLong(rawVnpAmount.trim());
            return Long.toString(scaled / 100L);
        } catch (NumberFormatException ex) {
            return rawVnpAmount;
        }
    }

    private static ValidationException invalid(String field, String code, String message) {
        return new ValidationException("VALIDATION_FAILED", List.of(new FieldError(field, code, message)));
    }
}
