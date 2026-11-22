package vn.unicom.thi360.commerce.payment.vnpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.PayRequest;
import vn.unicom.thi360.commerce.payment.gateway.VnpayProperties;
import vn.unicom.thi360.commerce.payment.vnpay.VnpaySigningProperties.PayEncodingRule;

/**
 * AC-3: Kiểm tra URL {@code pay} đủ tham số TM 8.4, múi giờ GMT+7, {@code vnp_Amount = số tiền × 100},
 * không gửi {@code vnp_SecureHashType}, và từ chối số tiền quá 12 chữ số sau khi nhân 100 trước khi dựng URL.
 */
class VnpayPayUrlTest {

    private static final Instant FIXED_UTC = Instant.parse("2026-11-21T03:00:00Z");

    private final VnpayRealSigner signer = new VnpayRealSigner(
            new VnpaySigningProperties(null, null, null, null));

    private RealVnpayGateway createGateway(String payUrl) {
        VnpayProperties props = new VnpayProperties(
                "SANDBOX",
                "TEST-TMN",
                "TEST-HASH-SECRET-KEY-001",
                payUrl,
                "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction",
                List.of(),
                null,
                null,
                null);
        return new RealVnpayGateway(
                props,
                new VnpayClientProperties(null, null, null),
                signer,
                new ObjectMapper(),
                Clock.fixed(FIXED_UTC, ZoneOffset.UTC));
    }

    @Test
    void buildsValidPayUrlWithRequiredParamsAndGmtPlus7Timestamps() {
        RealVnpayGateway gateway = createGateway("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");

        String url = gateway.pay(new PayRequest(
                "DH261121AB12CD01",
                "DH261121AB12CD",
                5_000L,
                null,
                "vn",
                "https://thi360.example/checkout/result",
                "203.0.113.7",
                null,
                null));

        assertThat(url).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        Map<String, String> parsed = parseQueryParams(url);
        assertThat(parsed)
                .containsEntry("vnp_Version", "2.1.0")
                .containsEntry("vnp_Command", "pay")
                .containsEntry("vnp_TmnCode", "TEST-TMN")
                .containsEntry("vnp_Amount", "500000")
                .containsEntry("vnp_CurrCode", "VND")
                .containsEntry("vnp_TxnRef", "DH261121AB12CD01")
                .containsEntry("vnp_OrderInfo", "Thanh toan don DH261121AB12CD")
                .containsEntry("vnp_OrderType", "other")
                .containsEntry("vnp_Locale", "vn")
                .containsEntry("vnp_ReturnUrl", "https://thi360.example/checkout/result")
                .containsEntry("vnp_IpAddr", "203.0.113.7")
                .containsEntry("vnp_CreateDate", "20261121100000")
                .containsEntry("vnp_ExpireDate", "20261121101500")
                .containsKey("vnp_SecureHash")
                .doesNotContainKey("vnp_SecureHashType");

        assertThat(gateway.verify(parsed)).isTrue();

        Map<String, String> tampered = new LinkedHashMap<>(parsed);
        tampered.put("vnp_Amount", "100");
        assertThat(gateway.verify(tampered)).isFalse();
    }

    @Test
    void acceptsMaxTwelveDigitScaledAmountAndHandlesExistingQueryString() {
        RealVnpayGateway gateway = createGateway("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?env=test");

        String url = gateway.pay(new PayRequest(
                "DH261121MAX01",
                "DH261121MAX",
                9_999_999_999L,
                "Thanh toan don DH261121MAX",
                "EN",
                "https://thi360.example/checkout/result",
                null,
                "20261121100000",
                "20261121101500"));

        assertThat(url).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?env=test&");
        Map<String, String> parsed = parseQueryParams(url);
        assertThat(parsed)
                .containsEntry("env", "test")
                .containsEntry("vnp_Amount", "999999999900")
                .containsEntry("vnp_Locale", "en")
                .containsEntry("vnp_IpAddr", "127.0.0.1");

        Map<String, String> vnpOnly = new LinkedHashMap<>(parsed);
        vnpOnly.remove("env");
        assertThat(gateway.verify(vnpOnly)).isTrue();
    }

