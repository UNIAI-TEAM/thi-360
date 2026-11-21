package vn.unicom.thi360.commerce.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpaySigner;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.LearnerAccount;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.StoredTestProduct;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.security.SecurityProperties;

/**
 * Slice 3 ITs cho admin list/filter/detail/buyers đối với đơn hàng bộ đề (AC-24, AC-25, AC-26).
 */
class BundleAdminOrdersIT extends AbstractBundleIT {

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private SecurityProperties securityProperties;

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
    void adminOrdersListFilterDetailAndBuyersIncludeBundleOrdersAndItems() throws Exception {
        var e1 = partExam("HSA", "TOAN");
        var e2 = subjectExam("HSA", "LY");
        serve(e1);
        serve(e2);

        UUID hsaId = kindItem("HSA");
        String bundleCode = "ADM-B-" + shortToken();
        Map<String, Object> payload = createPayload(bundleCode, "Bộ đề Quản trị", hsaId, List.of(e1.examId, e2.examId));
        payload.put("listPriceVnd", 210_000L);
        payload.put("attemptsPerExam", 4);
        JsonNode created = createBundle(payload, bundleAdmin());
        UUID bundleId = UUID.fromString(created.path("id").asText());
        performPublish(bundleId, created.path("revision").asLong(), bundleAdmin()).andExpect(status().isOk());

        StoredTestProduct unrelatedSingle = fixtures.insertOnSaleProduct("HSA", 70_000L, 1);

        LearnerAccount buyer = fixtures.createLearner();
        LearnerAccount otherLearner = fixtures.createLearner();

        String bundleOrderCode = buyAndPay(buyer, bundleCode, 210_000L);
        String singleOrderCode = buyAndPay(otherLearner, unrelatedSingle.code(), 70_000L);

        UUID adminId = trackActor();
        RequestPostProcessor orderAdmin = fixtures.asManager(adminId, "order-admin", Permissions.ORDER_VIEW);
        String origin = securityProperties.adminOrigins().getFirst();

        // AC-24: /admin/orders?examId={e1} khớp đơn bộ đề chứa e1 và loại trừ đơn lẻ khác.
        JsonNode filteredList = mockMvc.perform(get("/api/v1/admin/orders")
                        .with(orderAdmin)
                        .header(HttpHeaders.ORIGIN, origin)
                        .param("examId", e1.examId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].code").value(bundleOrderCode))
                .andExpect(jsonPath("$.items[0].productType").value("BUNDLE"))
                .andExpect(jsonPath("$.items[0].bundleId").value(bundleId.toString()))
                .andExpect(jsonPath("$.items[0].examId").value(nullValue()))
                .andExpect(jsonPath("$.items[0].orderState").value("FULFILLED"))
                .andExpect(jsonPath("$.items[0].amountDueVnd").value(210_000))
                .andExpect(jsonPath("$.items[0].items.length()").value(2))
                .andReturn().getResponse().getContentAsString().transform(this::readTree);

        JsonNode summaryItems = filteredList.path("items").get(0).path("items");
        assertThat(summaryItems.get(0).path("examId").asText()).isEqualTo(e1.examId.toString());
        assertThat(summaryItems.get(0).path("position").asInt()).isEqualTo(1);
        assertThat(summaryItems.get(1).path("examId").asText()).isEqualTo(e2.examId.toString());
        assertThat(summaryItems.get(1).path("position").asInt()).isEqualTo(2);

        // AC-25: /admin/orders/{code} trả đầy đủ snapshot bộ đề và các thành phần.
        mockMvc.perform(get("/api/v1/admin/orders/{code}", bundleOrderCode)
                        .with(orderAdmin)
                        .header(HttpHeaders.ORIGIN, origin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(bundleOrderCode))
                .andExpect(jsonPath("$.productType").value("BUNDLE"))
                .andExpect(jsonPath("$.bundleId").value(bundleId.toString()))
                .andExpect(jsonPath("$.bundleVersionNo").value(1))
                .andExpect(jsonPath("$.examId").value(nullValue()))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].attemptsGranted").value(4))
                .andExpect(jsonPath("$.items[0].position").value(1))
                .andExpect(jsonPath("$.items[1].attemptsGranted").value(4))
                .andExpect(jsonPath("$.items[1].position").value(2))
                .andExpect(jsonPath("$.payments[0].state").value("SUCCEEDED"));

        // AC-26: /admin/exams/{examId}/buyers cho từng bài thi thành phần đều liệt kê người mua từ bộ đề.
        for (UUID memberExamId : List.of(e1.examId, e2.examId)) {
            mockMvc.perform(get("/api/v1/admin/exams/{examId}/buyers", memberExamId)
                            .with(orderAdmin)
                            .header(HttpHeaders.ORIGIN, origin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalBuyers").value(1))
                    .andExpect(jsonPath("$.items[0].userId").value(buyer.id().toString()))
                    .andExpect(jsonPath("$.items[0].attemptsTotal").value(4))
                    .andExpect(jsonPath("$.items[0].attemptsRemaining").value(4))
                    .andExpect(jsonPath("$.items[0].orders[0].orderCode").value(bundleOrderCode));
        }

        // Bài thi lẻ không bị nhiễm đơn bộ đề.
        mockMvc.perform(get("/api/v1/admin/exams/{examId}/buyers", unrelatedSingle.examId())
                        .with(orderAdmin)
                        .header(HttpHeaders.ORIGIN, origin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBuyers").value(1))
                .andExpect(jsonPath("$.items[0].orders[0].orderCode").value(singleOrderCode));
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
        params.put("vnp_PayDate", "20261121100300");
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
