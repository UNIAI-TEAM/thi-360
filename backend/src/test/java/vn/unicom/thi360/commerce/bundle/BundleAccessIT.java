package vn.unicom.thi360.commerce.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
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
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpaySigner;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.refund.api.CreateRefundRequest;
import vn.unicom.thi360.commerce.refund.domain.RefundKind;
import vn.unicom.thi360.commerce.spi.AttemptStatusProvider;
import vn.unicom.thi360.commerce.spi.AttemptStatusProvider.OpenAttempt;
import vn.unicom.thi360.commerce.spi.AttemptStatusProvider.OpenAttemptState;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.LearnerAccount;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.StoredTestProduct;
import vn.unicom.thi360.exam.spi.ExamEventTopics;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.security.SecurityProperties;

/**
 * Slice 3 ITs cho `/api/v1/me/exam-access/query` (AC-22) và `/api/v1/me/exams` (AC-23) đối với bộ đề.
 */
class BundleAccessIT extends AbstractBundleIT {

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private SecurityProperties securityProperties;

    @MockitoSpyBean
    private AttemptStatusProvider attemptStatusProvider;

    private CommerceTestFixtures fixtures;
    private final java.util.List<UUID> projectedExamIds = new java.util.ArrayList<>();
    private final java.util.List<UUID> trackedLearnerIds = new java.util.ArrayList<>();

    @BeforeEach
    void initFixtures() {
        reset(attemptStatusProvider);
        fixtures = new CommerceTestFixtures(jdbc, mapper, catalog, promotionRepository, testSecuritySupport);
    }