    @Test
    void rejectsNonPositiveAndAboveTwelveDigitScaledAmountsBeforeBuildingUrl() {
        RealVnpayGateway gateway = createGateway("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");

        for (long invalidAmount : new long[] {0L, -1L, 10_000_000_000L, Long.MAX_VALUE}) {
            assertThatThrownBy(() -> gateway.pay(new PayRequest(
                    "DH261121BAD01",
                    "DH261121BAD",
                    invalidAmount,
                    "Thanh toan don DH261121BAD",
                    "vn",
                    "https://thi360.example/checkout/result",
                    "127.0.0.1",
                    "20261121100000",
                    "20261121101500")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsBlankTxnRefBlankReturnUrlBadTimestampAndOversizedOrderInfo() {
        RealVnpayGateway gateway = createGateway("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");

        assertThatThrownBy(() -> gateway.pay(new PayRequest(
                " ", "ORD1", 5_000L, "Thanh toan", "vn", "https://thi360.example/return", "127.0.0.1", null, null)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> gateway.pay(new PayRequest(
                "TXN1", "ORD1", 5_000L, "Thanh toan", "vn", " ", "127.0.0.1", null, null)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> gateway.pay(new PayRequest(
                "TXN1", "ORD1", 5_000L, "a".repeat(256), "vn", "https://thi360.example/return", "127.0.0.1", null, null)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> gateway.pay(new PayRequest(
                "TXN1", "ORD1", 5_000L, "Thanh toan", "vn", "https://thi360.example/return", "127.0.0.1", "2026-11-21", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rawPayEncodingStillUrlEncodesRedirectQueryStringAndVerifiesRoundTrip() {
        VnpayRealSigner rawSigner = new VnpayRealSigner(
                new VnpaySigningProperties(PayEncodingRule.RAW, null, null, null));
        VnpayProperties props = new VnpayProperties(
                "SANDBOX",
                "TEST-TMN",
                "TEST-HASH-SECRET-KEY-001",
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction",
                List.of(),
                null,
                null,
                null);
        RealVnpayGateway rawGateway = new RealVnpayGateway(
                props,
                new VnpayClientProperties(null, null, null),
                rawSigner,
                new ObjectMapper(),
                Clock.fixed(FIXED_UTC, ZoneOffset.UTC));

        String orderInfo = "Thanh toan don A & B?x=1";
        String returnUrl = "https://thi360.example/checkout/result?from=vnpay&step=2";
        String url = rawGateway.pay(new PayRequest(
                "DH261121RAW01",
                "DH261121RAW",
                50_000L,
                orderInfo,
                "vn",
                returnUrl,
                "203.0.113.7",
                null,
                null));

        URI parsedUri = URI.create(url);
        assertThat(parsedUri.getRawQuery()).doesNotContain(" ");
        Map<String, String> decodedParams = parseQueryParams(url);
        assertThat(decodedParams)
                .containsEntry("vnp_OrderInfo", orderInfo)
                .containsEntry("vnp_ReturnUrl", returnUrl)
                .containsEntry("vnp_TxnRef", "DH261121RAW01")
                .containsEntry("vnp_Amount", "5000000");

        assertThat(rawGateway.verify(decodedParams)).isTrue();
        Map<String, String> tampered = new LinkedHashMap<>(decodedParams);
        tampered.put("vnp_Amount", "100");
        assertThat(rawGateway.verify(tampered)).isFalse();
    }

    @Test
    void payQueryStringFollowsConfiguredPayEncodingRule() {
        String orderInfo = "Thanh toan don A * B ~ 1";

        RealVnpayGateway rfcGateway = createGatewayWithEncoding(PayEncodingRule.RFC_3986);
        String rfcUrl = rfcGateway.pay(new PayRequest(
                "DH261121RFC01", "DH261121RFC", 50_000L, orderInfo, "vn",
                "https://thi360.example/checkout/result", "203.0.113.7", null, null));
        String rfcRawQuery = URI.create(rfcUrl).getRawQuery();
        assertThat(rfcRawQuery)
                .contains("vnp_OrderInfo=Thanh%20toan%20don%20A%20%2A%20B%20~%201")
                .doesNotContain("+")
                .doesNotContain("%7E");
        assertThat(rfcGateway.verify(parseQueryParams(rfcUrl))).isTrue();

        RealVnpayGateway pyGateway = createGatewayWithEncoding(PayEncodingRule.PYTHON_QUOTE_PLUS);
        String pyUrl = pyGateway.pay(new PayRequest(
                "DH261121PY01", "DH261121PY", 50_000L, orderInfo, "vn",
                "https://thi360.example/checkout/result", "203.0.113.7", null, null));
        String pyRawQuery = URI.create(pyUrl).getRawQuery();
        assertThat(pyRawQuery)
                .contains("vnp_OrderInfo=Thanh+toan+don+A+%2A+B+~+1")
                .doesNotContain("%20")
                .doesNotContain("%7E");
        assertThat(pyGateway.verify(parseQueryParams(pyUrl))).isTrue();

        RealVnpayGateway rawGateway = createGatewayWithEncoding(PayEncodingRule.RAW);
        String rawUrl = rawGateway.pay(new PayRequest(
                "DH261121RAW02", "DH261121RAW", 50_000L, orderInfo, "vn",
                "https://thi360.example/checkout/result", "203.0.113.7", null, null));
        String rawRawQuery = URI.create(rawUrl).getRawQuery();
        assertThat(rawRawQuery)
                .contains("vnp_OrderInfo=Thanh+toan+don+A+*+B+%7E+1")
                .doesNotContain("%20")
                .doesNotContain("%2A");
        assertThat(rawGateway.verify(parseQueryParams(rawUrl))).isTrue();
    }

    private RealVnpayGateway createGatewayWithEncoding(PayEncodingRule encodingRule) {
        VnpayRealSigner customSigner = new VnpayRealSigner(
                new VnpaySigningProperties(encodingRule, null, null, null));
        VnpayProperties props = new VnpayProperties(
                "SANDBOX",
                "TEST-TMN",
                "TEST-HASH-SECRET-KEY-001",
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction",
                List.of(),
                null,
                null,
                null);
        return new RealVnpayGateway(
                props,
                new VnpayClientProperties(null, null, null),
                customSigner,
                new ObjectMapper(),
                Clock.fixed(FIXED_UTC, ZoneOffset.UTC));
    }

    private static Map<String, String> parseQueryParams(String url) {
        String query = URI.create(url).getRawQuery();
        Map<String, String> map = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
            String v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
            map.put(k, v);
        }
        return map;
    }
}
