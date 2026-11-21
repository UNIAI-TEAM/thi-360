package vn.unicom.thi360.commerce.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpaySigner;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.LearnerAccount;

/**
 * Slice 3 ITs cho gửi mail xác nhận đơn bộ đề khi cấp quyền (AC-27): không NPE với {@code examId == null}.
 */
class BundleMailIT extends AbstractBundleIT {

    @Autowired
    private PromotionRepository promotionRepository;

    private CommerceTestFixtures fixtures;

    @BeforeEach
    void initFixtures() {
        fixtures = new CommerceTestFixtures(jdbc, mapper, catalog, promotionRepository, testSecuritySupport);
    }

    @AfterEach
    void cleanFixtures() {
        fixtures.cleanup();
    }

    @Test
    void fulfilledBundleOrderEnqueuesOrderFulfilledEmailForViAndEnLearnersWithoutNpe() throws Exception {
        var e1 = partExam("HSA", "TOAN");
        var e2 = subjectExam("HSA", "LY");
        serve(e1);
        serve(e2);

        UUID hsaId = kindItem("HSA");
        String bundleCode = "MAIL-B-" + shortToken();
        String bundleName = "Bộ đề gửi thư";
        Map<String, Object> payload = createPayload(bundleCode, bundleName, hsaId, List.of(e1.examId, e2.examId));
        payload.put("listPriceVnd", 140_000L);
        payload.put("attemptsPerExam", 3);
        JsonNode created = createBundle(payload, bundleAdmin());
        UUID bundleId = UUID.fromString(created.path("id").asText());
        performPublish(bundleId, created.path("revision").asLong(), bundleAdmin()).andExpect(status().isOk());

        LearnerAccount viLearner = fixtures.createLearner(true, "vi");
        LearnerAccount enLearner = fixtures.createLearner(true, "en");
        LearnerAccount unverifiedLearner = fixtures.createLearner(false, "vi");

        String viOrderCode = buyAndPay(viLearner, bundleCode, 140_000L);
        String enOrderCode = buyAndPay(enLearner, bundleCode, 140_000L);
        String unverifiedOrderCode = buyAndPay(unverifiedLearner, bundleCode, 140_000L);

        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE code = ?", String.class, unverifiedOrderCode))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM shared.email_message WHERE user_id = ?", Long.class, unverifiedLearner.id()))
                .isZero();

        String e1Code = port.findServing(e1.examId).orElseThrow().version().code();
        String e2Code = port.findServing(e2.examId).orElseThrow().version().code();

        Map<String, Object> viMail = jdbc.queryForMap("""
                SELECT kind, to_email, locale, subject, html, text
                FROM shared.email_message
                WHERE user_id = ?
                """, viLearner.id());
        assertThat(viMail.get("kind")).isEqualTo("ORDER_FULFILLED");
        assertThat(viMail.get("to_email")).isEqualTo(viLearner.email());
        assertThat(viMail.get("locale")).isEqualTo("vi");
        assertThat((String) viMail.get("text")).contains(viOrderCode).contains(e1Code).contains(e2Code);
        assertThat((String) viMail.get("html")).contains(viOrderCode).contains(e1Code).contains(e2Code);

        Map<String, Object> enMail = jdbc.queryForMap("""
                SELECT kind, to_email, locale, subject, html, text
                FROM shared.email_message
                WHERE user_id = ?
                """, enLearner.id());
        assertThat(enMail.get("kind")).isEqualTo("ORDER_FULFILLED");
        assertThat(enMail.get("to_email")).isEqualTo(enLearner.email());
        assertThat(enMail.get("locale")).isEqualTo("en");
        assertThat((String) enMail.get("text")).contains(enOrderCode).contains(e1Code).contains(e2Code);
        assertThat((String) enMail.get("html")).contains(enOrderCode).contains(e1Code).contains(e2Code);
    }

    private String buyAndPay(LearnerAccount learner, String productCode, long expectedAmountDue) throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "code", productCode,
                "expectedAccountId", learner.id(),
                "expectedVersionNo", 1,
                "expectedAmountDue", expectedAmountDue));
        JsonNode created = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(learner))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().transform(this::readTree);

        String orderCode = created.path("order").path("code").asText();
        String txnRef = parseQueryParams(created.path("paymentUrl").asText()).get("vnp_TxnRef");
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", Long.toString(expectedAmountDue * 100L));
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", "Thanh toan " + productCode);
        params.put("vnp_PayDate", "20261121100400");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TmnCode", MockVnpayGateway.DEFAULT_MOCK_TMN_CODE);
        params.put("vnp_TransactionNo", "VNP" + shortToken());
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_SecureHash", VnpaySigner.signPayParams(params, MockVnpayGateway.DEFAULT_MOCK_SECRET));

        var req = get("/api/v1/public/payments/vnpay/ipn")
                .with(r -> {
                    r.setRemoteAddr("127.0.0.1");
                    return r;
                });
        params.forEach(req::param);
        mockMvc.perform(req)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"));
        return orderCode;
    }

    private static Map<String, String> parseQueryParams(String url) {
        String query = URI.create(url).getRawQuery();
        Map<String, String> map = new TreeMap<>();
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
            String v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
            map.put(k, v);
        }
        return map;
    }

    private JsonNode readTree(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
