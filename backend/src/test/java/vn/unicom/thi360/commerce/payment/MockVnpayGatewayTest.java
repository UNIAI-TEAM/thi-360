package vn.unicom.thi360.commerce.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.PayRequest;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.QueryDrRequest;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.QueryDrResult;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.RefundRequest;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.RefundResult;
import vn.unicom.thi360.commerce.payment.gateway.VnpayProperties;
import vn.unicom.thi360.commerce.payment.gateway.VnpaySigner;

/**
 * AC-17: Kiểm tra {@link VnpaySigner} khớp {@code vnpay/vectors.json} và {@link MockVnpayGateway} đủ 4 hàm.
 */
class MockVnpayGatewayTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void signerMatchesPayVectorsExactly() throws IOException {
        JsonNode root = readVectors();
        String secret = root.path("test_key").asText();

        for (JsonNode item : root.path("pay")) {
            String name = item.path("name").asText();
            if (item.has("request")) {
                assertPayVector(name + " (request)", item.path("request"), secret);
            }
            if (item.has("return")) {
                assertPayVector(name + " (return)", item.path("return"), secret);
            }
        }
        for (JsonNode item : root.path("querydr")) {
            assertPipeVector(item.path("request"), secret);
            assertPipeVector(item.path("response"), secret);
        }
        for (JsonNode item : root.path("refund")) {
            assertPipeVector(item.path("request"), secret);
            assertPipeVector(item.path("response"), secret);
        }
    }

    @Test
    void mockGatewayImplementsAllFourMethods() {
        VnpayProperties props = new VnpayProperties(
                "MOCK", null, null, null, null, List.of(),
                "dev-only-mock-secret", "DEV-MOCK", null);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneOffset.UTC);
        MockVnpayGateway gateway = new MockVnpayGateway(props, fixedClock);

        String payUrl = gateway.pay(new PayRequest(
                "DH261121AB12CD01",
                "DH261121AB12CD",
                125_000L,
                "Thanh toan don DH261121AB12CD",
                "vn",
                "http://localhost:3000/checkout/result",
                "127.0.0.1",
                "20261121100000",
                "20261121101500"));

        assertThat(payUrl).startsWith("/mock-vnpay?");
        Map<String, String> parsed = parseQueryParams(payUrl);
        assertThat(parsed)
                .containsEntry("vnp_Version", "2.1.0")
                .containsEntry("vnp_Command", "pay")
                .containsEntry("vnp_TmnCode", "DEV-MOCK")
                .containsEntry("vnp_Amount", "12500000")
                .containsEntry("vnp_CurrCode", "VND")
                .containsEntry("vnp_TxnRef", "DH261121AB12CD01")
                .containsEntry("vnp_OrderInfo", "Thanh toan don DH261121AB12CD")
                .containsEntry("vnp_OrderType", "other")
                .containsEntry("vnp_Locale", "vn")
                .containsEntry("vnp_ReturnUrl", "http://localhost:3000/checkout/result")
                .containsEntry("vnp_IpAddr", "127.0.0.1")
                .containsEntry("vnp_CreateDate", "20261121100000")
                .containsEntry("vnp_ExpireDate", "20261121101500")
                .doesNotContainKey("vnp_SecureHashType");

        assertThat(gateway.verify(parsed)).isTrue();
        Map<String, String> tampered = new LinkedHashMap<>(parsed);
        tampered.put("vnp_Amount", "100");
        assertThat(gateway.verify(tampered)).isFalse();

        QueryDrResult queryResult = gateway.queryDr(new QueryDrRequest(
                "req123", "DH261121AB12CD01", "20261121100000", "20261121102000",
                "127.0.0.1", "Check DH261121AB12CD", null));
        assertThat(queryResult.signatureValid()).isTrue();
        assertThat(queryResult.responseCode()).isEqualTo("00");
        assertThat(queryResult.txnRef()).isEqualTo("DH261121AB12CD01");

        RefundResult refundResult = gateway.refund(new RefundRequest(
                "req456", "02", "DH261121AB12CD01", 125_000L, "90000001",
                "20261121100000", "admin.ops", "20261121110000", "127.0.0.1", "Hoan tien don"));
        assertThat(refundResult.signatureValid()).isTrue();
        assertThat(refundResult.responseCode()).isEqualTo("00");
        assertThat(refundResult.amountVnd()).isEqualTo(125_000L);
    }

    private static void assertPayVector(String label, JsonNode vector, String secret) {
        Map<String, String> params = new LinkedHashMap<>();
        vector.path("params").fields().forEachRemaining(e -> params.put(e.getKey(), e.getValue().asText()));
        String expectedSignData = vector.path("sign_data").asText();
        String expectedHash = vector.path("secure_hash").asText();

        assertThat(VnpaySigner.buildPaySignData(params)).as(label + " sign_data").isEqualTo(expectedSignData);
        assertThat(VnpaySigner.signPayParams(params, secret)).as(label + " secure_hash").isEqualTo(expectedHash);

        Map<String, String> withHash = new LinkedHashMap<>(params);
        withHash.put("vnp_SecureHash", expectedHash);
        assertThat(VnpaySigner.verifyPayParams(withHash, secret)).as(label + " verify lowercase").isTrue();
        withHash.put("vnp_SecureHash", expectedHash.toUpperCase(Locale.ROOT));
        assertThat(VnpaySigner.verifyPayParams(withHash, secret)).as(label + " verify uppercase").isTrue();
    }

    private static void assertPipeVector(JsonNode vector, String secret) {
        String signData = vector.path("sign_data").asText();
        String expectedHash = vector.path("secure_hash").asText();
        assertThat(VnpaySigner.hmacSha512Hex(signData, secret)).isEqualTo(expectedHash);
    }

    private static JsonNode readVectors() throws IOException {
        try (InputStream in = new ClassPathResource("vnpay/vectors.json").getInputStream()) {
            return MAPPER.readTree(in);
        }
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
