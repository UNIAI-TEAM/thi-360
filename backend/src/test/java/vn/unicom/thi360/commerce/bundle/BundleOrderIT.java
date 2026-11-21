package vn.unicom.thi360.commerce.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpaySigner;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;
import vn.unicom.thi360.commerce.promotion.domain.Promotion;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudienceType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.LearnerAccount;
import vn.unicom.thi360.exam.spi.ExamEventTopics;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PathChoice;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingPath;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingPathPart;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * Slice 3 ITs cho luồng tạo đơn, tái sử dụng/thay thế đơn chờ, tiếp tục thanh toán và cô lập snapshot bộ đề
 * (AC-12, AC-14, AC-15, AC-16, E13).
 */
class BundleOrderIT extends AbstractBundleIT {

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
    void createBundleOrderFreezesSnapshotAndItemsWithOptionCodes() throws Exception {
        var e1 = partExam("HSA", "TOAN");
        var e2 = subjectExam("HSA", "LY");
        serve(e1);
        serve(e2);
        // Gắn PathChoice cho e1 trên FakePublishedExamPort để kiểm chứng purchase_order_item.option_codes.
        ServingExam base1 = port.findServing(e1.examId).orElseThrow();
        ServingPath pToan = new ServingPath(
                "p-toan", 1,
                List.of(new PathChoice("part-1", "opt-toan", "SUBJECT", "TOAN", "Toán")),
                25, BigDecimal.valueOf(25), 30,
                List.of(new ServingPathPart("part-1", "Phần 1", 25, BigDecimal.valueOf(25), 30)),
                List.of());
        ServingPath pVan = new ServingPath(
                "p-van", 1,
                List.of(new PathChoice("part-2", "opt-van", "SUBJECT", "VAN", "Văn")),
                25, BigDecimal.valueOf(25), 30,
                List.of(new ServingPathPart("part-2", "Phần 2", 25, BigDecimal.valueOf(25), 30)),
                List.of());
        port.serve(new ServingExam(base1.examId(), base1.status(), base1.version(), List.of(pToan, pVan)));

        UUID hsaId = kindItem("HSA");
        String bundleCode = "ORD-B-" + shortToken();
        Map<String, Object> payload = createPayload(bundleCode, "Bộ đề đặt hàng", hsaId, List.of(e1.examId, e2.examId));
        payload.put("listPriceVnd", 200_000L);
        payload.put("attemptsPerExam", 3);
        payload.put("benefitAiExplanation", true);
        payload.put("benefitStrengthWeakness", false);
        payload.put("benefitPractice", true);
        payload.put("benefitNote", "Ghi chú bộ đề");
        payload.put("resultView", "SCORE_AND_ANSWERS");
        JsonNode created = createBundle(payload, bundleAdmin());
        UUID bundleId = UUID.fromString(created.path("id").asText());
        long rev = created.path("revision").asLong();
        performPublish(bundleId, rev, bundleAdmin()).andExpect(status().isOk());

        LearnerAccount learner = fixtures.createLearner();
        JsonNode orderResp = createBundleOrder(learner, bundleCode, null, 1, 200_000L)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.next").value("REDIRECT"))
                .andExpect(jsonPath("$.paymentUrl").isNotEmpty())
                .andReturn().getResponse().getContentAsString().transform(this::readTree);

        JsonNode order = orderResp.path("order");
        assertThat(order.path("productType").asText()).isEqualTo("BUNDLE");
        assertThat(UUID.fromString(order.path("bundleId").asText())).isEqualTo(bundleId);
        assertThat(order.path("bundleVersionNo").asInt()).isEqualTo(1);
        assertThat(order.path("examId").isNull()).isTrue();
        assertThat(order.path("examVersionNo").isNull()).isTrue();
        assertThat(order.path("optionCodes")).isEmpty();
        assertThat(order.path("attemptsGranted").asInt()).isEqualTo(3);
        assertThat(order.path("benefits").path("aiExplanation").asBoolean()).isTrue();
        assertThat(order.path("benefits").path("strengthWeakness").asBoolean()).isFalse();
        assertThat(order.path("benefits").path("practice").asBoolean()).isTrue();
        assertThat(order.path("benefits").path("note").asText()).isEqualTo("Ghi chú bộ đề");
        assertThat(order.path("resultView").asText()).isEqualTo("SCORE_AND_ANSWERS");

        JsonNode items = order.path("items");
        assertThat(items).hasSize(2);
        assertThat(UUID.fromString(items.get(0).path("examId").asText())).isEqualTo(e1.examId);
        assertThat(items.get(0).path("position").asInt()).isEqualTo(1);
        assertThat(items.get(0).path("attemptsGranted").asInt()).isEqualTo(3);
        assertThat(items.get(0).path("optionCodes")).extracting(JsonNode::asText).containsExactly("TOAN", "VAN");
        assertThat(UUID.fromString(items.get(1).path("examId").asText())).isEqualTo(e2.examId);
        assertThat(items.get(1).path("position").asInt()).isEqualTo(2);

        UUID orderId = UUID.fromString(order.path("id").asText());
        List<Map<String, Object>> dbItems = jdbc.queryForList("""
                SELECT exam_id, exam_version_no, attempts_granted, position
                FROM commerce.purchase_order_item
                WHERE order_id = ?
                ORDER BY position ASC
                """, orderId);
        assertThat(dbItems).hasSize(2);
        assertThat(dbItems.get(0).get("exam_id")).isEqualTo(e1.examId);
        assertThat(dbItems.get(1).get("exam_id")).isEqualTo(e2.examId);
    }

