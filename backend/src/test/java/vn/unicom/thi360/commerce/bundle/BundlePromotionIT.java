package vn.unicom.thi360.commerce.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.unicom.thi360.commerce.pricing.PriceCalculator;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.PricedProduct;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.QuoteCalculation;
import vn.unicom.thi360.commerce.pricing.PriceCalculator.QuoteRequest;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.security.TestSecurity.SimulatedPrincipal;

/**
 * AC-21: Khuyến mãi và voucher với {@code scope = BUNDLE} (validation, hiển thị giá trên storefront, ưu tiên
 * best auto giữa {@code BUNDLE} và {@code EXAM_KIND}, preview giá, và capWarnings).
 */
@TestPropertySource(properties = "thi360.commerce.vnpay-min-amount=5000")
class BundlePromotionIT extends AbstractBundleIT {

    private static final String PROMO_BASE = "/api/v1/admin/promotions";

    @Autowired
    private PriceCalculator priceCalculator;

    private final Set<UUID> promoIds = ConcurrentHashMap.newKeySet();
    private final Set<UUID> learnerIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanupPromotions() {
        for (UUID userId : learnerIds) {
            CommerceTestFixtures.deleteStubOrdersForUser(jdbc, userId);
        }
        for (UUID id : promoIds) {
            jdbc.update("DELETE FROM commerce.promotion_change_log WHERE promotion_id = ?", id);
            jdbc.update("DELETE FROM commerce.promotion_redemption WHERE promotion_id = ?", id);
            jdbc.update("DELETE FROM commerce.promotion_scope_item WHERE promotion_id = ?", id);
            jdbc.update("DELETE FROM commerce.promotion WHERE id = ?", id);
        }
        promoIds.clear();
        for (UUID userId : learnerIds) {
            jdbc.update("DELETE FROM identity.user_account WHERE id = ?", userId);
        }
        learnerIds.clear();
    }