    @AfterEach
    void cleanFixtures() {
        reset(attemptStatusProvider);
        for (UUID userId : trackedLearnerIds) {
            jdbc.update("""
                    DELETE FROM commerce.refund
                    WHERE order_id IN (SELECT id FROM commerce.purchase_order WHERE user_id = ?)
                    """, userId);
        }
        trackedLearnerIds.clear();
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

        // R1-32: Trạng thái IN_PROGRESS khi bài thi thành phần có lượt đang mở -> items[0].state == IN_PROGRESS & activeAttemptId.
        UUID e1EntitlementId = jdbc.queryForObject("""
                SELECT id FROM commerce.entitlement
                WHERE user_id = ? AND exam_id = ? AND source = 'PURCHASE' AND source_ref IS NULL
                """, UUID.class, learner.id(), e1.examId);
        UUID openAttemptId = UUID.randomUUID();
        doReturn(Optional.of(new OpenAttempt(openAttemptId, OpenAttemptState.IN_PROGRESS)))
                .when(attemptStatusProvider).findOpenAttempt(learner.id(), e1EntitlementId);
        mockMvc.perform(post("/api/v1/me/exam-access/query")
                        .with(fixtures.asLearner(learner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("codes", List.of(b1Code)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].items[0].examId").value(e1.examId.toString()))
                .andExpect(jsonPath("$.items[0].items[0].state").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.items[0].items[0].activeAttemptId").value(openAttemptId.toString()));
        reset(attemptStatusProvider);

        // R1-19/20/21: Đổi tên & mã rồi công bố lại b1 v2, học viên mua lại b1 v2 -> /me/exams chỉ trả 1 MyExamBundleRef mới nhất.
        String b1CodeV2 = "ACC-B1V2-" + shortToken();
        Map<String, Object> b1Rename = createPayload(b1CodeV2, "Bộ đề Truy cập 1 V2", hsaId,
                List.of(e1.examId, e2.examId));
        b1Rename.put("expectedRevision", b1Created.path("revision").asLong() + 1);
        b1Rename.put("listPriceVnd", 155_000L);
        b1Rename.put("attemptsPerExam", 2);
        String b1UpdatedJson = performUpdate(b1Id, b1Rename, bundleAdmin())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long revBeforePub2 = mapper.readTree(b1UpdatedJson).path("revision").asLong();
        performPublish(b1Id, revBeforePub2, bundleAdmin()).andExpect(status().isOk());

        buyAndPayWithVersion(learner, b1CodeV2, 2, 155_000L);

        JsonNode myExamsAfterRebuy = mockMvc.perform(get("/api/v1/me/exams").with(fixtures.asLearner(learner)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().transform(this::readTree);
        JsonNode e2CardAfterRebuy = findCard(myExamsAfterRebuy.path("items"), e2Code);
        assertThat(e2CardAfterRebuy.path("bundles")).hasSize(1);
        assertThat(e2CardAfterRebuy.path("bundles").get(0).path("bundleId").asText()).isEqualTo(b1Id.toString());
        assertThat(e2CardAfterRebuy.path("bundles").get(0).path("code").asText()).isEqualTo(b1CodeV2);
        assertThat(e2CardAfterRebuy.path("bundles").get(0).path("name").asText()).isEqualTo("Bộ đề Truy cập 1 V2");
    }

    @Test
    void handlesCheckingPendingBundleOrderAndExcludesPendingPaymentAndRefundedFromMyExamsBundles() throws Exception {
        var e1 = partExam("HSA", "TOAN");
        var e2 = subjectExam("HSA", "LY");
        serve(e1);
        serve(e2);
        projectedExamIds.addAll(List.of(e1.examId, e2.examId));
        emitExamEvent(ExamEventTopics.PUBLISHED, e1.examId, 1);
        emitExamEvent(ExamEventTopics.PUBLISHED, e2.examId, 1);

        UUID hsaId = kindItem("HSA");
        String checkingCode = "ACC-CHK-" + shortToken();
        JsonNode checkingCreated = createBundle(createPayload(checkingCode, "Bộ đề Chờ cấp", hsaId,
                List.of(e1.examId, e2.examId)), bundleAdmin());
        UUID checkingBundleId = UUID.fromString(checkingCreated.path("id").asText());
        performPublish(checkingBundleId, checkingCreated.path("revision").asLong(), bundleAdmin())
                .andExpect(status().isOk());

        String pendingPayCode = "ACC-PND-" + shortToken();
        JsonNode pendingCreated = createBundle(createPayload(pendingPayCode, "Bộ đề Chưa trả", hsaId,
                List.of(e1.examId, e2.examId)), bundleAdmin());
        UUID pendingBundleId = UUID.fromString(pendingCreated.path("id").asText());
        performPublish(pendingBundleId, pendingCreated.path("revision").asLong(), bundleAdmin())
                .andExpect(status().isOk());

        String refundCode = "ACC-RFD-" + shortToken();
        JsonNode refundCreated = createBundle(createPayload(refundCode, "Bộ đề Đã hoàn", hsaId,
                List.of(e1.examId, e2.examId)), bundleAdmin());
        UUID refundBundleId = UUID.fromString(refundCreated.path("id").asText());
        performPublish(refundBundleId, refundCreated.path("revision").asLong(), bundleAdmin())
                .andExpect(status().isOk());

        LearnerAccount learner = fixtures.createLearner();
        trackedLearnerIds.add(learner.id());

        // 1. Tạo đơn PAID chưa cấp quyền -> /exam-access/query trả CHECKING + hasPendingFulfillment=true,
        //    và /me/exams trả thẻ CHECKING mang bundles[].
        JsonNode checkingOrderResp = createBundleOrder(learner, checkingCode, 1, 150_000L);
        UUID checkingOrderId = UUID.fromString(checkingOrderResp.path("order").path("id").asText());
        String checkingOrderCode = checkingOrderResp.path("order").path("code").asText();
        jdbc.update("UPDATE commerce.purchase_order SET state = 'PAID', paid_at = now() WHERE id = ?",
                checkingOrderId);

        // 2. Tạo đơn PENDING_PAYMENT (chưa thanh toán) -> không được xuất hiện trong MyExamCard.bundles.
        createBundleOrder(learner, pendingPayCode, 1, 150_000L);

        // 3. Tạo và hoàn tiền toàn bộ đơn refundCode -> chuyển REFUNDED và không xuất hiện trong MyExamCard.bundles.
        JsonNode refundedOrderResp = createBundleOrder(learner, refundCode, 1, 150_000L);
        UUID refundedOrderId = UUID.fromString(refundedOrderResp.path("order").path("id").asText());
        String refundedOrderCode = refundedOrderResp.path("order").path("code").asText();
        payOrderViaIpn(refundedOrderResp.path("paymentUrl").asText(), 150_000L);
        UUID paymentId = jdbc.queryForObject(
                "SELECT id FROM commerce.payment WHERE order_id = ? AND state = 'SUCCEEDED'",
                UUID.class, refundedOrderId);
        UUID adminId = trackActor();
        jdbc.update("DELETE FROM commerce.refund WHERE order_id = ?", refundedOrderId);
        mockMvc.perform(post("/api/v1/admin/orders/{code}/payments/{paymentId}/refunds",
                                refundedOrderCode, paymentId)
                        .with(fixtures.asManager(adminId, "refund-acc-admin",
                                Permissions.ORDER_VIEW, Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new CreateRefundRequest(
                                150_000L, RefundKind.API, "Hoàn toàn bộ kiểm tra bundles[]", Boolean.TRUE, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("SUCCEEDED"));

        mockMvc.perform(post("/api/v1/me/exam-access/query")
                        .with(fixtures.asLearner(learner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("codes", List.of(checkingCode)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].items.length()").value(2))
                .andExpect(jsonPath("$.items[0].items[0].state").value("CHECKING"))
                .andExpect(jsonPath("$.items[0].items[0].hasPendingFulfillment").value(true))
                .andExpect(jsonPath("$.items[0].items[0].pendingOrderCode").value(checkingOrderCode))
                .andExpect(jsonPath("$.items[0].items[1].state").value("CHECKING"))
                .andExpect(jsonPath("$.items[0].items[1].hasPendingFulfillment").value(true))
                .andExpect(jsonPath("$.items[0].items[1].pendingOrderCode").value(checkingOrderCode));

        JsonNode myExams = mockMvc.perform(get("/api/v1/me/exams").with(fixtures.asLearner(learner)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().transform(this::readTree);

        String e1Code = port.findServing(e1.examId).orElseThrow().version().code();
        JsonNode checkingCard = null;
        for (JsonNode card : myExams.path("items")) {
            if (e1Code.equals(card.path("code").asText()) && "CHECKING".equals(card.path("state").asText())) {
                checkingCard = card;
                break;
            }
        }
        assertThat(checkingCard).isNotNull();
        assertThat(checkingCard.path("bundles")).extracting(n -> n.path("bundleId").asText())
                .containsExactly(checkingBundleId.toString());

        // Thẻ entitlement của e1 (từ đơn đã hoàn tiền) cũng không chứa pendingBundleId hay refundBundleId.
        for (JsonNode card : myExams.path("items")) {
            assertThat(card.path("bundles")).extracting(n -> n.path("bundleId").asText())
                    .doesNotContain(pendingBundleId.toString(), refundBundleId.toString());
        }
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
        buyAndPayWithVersion(learner, productCode, 1, expectedAmountDue);
    }

    private void buyAndPayWithVersion(
            LearnerAccount learner, String productCode, int expectedVersionNo, long expectedAmountDue)
            throws Exception {
        JsonNode created = createBundleOrder(learner, productCode, expectedVersionNo, expectedAmountDue);
        payOrderViaIpn(created.path("paymentUrl").asText(), expectedAmountDue);
    }

    private JsonNode createBundleOrder(
            LearnerAccount learner, String productCode, int expectedVersionNo, long expectedAmountDue)
            throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "code", productCode,
                "expectedAccountId", learner.id(),
                "expectedVersionNo", expectedVersionNo,
                "expectedAmountDue", expectedAmountDue));
        return mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(learner))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().transform(this::readTree);
    }

    private void payOrderViaIpn(String paymentUrl, long expectedAmountDue) throws Exception {
        String txnRef = parseQueryParams(paymentUrl).get("vnp_TxnRef");
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", Long.toString(expectedAmountDue * 100L));
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", "Thanh toan bo de");
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
