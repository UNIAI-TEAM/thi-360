package vn.unicom.thi360.commerce.payment.vnpay;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * AC-1: Kiểm tra {@link VnpayRealSigner} khớp toàn bộ vector {@code pay}, {@code querydr}, {@code refund}
 * (cả yêu cầu và phản hồi) trong {@code backend/src/test/resources/vnpay/vectors.json}.
 */
class VnpaySignatureVectorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final VnpayRealSigner signer = new VnpayRealSigner(
            new VnpaySigningProperties(null, null, null, null));

    @Test
    void matchesAllPayVectorsInVectorsJson() throws IOException {
        JsonNode root = readVectors();
        String secret = root.path("test_key").asText();
        JsonNode payVectors = root.path("pay");
        assertThat(payVectors.isArray() && !payVectors.isEmpty()).isTrue();

        for (JsonNode item : payVectors) {
            String name = item.path("name").asText();
            if (item.has("request")) {
                assertPayVector(name + " (request)", item.path("request"), secret);
            }
            if (item.has("return")) {
                assertPayVector(name + " (return)", item.path("return"), secret);
            }
        }
    }

    @Test
    void matchesAllQueryDrVectorsInVectorsJson() throws IOException {
        JsonNode root = readVectors();
        String secret = root.path("test_key").asText();
        JsonNode queryDrVectors = root.path("querydr");
        assertThat(queryDrVectors.isArray() && !queryDrVectors.isEmpty()).isTrue();

        for (JsonNode item : queryDrVectors) {
            String name = item.path("name").asText();
            JsonNode req = item.path("request");
            Map<String, String> reqParams = toStringMap(req.path("params"));
            assertThat(signer.buildQueryDrRequestSignData(reqParams))
                    .as(name + " querydr request sign_data")
                    .isEqualTo(req.path("sign_data").asText());
            assertThat(signer.signQueryDrRequest(reqParams, secret))
                    .as(name + " querydr request secure_hash")
                    .isEqualTo(req.path("secure_hash").asText());

            JsonNode resp = item.path("response");
            Map<String, Object> respParams = toObjectMap(resp.path("params"));
            String expectedHash = resp.path("secure_hash").asText();
            assertThat(signer.buildQueryDrResponseSignData(respParams))
                    .as(name + " querydr response sign_data")
                    .isEqualTo(resp.path("sign_data").asText());

            Map<String, Object> withHash = new LinkedHashMap<>(respParams);
            assertThat(signer.verifyQueryDrResponse(withHash, secret)).isFalse();
            withHash.put("vnp_SecureHash", expectedHash);
            assertThat(signer.verifyQueryDrResponse(withHash, secret)).isTrue();
            withHash.put("vnp_SecureHash", expectedHash.toUpperCase(Locale.ROOT));
            assertThat(signer.verifyQueryDrResponse(withHash, secret)).isTrue();

            Map<String, Object> tamperedAmount = new LinkedHashMap<>(withHash);
            tamperedAmount.put("vnp_Amount", "9999999");
            assertThat(signer.verifyQueryDrResponse(tamperedAmount, secret)).isFalse();
        }
    }

    @Test
    void matchesAllRefundVectorsInVectorsJson() throws IOException {
        JsonNode root = readVectors();
        String secret = root.path("test_key").asText();
        JsonNode refundVectors = root.path("refund");
        assertThat(refundVectors.isArray() && !refundVectors.isEmpty()).isTrue();

        for (JsonNode item : refundVectors) {
            String name = item.path("name").asText();
            JsonNode req = item.path("request");
            Map<String, String> reqParams = toStringMap(req.path("params"));
            assertThat(signer.buildRefundRequestSignData(reqParams))
                    .as(name + " refund request sign_data")
                    .isEqualTo(req.path("sign_data").asText());
            assertThat(signer.signRefundRequest(reqParams, secret))
                    .as(name + " refund request secure_hash")
                    .isEqualTo(req.path("secure_hash").asText());

            JsonNode resp = item.path("response");
            Map<String, Object> respParams = toObjectMap(resp.path("params"));
            String expectedHash = resp.path("secure_hash").asText();
            assertThat(signer.buildRefundResponseSignData(respParams))
                    .as(name + " refund response sign_data")
                    .isEqualTo(resp.path("sign_data").asText());

            Map<String, Object> withHash = new LinkedHashMap<>(respParams);
            assertThat(signer.verifyRefundResponse(withHash, secret)).isFalse();
            withHash.put("vnp_SecureHash", expectedHash);
            assertThat(signer.verifyRefundResponse(withHash, secret)).isTrue();
            withHash.put("vnp_SecureHash", expectedHash.toUpperCase(Locale.ROOT));
            assertThat(signer.verifyRefundResponse(withHash, secret)).isTrue();

            Map<String, Object> tamperedHash = new LinkedHashMap<>(withHash);
            tamperedHash.put("vnp_SecureHash", "0".repeat(128));
            assertThat(signer.verifyRefundResponse(tamperedHash, secret)).isFalse();
        }
    }

    private void assertPayVector(String label, JsonNode vector, String secret) {
        Map<String, String> params = toStringMap(vector.path("params"));
        String expectedSignData = vector.path("sign_data").asText();
        String expectedHash = vector.path("secure_hash").asText();

        assertThat(signer.buildPaySignData(params)).as(label + " sign_data").isEqualTo(expectedSignData);
        assertThat(signer.signPay(params, secret)).as(label + " secure_hash").isEqualTo(expectedHash);

        Map<String, String> withHash = new LinkedHashMap<>(params);
        assertThat(signer.verifyPay(withHash, secret)).as(label + " missing hash").isFalse();

        withHash.put("vnp_SecureHash", expectedHash);
        assertThat(signer.verifyPay(withHash, secret)).as(label + " verify lowercase").isTrue();

        withHash.put("vnp_SecureHash", expectedHash.toUpperCase(Locale.ROOT));
        assertThat(signer.verifyPay(withHash, secret)).as(label + " verify uppercase").isTrue();

        Map<String, String> tampered = new LinkedHashMap<>(withHash);
        tampered.put("vnp_Amount", "99999999");
        assertThat(signer.verifyPay(tampered, secret)).as(label + " tampered amount").isFalse();
    }

    private static Map<String, String> toStringMap(JsonNode node) {
        Map<String, String> map = new LinkedHashMap<>();
        node.fields().forEachRemaining(e -> map.put(e.getKey(), e.getValue().asText()));
        return map;
    }

    private static Map<String, Object> toObjectMap(JsonNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        node.fields().forEachRemaining(e -> map.put(e.getKey(), e.getValue().asText()));
        return map;
    }

    private static JsonNode readVectors() throws IOException {
        try (InputStream in = new ClassPathResource("vnpay/vectors.json").getInputStream()) {
            return MAPPER.readTree(in);
        }
    }
}