    @Test
    void validatesBundleScopeTargetsAppliesAutoAndVoucherAndSupportsPricePreview() throws Exception {
        UUID actor = trackActor();
        var auth = asUser(actor, "combo.admin",
                Permissions.EXAM_VIEW, Permissions.EXAM_MODIFY, Permissions.EXAM_PUBLISH,
                Permissions.PROMOTION_VIEW, Permissions.PROMOTION_MODIFY);
        UUID hsaId = kindItem("HSA");

        BundleExamBuilder e1 = partExam("HSA", "TOAN");
        BundleExamBuilder e2 = subjectExam("HSA", "VAN");
        serve(e1);
        serve(e2);

        String codeA = "PR-A-" + shortToken();
        Map<String, Object> reqA = createPayload(codeA, "Bộ đề A", hsaId, List.of(e1.examId, e2.examId));
        reqA.put("listPriceVnd", 200_000L);
        JsonNode createdA = createBundle(reqA, auth);
        UUID bundleA = UUID.fromString(createdA.path("id").asText());
        performPublish(bundleA, createdA.path("revision").asLong(), auth).andExpect(status().isOk());

        String codeB = "PR-B-" + shortToken();
        Map<String, Object> reqB = createPayload(codeB, "Bộ đề B", hsaId, List.of(e1.examId, e2.examId));
        reqB.put("listPriceVnd", 250_000L);
        JsonNode createdB = createBundle(reqB, auth);
        UUID bundleB = UUID.fromString(createdB.path("id").asText());
        performPublish(bundleB, createdB.path("revision").asLong(), auth).andExpect(status().isOk());

        JsonNode draftB = createBundle(createPayload("PR-DFT-" + shortToken(), "Draft B", hsaId,
                List.of(e1.examId, e2.examId)), auth);
        UUID draftId = UUID.fromString(draftB.path("id").asText());

        // 1. Validate scope = BUNDLE: thiếu bundleIds -> 400; truyền examIds -> 400; truyền draftId -> 422
        mockMvc.perform(post(PROMO_BASE)
                        .with(auth)
                        .header("Idempotency-Key", "k-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(autoPromoPayload("No Targets", 10_000L, "BUNDLE",
                                Map.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("bundleIds"));

        mockMvc.perform(post(PROMO_BASE)
                        .with(auth)
                        .header("Idempotency-Key", "k-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(autoPromoPayload("ExamIds Disallowed", 10_000L, "BUNDLE",
                                Map.of("examIds", List.of(e1.examId), "bundleIds", List.of(bundleA))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("examIds"));

        mockMvc.perform(post(PROMO_BASE)
                        .with(auth)
                        .header("Idempotency-Key", "k-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(autoPromoPayload("Draft Disallowed", 10_000L, "BUNDLE",
                                Map.of("bundleIds", List.of(draftId))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PROMOTION_SCOPE_INVALID"));

        // 2. Tạo KM tự động scope = BUNDLE (giảm 30k cho bundleA) và scope = EXAM_KIND (giảm 20k cho HSA)
        JsonNode promoBundle = createPromo(autoPromoPayload("Giảm Bộ A 30k", 30_000L, "BUNDLE",
                Map.of("bundleIds", List.of(bundleA))), auth);
        UUID promoBundleId = UUID.fromString(promoBundle.path("id").asText());
        assertThat(promoBundle.path("scope").asText()).isEqualTo("BUNDLE");
        assertThat(promoBundle.path("scopeItems").get(0).path("type").asText()).isEqualTo("BUNDLE");
        assertThat(promoBundle.path("scopeItems").get(0).path("code").asText()).isEqualTo(codeA);

        createPromo(autoPromoPayload("Giảm HSA 20k", 20_000L, "EXAM_KIND",
                Map.of("examKindIds", List.of(hsaId))), auth);

        // KM tự động scope = EXAM cho bài thành phần e1 KHÔNG áp dụng cho bộ đề
        createPromo(autoPromoPayload("Giảm riêng E1 90k", 90_000L, "EXAM",
                Map.of("examIds", List.of(e1.examId))), auth);

        // Storefront: bundleA hưởng KM tốt nhất 30k -> 170_000; bundleB hưởng EXAM_KIND 20k -> 230_000
        mockMvc.perform(get(STOREFRONT_BASE + "/products/" + codeA)
                        .header("X-Forwarded-For", newClientIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listPrice").value(200_000))
                .andExpect(jsonPath("$.price").value(170_000))
                .andExpect(jsonPath("$.promotion.name").value("Giảm Bộ A 30k"));

        mockMvc.perform(get(STOREFRONT_BASE + "/products/" + codeB)
                        .header("X-Forwarded-For", newClientIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listPrice").value(250_000))
                .andExpect(jsonPath("$.price").value(230_000))
                .andExpect(jsonPath("$.promotion.name").value("Giảm HSA 20k"));

        mockMvc.perform(get(STOREFRONT_BASE + "/products")
                        .param("format", "BUNDLE")
                        .header("X-Forwarded-For", newClientIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.code=='" + codeA + "')].price", hasItem(170_000)))
                .andExpect(jsonPath("$.items[?(@.code=='" + codeB + "')].price", hasItem(230_000)));

        // 3. Price preview cho bundleId
        mockMvc.perform(post(PROMO_BASE + "/price-preview")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "bundleId", bundleA,
                                "promotionId", promoBundleId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode").value(codeA))
                .andExpect(jsonPath("$.listPrice").value(200_000))
                .andExpect(jsonPath("$.finalPrice").value(170_000));

        // 4. Voucher scope = BUNDLE áp dụng cho bundleA nhưng không áp dụng cho bundleB
        String voucherCode = "VBD" + shortToken();
        Map<String, Object> voucherReq = autoPromoPayload("Voucher Bộ A", 50_000L, "BUNDLE",
                Map.of("bundleIds", List.of(bundleA)));
        voucherReq.put("kind", "VOUCHER");
        voucherReq.put("code", voucherCode);
        voucherReq.put("audience", "ALL");
        voucherReq.put("combinable", false);
        createPromo(voucherReq, auth);

        UUID buyer = UUID.randomUUID();
        QuoteCalculation quoteA = priceCalculator.calculateQuote(
                new QuoteRequest(PricedProduct.forBundle(bundleA, hsaId, 200_000L), buyer, voucherCode));
        assertThat(quoteA.finalPrice()).isEqualTo(150_000L);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> priceCalculator.calculateQuote(
                new QuoteRequest(PricedProduct.forBundle(bundleB, hsaId, 250_000L), buyer, voucherCode)))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("VOUCHER_NOT_APPLICABLE"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> priceCalculator.calculateQuote(
                new QuoteRequest(new PricedProduct(e1.examId, hsaId, 79_000L), buyer, voucherCode)))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("VOUCHER_NOT_APPLICABLE"));

        // 4b. Endpoint học viên POST /api/v1/me/price-quote nhận mã bộ đề như mã bài thi
        UUID learnerId = createActiveLearner();
        mockMvc.perform(post("/api/v1/me/price-quote")
                        .with(learner(learnerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("code", codeA, "voucherCode", voucherCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode").value(codeA))
                .andExpect(jsonPath("$.listPrice").value(200_000))
                .andExpect(jsonPath("$.finalPrice").value(150_000));
        mockMvc.perform(post("/api/v1/me/price-quote")
                        .with(learner(learnerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("code", codeB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode").value(codeB))
                .andExpect(jsonPath("$.finalPrice").value(230_000));

        // 5. Auto giảm sâu chạm sàn vnpayMinAmount -> xuất hiện trong capWarnings cho bundleA
        assertThat(pricingProperties.vnpayMinAmount()).isEqualTo(5_000L);
        JsonNode flooredPromo = createPromo(autoPromoPayload("Floor Check", 199_000L, "BUNDLE",
                Map.of("bundleIds", List.of(bundleA))), auth);
        assertThat(flooredPromo.path("capWarnings").findValuesAsText("examId")).contains(bundleA.toString());

        // 6. R1-30: scope = ALL (auto + voucher) áp dụng cho bundle; EXAM_KIND lệch (SP2E) không áp cho HSA bundle;
        //    voucher scope = EXAM cho e1 bị từ chối VOUCHER_NOT_APPLICABLE khi áp lên bundle.
        UUID sp2eId = kindItem("SP2E");
        createPromo(autoPromoPayload("KM SP2E lệch kỳ thi", 45_000L, "EXAM_KIND",
                Map.of("examKindIds", List.of(sp2eId))), auth);
        // bundleB vẫn chỉ hưởng Giảm HSA 20k -> 230_000 (không bị KM SP2E 45k áp vào).
        mockMvc.perform(get(STOREFRONT_BASE + "/products/" + codeB)
                        .header("X-Forwarded-For", newClientIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(230_000))
                .andExpect(jsonPath("$.promotion.name").value("Giảm HSA 20k"));

        String examVoucherCode = "VEX" + shortToken();
        Map<String, Object> examVoucherReq = autoPromoPayload("Voucher Riêng E1", 35_000L, "EXAM",
                Map.of("examIds", List.of(e1.examId)));
        examVoucherReq.put("kind", "VOUCHER");
        examVoucherReq.put("code", examVoucherCode);
        examVoucherReq.put("audience", "ALL");
        examVoucherReq.put("combinable", false);
        createPromo(examVoucherReq, auth);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> priceCalculator.calculateQuote(
                new QuoteRequest(PricedProduct.forBundle(bundleA, hsaId, 200_000L), buyer, examVoucherCode)))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("VOUCHER_NOT_APPLICABLE"));

        String allVoucherCode = "VALL" + shortToken();
        Map<String, Object> allVoucherReq = autoPromoPayload("Voucher Toàn Sàn", 25_000L, "ALL", Map.of());
        allVoucherReq.put("kind", "VOUCHER");
        allVoucherReq.put("code", allVoucherCode);
        allVoucherReq.put("audience", "ALL");
        allVoucherReq.put("combinable", false);
        createPromo(allVoucherReq, auth);
        QuoteCalculation allVoucherQuote = priceCalculator.calculateQuote(
                new QuoteRequest(PricedProduct.forBundle(bundleA, hsaId, 200_000L), buyer, allVoucherCode));
        assertThat(allVoucherQuote.finalPrice()).isEqualTo(175_000L);

        // scope = ALL auto (giảm 60k) thắng BUNDLE 30k & EXAM_KIND 20k trên bundleB -> 250k - 60k = 190k.
        createPromo(autoPromoPayload("KM Toàn Sàn 60k", 60_000L, "ALL", Map.of()), auth);
        mockMvc.perform(get(STOREFRONT_BASE + "/products/" + codeB)
                        .header("X-Forwarded-For", newClientIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(190_000))
                .andExpect(jsonPath("$.promotion.name").value("KM Toàn Sàn 60k"));
    }

    @Test
    void sortsBundlesByPostPromotionPriceValidatesPatchBundleIdsAndPricePreviewXor() throws Exception {
        UUID actor = trackActor();
        var auth = asUser(actor, "combo.sort.admin",
                Permissions.EXAM_VIEW, Permissions.EXAM_MODIFY, Permissions.EXAM_PUBLISH,
                Permissions.PROMOTION_VIEW, Permissions.PROMOTION_MODIFY);
        UUID hsaId = kindItem("HSA");

        BundleExamBuilder e1 = partExam("HSA", "TOAN");
        BundleExamBuilder e2 = subjectExam("HSA", "VAN");
        serve(e1);
        serve(e2);

        String sortToken = "SORT" + shortToken();
        String codeLow = sortToken + "-LOW";
        Map<String, Object> reqLow = createPayload(codeLow, "Bộ Đề Thấp " + sortToken, hsaId,
                List.of(e1.examId, e2.examId));
        reqLow.put("listPriceVnd", 200_000L);
        JsonNode createdLow = createBundle(reqLow, auth);
        UUID bundleLow = UUID.fromString(createdLow.path("id").asText());
        performPublish(bundleLow, createdLow.path("revision").asLong(), auth).andExpect(status().isOk());

        String codeHigh = sortToken + "-HIGH";
        Map<String, Object> reqHigh = createPayload(codeHigh, "Bộ Đề Cao " + sortToken, hsaId,
                List.of(e1.examId, e2.examId));
        reqHigh.put("listPriceVnd", 250_000L);
        JsonNode createdHigh = createBundle(reqHigh, auth);
        UUID bundleHigh = UUID.fromString(createdHigh.path("id").asText());
        performPublish(bundleHigh, createdHigh.path("revision").asLong(), auth).andExpect(status().isOk());

        // KM tự động đảo ngược thứ tự giá: bundleLow 200k - 20k = 180k, bundleHigh 250k - 100k = 150k.
        createPromo(autoPromoPayload("Giảm Low 20k", 20_000L, "BUNDLE",
                Map.of("bundleIds", List.of(bundleLow))), auth);
        JsonNode highPromo = createPromo(autoPromoPayload("Giảm High 100k", 100_000L, "BUNDLE",
                Map.of("bundleIds", List.of(bundleHigh))), auth);
        UUID highPromoId = UUID.fromString(highPromo.path("id").asText());

        mockMvc.perform(get(STOREFRONT_BASE + "/products")
                        .param("format", "BUNDLE")
                        .param("q", sortToken)
                        .param("sort", "PRICE_ASC")
                        .header("X-Forwarded-For", newClientIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].code").value(codeHigh))
                .andExpect(jsonPath("$.items[0].price").value(150_000))
                .andExpect(jsonPath("$.items[1].code").value(codeLow))
                .andExpect(jsonPath("$.items[1].price").value(180_000));

        mockMvc.perform(get(STOREFRONT_BASE + "/products")
                        .param("format", "BUNDLE")
                        .param("q", sortToken)
                        .param("sort", "PRICE_DESC")
                        .header("X-Forwarded-For", newClientIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].code").value(codeLow))
                .andExpect(jsonPath("$.items[0].price").value(180_000))
                .andExpect(jsonPath("$.items[1].code").value(codeHigh))
                .andExpect(jsonPath("$.items[1].price").value(150_000));

        // R1-29: PATCH cập nhật bundleIds khi chưa dùng -> 200; từ chối bundleIds trên ALL/EXAM_KIND/EXAM -> 400.
        JsonNode editable = createPromo(autoPromoPayload("Promo Patch Target", 15_000L, "BUNDLE",
                Map.of("bundleIds", List.of(bundleLow))), auth);
        UUID editableId = UUID.fromString(editable.path("id").asText());
        long editableRev = editable.path("revision").asLong();
        String patchedJson = mockMvc.perform(patch(PROMO_BASE + "/" + editableId)
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "expectedRevision", editableRev,
                                "bundleIds", List.of(bundleHigh)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopeItems.length()").value(1))
                .andExpect(jsonPath("$.scopeItems[0].id").value(bundleHigh.toString()))
                .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(patchedJson).path("revision").asLong()).isGreaterThan(editableRev);

        JsonNode allPromo = createPromo(autoPromoPayload("Promo All", 5_000L, "ALL", Map.of()), auth);
        JsonNode kindPromo = createPromo(autoPromoPayload("Promo Kind", 5_000L, "EXAM_KIND",
                Map.of("examKindIds", List.of(hsaId))), auth);
        JsonNode examPromo = createPromo(autoPromoPayload("Promo Exam", 5_000L, "EXAM",
                Map.of("examIds", List.of(e1.examId))), auth);
        for (JsonNode nonBundle : List.of(allPromo, kindPromo, examPromo)) {
            mockMvc.perform(patch(PROMO_BASE + "/" + nonBundle.path("id").asText())
                            .with(auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of(
                                    "expectedRevision", nonBundle.path("revision").asLong(),
                                    "bundleIds", List.of(bundleLow)))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("bundleIds"));
        }

        // R1-29: Đã có lượt dùng (in-use) thì PATCH đổi bundleIds phải trả 409 PROMOTION_IN_USE.
        String freeVoucherCode = "VUSE" + shortToken();
        Map<String, Object> inUseVoucherReq = autoPromoPayload("Voucher In Use", 100L, "BUNDLE",
                Map.of("bundleIds", List.of(bundleLow)));
        inUseVoucherReq.put("kind", "VOUCHER");
        inUseVoucherReq.put("code", freeVoucherCode);
        inUseVoucherReq.put("discountType", "PERCENT");
        inUseVoucherReq.put("audience", "ALL");
        inUseVoucherReq.put("combinable", false);
        JsonNode inUseVoucher = createPromo(inUseVoucherReq, auth);
        UUID inUsePromoId = UUID.fromString(inUseVoucher.path("id").asText());
        long inUseRev = inUseVoucher.path("revision").asLong();

        UUID buyerId = createActiveLearner();
        mockMvc.perform(post("/api/v1/me/orders")
                        .with(learner(buyerId))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", codeLow,
                                "voucherCode", freeVoucherCode,
                                "expectedAccountId", buyerId,
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 0L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.state").value("FULFILLED"));

        mockMvc.perform(patch(PROMO_BASE + "/" + inUsePromoId)
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "expectedRevision", inUseRev,
                                "bundleIds", List.of(bundleHigh)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROMOTION_IN_USE"));

        // R1-29 & R1-37: price-preview XOR (thiếu cả hai -> 400 REQUIRED; có cả hai -> 400 INVALID).
        mockMvc.perform(post(PROMO_BASE + "/price-preview")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("promotionId", highPromoId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("examId"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("REQUIRED"));

        mockMvc.perform(post(PROMO_BASE + "/price-preview")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "examId", e1.examId,
                                "bundleId", bundleHigh,
                                "promotionId", highPromoId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("bundleId"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("INVALID"));
    }

    private Map<String, Object> autoPromoPayload(String name, long value, String scope, Map<String, Object> extra) {
        Instant now = Instant.now();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("kind", "AUTO");
        map.put("name", name);
        map.put("discountType", "AMOUNT");
        map.put("discountValue", value);
        map.put("startsAt", now.minus(1, ChronoUnit.HOURS).toString());
        map.put("endsAt", now.plus(2, ChronoUnit.DAYS).toString());
        map.put("scope", scope);
        map.putAll(extra);
        return map;
    }

    private JsonNode createPromo(Map<String, Object> body, org.springframework.test.web.servlet.request.RequestPostProcessor auth)
            throws Exception {
        String res = mockMvc.perform(post(PROMO_BASE)
                        .with(auth)
                        .header("Idempotency-Key", "k-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = mapper.readTree(res);
        promoIds.add(UUID.fromString(node.path("id").asText()));
        return node;
    }

    private RequestPostProcessor learner(UUID userId) {
        return testSecuritySupport.apply(new SimulatedPrincipal(userId, "learner." + userId, "STUDENT", false, List.of()));
    }

    private UUID createActiveLearner() {
        UUID userId = UUID.randomUUID();
        Long seq = jdbc.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        String username = "bdl." + userId.toString().substring(0, 8);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO identity.user_account
                    (id, code, username, username_normalized, full_name, email, email_normalized,
                     source, status, must_change_password, deleted_at, created_at, created_by, updated_at, updated_by, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, 2, false, NULL, ?, ?, ?, ?, 0)
                """, userId, String.format("U%06d", seq), username, username, "Bundle Learner",
                userId + "@test.local", userId + "@test.local", now, userId, now, userId);
        learnerIds.add(userId);
        return userId;
    }
}
