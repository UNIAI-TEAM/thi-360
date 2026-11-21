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
import org.springframework.http.MediaType;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpaySigner;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.LearnerAccount;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.StoredTestProduct;
import vn.unicom.thi360.exam.spi.ExamEventTopics;

/**
 * Slice 3 ITs cho `/api/v1/me/exam-access/query` (AC-22) và `/api/v1/me/exams` (AC-23) đối với bộ đề.
 */
class BundleAccessIT extends AbstractBundleIT {

    @Autowired
    private PromotionRepository promotionRepository;

    private CommerceTestFixtures fixtures;
    private final java.util.List<UUID> projectedExamIds = new java.util.ArrayList<>();

    @BeforeEach
    void initFixtures() {
        fixtures = new CommerceTestFixtures(jdbc, mapper, catalog, promotionRepository, testSecuritySupport);
    }

    @AfterEach
    void cleanFixtures() {
        fixtures.cleanup();
        projectedExamIds.forEach(examId -> jdbc.update("DELETE FROM commerce.storefront_product WHERE exam_id = ?", examId));
        projectedExamIds.clear();
    }

    @Test
    void examAccessQueryExpandsBundleItemsAndMyExamsReturnsPurchasedBundles() throws Exception {
        var e1 = partExam("HSA", "TOAN");
        var e2 = subjectExam("HSA", "LY");
        var e3 = subjectExam("HSA", "HOA");
        serve(e1);
        serve(e2);
        serve(e3);
        projectedExamIds.addAll(List.of(e1.examId, e2.examId, e3.examId));
        emitExamEvent(ExamEventTopics.PUBLISHED, e1.examId, 1);
        emitExamEvent(ExamEventTopics.PUBLISHED, e2.examId, 1);
        emitExamEvent(ExamEventTopics.PUBLISHED, e3.examId, 1);

        UUID hsaId = kindItem("HSA");
        String b1Code = "ACC-B1-" + shortToken();
        Map<String, Object> b1Payload = createPayload(b1Code, "Bộ đề Truy cập 1", hsaId, List.of(e1.examId, e2.examId));
        b1Payload.put("listPriceVnd", 150_000L);
        b1Payload.put("attemptsPerExam", 2);
        JsonNode b1Created = createBundle(b1Payload, bundleAdmin());
        UUID b1Id = UUID.fromString(b1Created.path("id").asText());
        performPublish(b1Id, b1Created.path("revision").asLong(), bundleAdmin()).andExpect(status().isOk());

        String b2Code = "ACC-B2-" + shortToken();
        Map<String, Object> b2Payload = createPayload(b2Code, "Bộ đề Truy cập 2", hsaId, List.of(e1.examId, e3.examId));
        b2Payload.put("listPriceVnd", 180_000L);
        b2Payload.put("attemptsPerExam", 3);
        JsonNode b2Created = createBundle(b2Payload, bundleAdmin());
        UUID b2Id = UUID.fromString(b2Created.path("id").asText());
        performPublish(b2Id, b2Created.path("revision").asLong(), bundleAdmin()).andExpect(status().isOk());

        // Thêm 1 bài thi mua lẻ không thuộc bộ đề để kiểm chứng MyExamCard.bundles = []
        StoredTestProduct singleProduct = fixtures.insertOnSaleProduct("HSA", 90_000L, 1);

        LearnerAccount learner = fixtures.createLearner();

        // Trước khi mua: bundle có state=NONE và items[] chứa từng bài thi thành phần ở trạng thái NONE.
        String queryBody = mapper.writeValueAsString(Map.of("codes", List.of(b1Code, singleProduct.code())));
        mockMvc.perform(post("/api/v1/me/exam-access/query")
                        .with(fixtures.asLearner(learner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(queryBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].code").value(b1Code))
                .andExpect(jsonPath("$.items[0].state").value("NONE"))
                .andExpect(jsonPath("$.items[0].items.length()").value(2))
                .andExpect(jsonPath("$.items[0].items[0].examId").value(e1.examId.toString()))
                .andExpect(jsonPath("$.items[0].items[0].state").value("NONE"))
                .andExpect(jsonPath("$.items[0].items[0].attemptsLeft").value(0))
                .andExpect(jsonPath("$.items[0].items[1].examId").value(e2.examId.toString()))
                .andExpect(jsonPath("$.items[0].items[1].state").value("NONE"))
                .andExpect(jsonPath("$.items[0].items[1].attemptsLeft").value(0))
                .andExpect(jsonPath("$.items[1].code").value(singleProduct.code()))
                .andExpect(jsonPath("$.items[1].items").value(nullValue()));

        // Mua và thanh toán bộ 1, bộ 2, và bài thi lẻ.
        buyAndPay(learner, b1Code, 150_000L);
        buyAndPay(learner, b2Code, 180_000L);
        buyAndPay(learner, singleProduct.code(), 90_000L);

        // Sau khi mua: exam-access/query phản ánh số lượt khả dụng gộp của từng thành phần trong bundle.items.
        mockMvc.perform(post("/api/v1/me/exam-access/query")
                        .with(fixtures.asLearner(learner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("codes", List.of(b1Code)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].code").value(b1Code))
                .andExpect(jsonPath("$.items[0].state").value("NONE"))
                .andExpect(jsonPath("$.items[0].items[0].examId").value(e1.examId.toString()))
                .andExpect(jsonPath("$.items[0].items[0].state").value("CAN_START"))
                .andExpect(jsonPath("$.items[0].items[0].attemptsLeft").value(5))
                .andExpect(jsonPath("$.items[0].items[1].examId").value(e2.examId.toString()))
                .andExpect(jsonPath("$.items[0].items[1].state").value("CAN_START"))
                .andExpect(jsonPath("$.items[0].items[1].attemptsLeft").value(2));

        // /api/v1/me/exams trả 4 thẻ (e1, e2, e3, single) với MyExamBundleRef chính xác.
        String e1Code = port.findServing(e1.examId).orElseThrow().version().code();
        String e2Code = port.findServing(e2.examId).orElseThrow().version().code();
        JsonNode myExams = mockMvc.perform(get("/api/v1/me/exams").with(fixtures.asLearner(learner)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().transform(this::readTree);

        JsonNode cards = myExams.path("items");
        assertThat(cards).hasSize(4);

        JsonNode e1Card = findCard(cards, e1Code);
        assertThat(e1Card.path("bundles")).hasSize(2);
        assertThat(e1Card.path("bundles")).extracting(n -> n.path("code").asText())
                .containsExactlyInAnyOrder(b1Code, b2Code);
        assertThat(e1Card.path("bundles")).extracting(n -> n.path("name").asText())
                .containsExactlyInAnyOrder("Bộ đề Truy cập 1", "Bộ đề Truy cập 2");

        JsonNode e2Card = findCard(cards, e2Code);
        assertThat(e2Card.path("bundles")).hasSize(1);
        assertThat(e2Card.path("bundles").get(0).path("bundleId").asText()).isEqualTo(b1Id.toString());

        JsonNode singleCard = findCard(cards, singleProduct.code());
        assertThat(singleCard.path("bundles")).isEmpty();
    }

    private JsonNode findCard(JsonNode cards, String examCode) {
        for (JsonNode card : cards) {
            if (examCode.equals(card.path("code").asText())) {
                return card;
            }
        }
        throw new AssertionError("Missing MyExamCard for code " + examCode);
    }

    private void buyAndPay(LearnerAccount learner, String productCode, long expectedAmountDue) throws Exception {
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

        String txnRef = parseQueryParams(created.path("paymentUrl").asText()).get("vnp_TxnRef");
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", Long.toString(expectedAmountDue * 100L));
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", "Thanh toan " + productCode);
        params.put("vnp_PayDate", "20261121100200");
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