    @Test
    void draftEditsAndRepublishKeepExistingOrderSnapshotIsolated() throws Exception {
        var e1 = partExam("HSA", "TOAN");
        var e2 = subjectExam("HSA", "LY");
        var e3 = subjectExam("HSA", "HOA");
        serve(e1);
        serve(e2);
        serve(e3);

        UUID hsaId = kindItem("HSA");
        String bundleCode = "ORD-ISO-" + shortToken();
        Map<String, Object> payload = createPayload(bundleCode, "Bộ đề V1", hsaId, List.of(e1.examId, e2.examId));
        payload.put("listPriceVnd", 180_000L);
        payload.put("attemptsPerExam", 2);
        JsonNode created = createBundle(payload, bundleAdmin());
        UUID bundleId = UUID.fromString(created.path("id").asText());
        long rev = created.path("revision").asLong();
        JsonNode publishedV1 = performPublish(bundleId, rev, bundleAdmin())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().transform(this::readTree);
        rev = publishedV1.path("revision").asLong();

        // Chỉnh sửa nháp nhưng chưa công bố lại: đơn hàng mới vẫn dùng v1.
        Map<String, Object> draftEdit = createPayload(bundleCode, "Bộ đề V2 nháp", hsaId,
                List.of(e1.examId, e2.examId, e3.examId));
        draftEdit.put("expectedRevision", rev);
        draftEdit.put("listPriceVnd", 320_000L);
        draftEdit.put("attemptsPerExam", 5);
        JsonNode updatedDraft = performUpdate(bundleId, draftEdit, bundleAdmin())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().transform(this::readTree);
        rev = updatedDraft.path("revision").asLong();

        LearnerAccount learner = fixtures.createLearner();
        JsonNode v1Resp = createBundleOrder(learner, bundleCode, null, 1, 180_000L)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().transform(this::readTree);
        String v1OrderCode = v1Resp.path("order").path("code").asText();
        payOrderViaIpn(v1Resp.path("paymentUrl").asText(), 180_000L);

        // Công bố v2 -> đơn mới phải dùng giá/phiên bản v2, còn đơn v1 giữ nguyên snapshot v1.
        performPublish(bundleId, rev, bundleAdmin()).andExpect(status().isOk());

        createBundleOrder(learner, bundleCode, null, 1, 180_000L)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRICE_CHANGED"));

        JsonNode v2Resp = createBundleOrder(learner, bundleCode, null, 2, 320_000L)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.bundleVersionNo").value(2))
                .andExpect(jsonPath("$.order.listPriceVnd").value(320_000))
                .andExpect(jsonPath("$.order.attemptsGranted").value(5))
                .andExpect(jsonPath("$.order.items.length()").value(3))
                .andReturn().getResponse().getContentAsString().transform(this::readTree);
        assertThat(v2Resp.path("order").path("code").asText()).isNotEqualTo(v1OrderCode);

        // Đơn v1 giữ nguyên snapshot v1 bất biến.
        mockMvc.perform(get("/api/v1/me/orders/{code}", v1OrderCode).with(fixtures.asLearner(learner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bundleVersionNo").value(1))
                .andExpect(jsonPath("$.listPriceVnd").value(180_000))
                .andExpect(jsonPath("$.attemptsGranted").value(2))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void pendingBundleOrderReusesSameQuoteReplacesChangedQuoteAndSupportsFreeBundleVoucher() throws Exception {
        var e1 = partExam("HSA", "TOAN");
        var e2 = subjectExam("HSA", "LY");
        serve(e1);
        serve(e2);

        UUID hsaId = kindItem("HSA");
        String bundleCode = "ORD-E13-" + shortToken();
        Map<String, Object> payload = createPayload(bundleCode, "Bộ đề E13", hsaId, List.of(e1.examId, e2.examId));
        payload.put("listPriceVnd", 150_000L);
        payload.put("attemptsPerExam", 2);
        JsonNode created = createBundle(payload, bundleAdmin());
        UUID bundleId = UUID.fromString(created.path("id").asText());
        long rev = created.path("revision").asLong();
        performPublish(bundleId, rev, bundleAdmin()).andExpect(status().isOk());

        Promotion partialVoucher = fixtures.createVoucher(
                "BND50", DiscountType.AMOUNT, 50_000L, null, 10, 5, false,
                PromotionScope.BUNDLE, PromotionAudienceType.ALL);
        Promotion freeVoucher = fixtures.createVoucher(
                "BNDFREE", DiscountType.PERCENT, 100L, null, 10, 5, false,
                PromotionScope.BUNDLE, PromotionAudienceType.ALL);
        jdbc.update("INSERT INTO commerce.promotion_scope_item (id, promotion_id, bundle_id) VALUES (?, ?, ?)",
                Ids.newId(), partialVoucher.getId(), bundleId);
        jdbc.update("INSERT INTO commerce.promotion_scope_item (id, promotion_id, bundle_id) VALUES (?, ?, ?)",
                Ids.newId(), freeVoucher.getId(), bundleId);

        LearnerAccount learner = fixtures.createLearner();
        JsonNode firstPending = createBundleOrder(learner, bundleCode, null, 1, 150_000L)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().transform(this::readTree);
        String firstCode = firstPending.path("order").path("code").asText();

        // E13: gọi lại cùng sản phẩm và cùng báo giá -> tái sử dụng đơn PENDING cũ.
        JsonNode reused = createBundleOrder(learner, bundleCode, null, 1, 150_000L)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().transform(this::readTree);
        assertThat(reused.path("order").path("code").asText()).isEqualTo(firstCode);

        // E13: áp voucher đổi số tiền -> thay thế đơn chờ cũ sang REPLACED.
        JsonNode replaced = createBundleOrder(learner, bundleCode, partialVoucher.getCode(), 1, 100_000L)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.amountDueVnd").value(100_000))
                .andReturn().getResponse().getContentAsString().transform(this::readTree);
        String secondCode = replaced.path("order").path("code").asText();
        assertThat(secondCode).isNotEqualTo(firstCode);

        mockMvc.perform(get("/api/v1/me/orders/{code}", firstCode).with(fixtures.asLearner(learner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REPLACED"))
                .andExpect(jsonPath("$.replacedByOrderCode").value(secondCode));

        // Voucher giảm 100% (0đ) -> thay thế đơn chờ trước đó và cấp quyền ngay lập tức.
        JsonNode freeResp = createBundleOrder(learner, bundleCode, freeVoucher.getCode(), 1, 0L)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.next").value("FULFILLED"))
                .andExpect(jsonPath("$.order.state").value("FULFILLED"))
                .andExpect(jsonPath("$.order.paymentMethod").value("FREE"))
                .andExpect(jsonPath("$.order.items.length()").value(2))
                .andReturn().getResponse().getContentAsString().transform(this::readTree);
        assertThat(freeResp.path("paymentUrl").isNull()).isTrue();
    }

    @Test
    void unavailableAndDiscontinuedBundlesReturn409And410OnCreateAndPay() throws Exception {
        var e1 = partExam("HSA", "TOAN");
        var e2 = subjectExam("HSA", "LY");
        serve(e1);
        serve(e2);

        UUID hsaId = kindItem("HSA");
        String bundleCode = "ORD-STS-" + shortToken();
        Map<String, Object> payload = createPayload(bundleCode, "Bộ đề trạng thái", hsaId, List.of(e1.examId, e2.examId));
        payload.put("listPriceVnd", 160_000L);
        payload.put("attemptsPerExam", 2);
        JsonNode created = createBundle(payload, bundleAdmin());
        UUID bundleId = UUID.fromString(created.path("id").asText());
        long rev = created.path("revision").asLong();
        JsonNode published = performPublish(bundleId, rev, bundleAdmin())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().transform(this::readTree);
        rev = published.path("revision").asLong();

        LearnerAccount learner = fixtures.createLearner();
        LearnerAccount fulfilledLearner = fixtures.createLearner();
        JsonNode fulfilledResp = createBundleOrder(fulfilledLearner, bundleCode, null, 1, 160_000L)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().transform(this::readTree);
        UUID fulfilledOrderId = UUID.fromString(fulfilledResp.path("order").path("id").asText());
        payOrderViaIpn(fulfilledResp.path("paymentUrl").asText(), 160_000L);

        JsonNode pendingResp = createBundleOrder(learner, bundleCode, null, 1, 160_000L)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().transform(this::readTree);
        String pendingCode = pendingResp.path("order").path("code").asText();

        // AC-12: bài thi thành phần ngừng bán -> storefront_product chuyển UNAVAILABLE -> 409 PRODUCT_UNAVAILABLE.
        e1.discontinued();
        serve(e1);
        emitExamEvent(ExamEventTopics.DISCONTINUED, e1.examId, 1);

        createBundleOrder(learner, bundleCode, null, 1, 160_000L)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_UNAVAILABLE"));

        mockMvc.perform(post("/api/v1/me/orders/{code}/pay", pendingCode)
                        .with(fixtures.asLearner(learner))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_UNAVAILABLE"));

        // AC-12: đơn đã cấp quyền trước khi thành phần ngừng bán vẫn giữ nguyên trạng thái, dòng đơn và quyền.
        assertFulfilledOrderAndGrantsIntact(fulfilledOrderId, fulfilledLearner.id());

        // AC-14: ngừng bán chính bộ đề -> 410 PRODUCT_DISCONTINUED cả trên create lẫn pay, quyền đã cấp giữ nguyên.
        performDiscontinue(bundleId, rev, bundleAdmin()).andExpect(status().isOk());

        createBundleOrder(learner, bundleCode, null, 1, 160_000L)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("PRODUCT_DISCONTINUED"));

        mockMvc.perform(post("/api/v1/me/orders/{code}/pay", pendingCode)
                        .with(fixtures.asLearner(learner))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("PRODUCT_DISCONTINUED"));

        assertFulfilledOrderAndGrantsIntact(fulfilledOrderId, fulfilledLearner.id());
    }

    @Test
    void bundleOrderUsesLiveServingExamVersionAndBlocksDiscontinuedMemberDuringOutboxLag() throws Exception {
        var e1 = partExam("HSA", "TOAN").named("Toán V1").code("E1V1-" + shortToken());
        var e2 = subjectExam("HSA", "LY").named("Lý V1");
        serve(e1);
        serve(e2);

        UUID hsaId = kindItem("HSA");
        String bundleCode = "ORD-LIVE-" + shortToken();
        Map<String, Object> payload = createPayload(bundleCode, "Bộ đề Live Exam", hsaId, List.of(e1.examId, e2.examId));
        payload.put("listPriceVnd", 190_000L);
        payload.put("attemptsPerExam", 3);
        JsonNode created = createBundle(payload, bundleAdmin());
        UUID bundleId = UUID.fromString(created.path("id").asText());
        performPublish(bundleId, created.path("revision").asLong(), bundleAdmin()).andExpect(status().isOk());

        // Republish e1 lên v2 với tên mới và optionCodes mới, relay ExamPublished.
        e1.nextVersion(2, "Toán V2 Live", 30, 45, 50);
        ServingExam e1BaseV2 = e1.build();
        ServingPath v2Path = new ServingPath(
                "p-v2", 2,
                List.of(new PathChoice("part-1", "opt-v2", "SUBJECT", "TOAN_V2", "Toán V2")),
                30, BigDecimal.valueOf(50), 45,
                List.of(new ServingPathPart("part-1", "Phần 1", 30, BigDecimal.valueOf(50), 45)),
                List.of());
        port.serve(new ServingExam(e1.examId, "PUBLISHED", e1BaseV2.version(), List.of(v2Path)));
        emitExamEvent(ExamEventTopics.PUBLISHED, e1.examId, 2);

        LearnerAccount learner = fixtures.createLearner();
        JsonNode orderResp = createBundleOrder(learner, bundleCode, null, 1, 190_000L)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.bundleVersionNo").value(1))
                .andReturn().getResponse().getContentAsString().transform(this::readTree);

        UUID orderId = UUID.fromString(orderResp.path("order").path("id").asText());
        List<Map<String, Object>> dbItems = jdbc.queryForList("""
                SELECT exam_id, exam_version_no, exam_code, exam_name, option_codes
                FROM commerce.purchase_order_item
                WHERE order_id = ?
                ORDER BY position ASC
                """, orderId);
        assertThat(dbItems).hasSize(2);
        assertThat(dbItems.get(0).get("exam_id")).isEqualTo(e1.examId);
        assertThat(((Number) dbItems.get(0).get("exam_version_no")).intValue()).isEqualTo(2);
        assertThat(dbItems.get(0).get("exam_code")).isEqualTo(e1.code);
        assertThat(dbItems.get(0).get("exam_name")).isEqualTo("Toán V2 Live");
        assertThat((String[]) ((java.sql.Array) dbItems.get(0).get("option_codes")).getArray())
                .containsExactly("TOAN_V2");

        // Trong cửa sổ trễ outbox (e1 đã DISCONTINUED trên PublishedExamPort nhưng chưa relay ExamDiscontinued),
        // storefront_product vẫn đang ON_SALE nhưng tạo đơn phải bị chặn với 409 PRODUCT_UNAVAILABLE.
        e1.discontinued();
        serve(e1);
        LearnerAccount lagLearner = fixtures.createLearner();
        createBundleOrder(lagLearner, bundleCode, null, 1, 190_000L)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_UNAVAILABLE"));
    }

    private void assertFulfilledOrderAndGrantsIntact(UUID orderId, UUID userId) {
        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, orderId))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM commerce.purchase_order_item WHERE order_id = ?", Long.class, orderId))
                .isEqualTo(2L);
        List<Map<String, Object>> grants = jdbc.queryForList(
                "SELECT attempts, attempts_revoked, revoked_at FROM commerce.entitlement_grant WHERE order_id = ?",
                orderId);
        assertThat(grants).hasSize(2);
        assertThat(grants).allSatisfy(row -> {
            assertThat(((Number) row.get("attempts")).intValue()).isEqualTo(2);
            assertThat(((Number) row.get("attempts_revoked")).intValue()).isZero();
            assertThat(row.get("revoked_at")).isNull();
        });
        List<Map<String, Object>> entitlements = jdbc.queryForList(
                "SELECT attempts_total, attempts_revoked FROM commerce.entitlement WHERE user_id = ?",
                userId);
        assertThat(entitlements).hasSize(2);
        assertThat(entitlements).allSatisfy(row -> {
            assertThat(((Number) row.get("attempts_total")).intValue()).isEqualTo(2);
            assertThat(((Number) row.get("attempts_revoked")).intValue()).isZero();
        });
    }

    private org.springframework.test.web.servlet.ResultActions createBundleOrder(
            LearnerAccount learner,
            String code,
            String voucherCode,
            int expectedVersionNo,
            long expectedAmountDue) throws Exception {
        RequestPostProcessor auth = fixtures.asLearner(learner);
        String body = mapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("code", code);
            if (voucherCode != null) {
                put("voucherCode", voucherCode);
            }
            put("expectedAccountId", learner.id());
            put("expectedVersionNo", expectedVersionNo);
            put("expectedAmountDue", expectedAmountDue);
        }});
        return mockMvc.perform(post("/api/v1/me/orders")
                .with(auth)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void payOrderViaIpn(String paymentUrl, long amountVnd) throws Exception {
        String txnRef = parseQueryParams(paymentUrl).get("vnp_TxnRef");
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", Long.toString(amountVnd * 100L));
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", "Thanh toan bo de");
        params.put("vnp_PayDate", "20261121100100");
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
