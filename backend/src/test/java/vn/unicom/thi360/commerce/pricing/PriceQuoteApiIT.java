package vn.unicom.thi360.commerce.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.pricing.PricingTestClockConfig.MutableClock;
import vn.unicom.thi360.commerce.promotion.application.PromotionRedemptionService;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;
import vn.unicom.thi360.commerce.promotion.domain.Promotion;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudience;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudienceType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScopeItem;
import vn.unicom.thi360.commerce.promotion.domain.PromotionStatus;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionAudienceRepository;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionScopeItemRepository;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.security.TestSecurity.SimulatedPrincipal;

/**
 * Integration tests for {@code POST /api/v1/me/price-quote} covering AC-4, AC-9, AC-10, AC-11, AC-12, AC-13, AC-14,
 * AC-34, AC-35, and AC-36.
 */
@Import(PricingTestClockConfig.class)
@TestPropertySource(properties = "thi360.commerce.vnpay-min-amount=5000")
class PriceQuoteApiIT extends AbstractIntegrationTest {

    private static final String QUOTE_PATH = "/api/v1/me/price-quote";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private PromotionScopeItemRepository scopeItemRepository;
    @Autowired private PromotionAudienceRepository audienceRepository;
    @Autowired private PromotionRedemptionService redemptionService;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private MutableClock clock;

    private final List<UUID> userIds = new ArrayList<>();
    private final List<UUID> classIds = new ArrayList<>();
    private final List<UUID> examIds = new ArrayList<>();
    private final List<UUID> promotionIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        clock.reset();
        for (UUID userId : userIds) {
            redisTemplate.delete(VoucherQuoteRateLimiter.KEY_PREFIX + userId);
        }
        for (UUID promoId : promotionIds) {
            jdbc.update("DELETE FROM commerce.promotion_change_log WHERE promotion_id = ?", promoId);
            jdbc.update("DELETE FROM commerce.promotion_redemption WHERE promotion_id = ?", promoId);
            jdbc.update("DELETE FROM commerce.promotion WHERE id = ?", promoId);
        }
        promotionIds.clear();
        for (UUID examId : examIds) {
            jdbc.update("DELETE FROM commerce.storefront_product WHERE exam_id = ?", examId);
        }
        examIds.clear();
        for (UUID classId : classIds) {
            jdbc.update("DELETE FROM classroom.class_member WHERE class_id = ?", classId);
            jdbc.update("DELETE FROM classroom.class_room WHERE id = ?", classId);
        }
        classIds.clear();
        for (UUID userId : userIds) {
            jdbc.update("DELETE FROM classroom.class_member WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM identity.user_account WHERE id = ?", userId);
        }
        userIds.clear();
    }

    @Test
    void unusableCodesLookTheSame() throws Exception {
        UUID userId = createLearner(2, false);
        TestProduct product = insertOnSaleProduct("HSA", 200_000L, 3, 1);
        Instant now = Instant.now();

        Promotion upcoming = saveVoucher("UPCOMING" + shortToken(), DiscountType.AMOUNT, 20_000L, null,
                now.plus(2, ChronoUnit.HOURS), now.plus(2, ChronoUnit.DAYS), null, null, false,
                PromotionScope.ALL, PromotionAudienceType.ALL, PromotionStatus.ACTIVE, null);

        Promotion stopped = saveVoucher("STOPPED" + shortToken(), DiscountType.AMOUNT, 20_000L, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.DAYS), null, null, false,
                PromotionScope.ALL, PromotionAudienceType.ALL, PromotionStatus.STOPPED, null);

        Promotion deleted = saveVoucher("DELETED" + shortToken(), DiscountType.AMOUNT, 20_000L, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.DAYS), null, null, false,
                PromotionScope.ALL, PromotionAudienceType.ALL, PromotionStatus.ACTIVE, now.minusSeconds(60));

        List<String> codes = List.of(
                "UNKNOWN" + shortToken(),
                upcoming.getCode(),
                stopped.getCode(),
                deleted.getCode());

        ObjectNode baseline = null;
        for (String voucherCode : codes) {
            MvcResult res = mockMvc.perform(post(QUOTE_PATH)
                            .with(learner(userId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(quoteBody(product.code(), voucherCode, null, null)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("VOUCHER_INVALID"))
                    .andReturn();
            ObjectNode body = (ObjectNode) mapper.readTree(res.getResponse().getContentAsString());
            assertThat(body.path("traceId").asText()).isNotBlank();
            body.remove("traceId");
            if (baseline == null) {
                baseline = body;
            } else {
                assertThat(body).as("unusable voucher response for %s", voucherCode).isEqualTo(baseline);
            }
        }
    }

    @Test
    void specificVoucherErrors() throws Exception {
        UUID userId = createLearner(2, false);
        UUID otherUser = createLearner(2, false);
        TestProduct product = insertOnSaleProduct("HSA", 200_000L, 3, 1);
        UUID qdaKindId = kindId("QDA");
        Instant now = Instant.now();

        Promotion expired = saveVoucher("EXP" + shortToken(), DiscountType.AMOUNT, 20_000L, null,
                now.minus(2, ChronoUnit.DAYS), now.minus(1, ChronoUnit.MINUTES), null, null, false,
                PromotionScope.ALL, PromotionAudienceType.ALL, PromotionStatus.ACTIVE, null);

        Promotion exhausted = saveVoucher("EXH" + shortToken(), DiscountType.AMOUNT, 20_000L, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS), 1, null, false,
                PromotionScope.ALL, PromotionAudienceType.ALL, PromotionStatus.ACTIVE, null);
        redemptionService.reserve(exhausted.getId(), otherUser, UUID.randomUUID(), 20_000L);

        Promotion userLimited = saveVoucher("ULM" + shortToken(), DiscountType.AMOUNT, 20_000L, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS), 10, 1, false,
                PromotionScope.ALL, PromotionAudienceType.ALL, PromotionStatus.ACTIVE, null);
        redemptionService.reserve(userLimited.getId(), userId, UUID.randomUUID(), 20_000L);

        Promotion wrongScope = saveVoucher("SCP" + shortToken(), DiscountType.AMOUNT, 20_000L, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS), null, null, false,
                PromotionScope.EXAM_KIND, PromotionAudienceType.ALL, PromotionStatus.ACTIVE, null);
        scopeItemRepository.saveAndFlush(PromotionScopeItem.forExamKind(Ids.newId(), wrongScope.getId(), qdaKindId));

        Promotion restricted = saveVoucher("ELG" + shortToken(), DiscountType.AMOUNT, 20_000L, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS), null, null, false,
                PromotionScope.ALL, PromotionAudienceType.SELECTED, PromotionStatus.ACTIVE, null);
        audienceRepository.saveAndFlush(PromotionAudience.forUser(Ids.newId(), restricted.getId(), otherUser));

        assertQuoteError(userId, product.code(), expired.getCode(), "VOUCHER_EXPIRED");
        assertQuoteError(userId, product.code(), exhausted.getCode(), "VOUCHER_EXHAUSTED");
        assertQuoteError(userId, product.code(), userLimited.getCode(), "VOUCHER_USER_LIMIT");
        assertQuoteError(userId, product.code(), wrongScope.getCode(), "VOUCHER_NOT_APPLICABLE");
        assertQuoteError(userId, product.code(), restricted.getCode(), "VOUCHER_NOT_ELIGIBLE");
    }

    @Test
    void scopeRules() throws Exception {
        UUID userId = createLearner(2, false);
        TestProduct hsaProduct = insertOnSaleProduct("HSA", 200_000L, 3, 1);
        TestProduct qdaProduct = insertOnSaleProduct("QDA", 200_000L, 3, 1);
        Instant now = Instant.now();

        Promotion allPromo = saveVoucher("ALL" + shortToken(), DiscountType.AMOUNT, 10_000L, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS), null, null, false,
                PromotionScope.ALL, PromotionAudienceType.ALL, PromotionStatus.ACTIVE, null);

        Promotion kindPromo = saveVoucher("KND" + shortToken(), DiscountType.AMOUNT, 15_000L, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS), null, null, false,
                PromotionScope.EXAM_KIND, PromotionAudienceType.ALL, PromotionStatus.ACTIVE, null);
        scopeItemRepository.saveAndFlush(
                PromotionScopeItem.forExamKind(Ids.newId(), kindPromo.getId(), hsaProduct.examKindItemId()));

        Promotion examPromo = saveVoucher("EXM" + shortToken(), DiscountType.AMOUNT, 25_000L, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS), null, null, false,
                PromotionScope.EXAM, PromotionAudienceType.ALL, PromotionStatus.ACTIVE, null);
        scopeItemRepository.saveAndFlush(
                PromotionScopeItem.forExam(Ids.newId(), examPromo.getId(), qdaProduct.examId()));

        assertQuoteSuccess(userId, hsaProduct.code(), allPromo.getCode(), 190_000L);
        assertQuoteSuccess(userId, qdaProduct.code(), allPromo.getCode(), 190_000L);

        assertQuoteSuccess(userId, hsaProduct.code(), kindPromo.getCode(), 185_000L);
        assertQuoteError(userId, qdaProduct.code(), kindPromo.getCode(), "VOUCHER_NOT_APPLICABLE");

        assertQuoteSuccess(userId, qdaProduct.code(), examPromo.getCode(), 175_000L);
        assertQuoteError(userId, hsaProduct.code(), examPromo.getCode(), "VOUCHER_NOT_APPLICABLE");
    }

    /**
     * AC-12: Pins temporary CM-2 behavior where {@link ProvisionalPurchaseHistory} always returns {@code false},
     * allowing {@code NEW_ACCOUNT} vouchers until CM-3a replaces {@link PurchaseHistory} with real order history.
     */
    @Test
    void newAccountUsesProvisionalHistory() throws Exception {
        UUID userId = createLearner(2, false);
        TestProduct product = insertOnSaleProduct("HSA", 200_000L, 3, 1);
        Instant now = Instant.now();

        Promotion newAccountVoucher = saveVoucher("NEW" + shortToken(), DiscountType.AMOUNT, 35_000L, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS), null, null, false,
                PromotionScope.ALL, PromotionAudienceType.NEW_ACCOUNT, PromotionStatus.ACTIVE, null);

        JsonNode quote = assertQuoteSuccess(userId, product.code(), newAccountVoucher.getCode(), 165_000L);
        assertThat(quote.path("discountApplied").asText()).isEqualTo("VOUCHER_ONLY");
        assertThat(quote.path("voucher").path("code").asText()).isEqualTo(newAccountVoucher.getCode());
    }

    @Test
    void selectedAudienceFollowsCurrentClass() throws Exception {
        UUID directUser = createLearner(2, false);
        UUID classUser = createLearner(2, false);
        UUID outsider = createLearner(2, false);
        UUID classId = createClassRoom();
        addClassMember(classId, classUser);

        TestProduct product = insertOnSaleProduct("HSA", 200_000L, 3, 1);
        Instant now = Instant.now();
        Promotion selectedVoucher = saveVoucher("SEL" + shortToken(), DiscountType.AMOUNT, 30_000L, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS), null, null, false,
                PromotionScope.ALL, PromotionAudienceType.SELECTED, PromotionStatus.ACTIVE, null);
        audienceRepository.saveAndFlush(PromotionAudience.forUser(Ids.newId(), selectedVoucher.getId(), directUser));
        audienceRepository.saveAndFlush(PromotionAudience.forClass(Ids.newId(), selectedVoucher.getId(), classId));

        assertQuoteSuccess(directUser, product.code(), selectedVoucher.getCode(), 170_000L);
        assertQuoteSuccess(classUser, product.code(), selectedVoucher.getCode(), 170_000L);
        assertQuoteError(outsider, product.code(), selectedVoucher.getCode(), "VOUCHER_NOT_ELIGIBLE");

        jdbc.update("DELETE FROM classroom.class_member WHERE class_id = ? AND user_id = ?", classId, classUser);
        assertQuoteError(classUser, product.code(), selectedVoucher.getCode(), "VOUCHER_NOT_ELIGIBLE");
    }

    @Test
    void validityWindowBoundaries() throws Exception {
        UUID userId = createLearner(2, false);
        TestProduct product = insertOnSaleProduct("HSA", 200_000L, 3, 1);
        Instant startsAt = Instant.parse("2026-11-22T10:00:00Z");
        Instant endsAt = Instant.parse("2026-11-22T12:00:00Z");

        Promotion voucher = saveVoucher("BND" + shortToken(), DiscountType.AMOUNT, 20_000L, null,
                startsAt, endsAt, null, null, false,
                PromotionScope.ALL, PromotionAudienceType.ALL, PromotionStatus.ACTIVE, null);

        clock.fixAt(startsAt.minusSeconds(1));
        assertQuoteError(userId, product.code(), voucher.getCode(), "VOUCHER_INVALID");

        clock.fixAt(startsAt);
        assertQuoteSuccess(userId, product.code(), voucher.getCode(), 180_000L);

        clock.fixAt(endsAt.minusSeconds(1));
        assertQuoteSuccess(userId, product.code(), voucher.getCode(), 180_000L);

        clock.fixAt(endsAt);
        assertQuoteError(userId, product.code(), voucher.getCode(), "VOUCHER_EXPIRED");
    }

    @Test
    void quoteContentAndChangedFlag() throws Exception {
        UUID userId = createLearner(2, false);
        TestProduct product = insertOnSaleProduct("HSA", 200_000L, 5, 3);
        Instant now = Instant.now();
        Instant endsAt = now.plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

        Promotion autoPromo = saveAuto("Auto 20% " + shortToken(), DiscountType.PERCENT, 20, null,
                now.minus(1, ChronoUnit.HOURS), endsAt, null, PromotionScope.EXAM, PromotionStatus.ACTIVE);
        scopeItemRepository.saveAndFlush(PromotionScopeItem.forExam(Ids.newId(), autoPromo.getId(), product.examId()));

        Promotion stackVoucher = saveVoucher("STK" + shortToken(), DiscountType.PERCENT, 10, null,
                now.minus(1, ChronoUnit.HOURS), endsAt, null, null, true,
                PromotionScope.ALL, PromotionAudienceType.ALL, PromotionStatus.ACTIVE, null);

        // 200k -> auto 40k (160k) -> combinable 10% 16k -> 144k
        JsonNode exactSeen = quoteSuccess(userId, product.code(), stackVoucher.getCode(), 3, 144_000L);
        assertThat(exactSeen.path("productCode").asText()).isEqualTo(product.code());
        assertThat(exactSeen.path("productName").asText()).isEqualTo(product.name());
        assertThat(exactSeen.path("versionNo").asInt()).isEqualTo(3);
        assertThat(exactSeen.path("attemptsGranted").asInt()).isEqualTo(5);
        assertThat(exactSeen.path("listPrice").asLong()).isEqualTo(200_000L);
        assertThat(exactSeen.path("promotion").path("id").asText()).isEqualTo(autoPromo.getId().toString());
        assertThat(exactSeen.path("promotion").path("discountVnd").asLong()).isEqualTo(40_000L);
        assertThat(exactSeen.path("voucher").path("id").asText()).isEqualTo(stackVoucher.getId().toString());
        assertThat(exactSeen.path("voucher").path("discountVnd").asLong()).isEqualTo(16_000L);
        assertThat(exactSeen.path("discountApplied").asText()).isEqualTo("COMBINED");
        assertThat(exactSeen.path("voucherNotBetter").asBoolean()).isFalse();
        assertThat(exactSeen.path("finalPrice").asLong()).isEqualTo(144_000L);
        assertThat(exactSeen.path("changed").asBoolean()).isFalse();

        JsonNode versionChanged = quoteSuccess(userId, product.code(), stackVoucher.getCode(), 2, 144_000L);
        assertThat(versionChanged.path("changed").asBoolean()).isTrue();

        JsonNode priceChanged = quoteSuccess(userId, product.code(), stackVoucher.getCode(), 3, 160_000L);
        assertThat(priceChanged.path("changed").asBoolean()).isTrue();

        // AC-4: non-combinable worse voucher must not populate voucher in response
        Promotion worseVoucher = saveVoucher("WRS" + shortToken(), DiscountType.AMOUNT, 10_000L, null,
                now.minus(1, ChronoUnit.HOURS), endsAt, null, null, false,
                PromotionScope.ALL, PromotionAudienceType.ALL, PromotionStatus.ACTIVE, null);
        JsonNode autoWon = quoteSuccess(userId, product.code(), worseVoucher.getCode(), null, null);
        assertThat(autoWon.path("discountApplied").asText()).isEqualTo("PROMOTION_ONLY");
        assertThat(autoWon.path("voucher").isNull()).isTrue();
        assertThat(autoWon.path("voucherNotBetter").asBoolean()).isTrue();
        assertThat(autoWon.path("finalPrice").asLong()).isEqualTo(160_000L);
    }

    @Test
    void quoteUnknownAndDiscontinued() throws Exception {
        UUID userId = createLearner(2, false);
        TestProduct discontinued = insertProduct("HSA", "DISCONTINUED", 150_000L, 2, 1);

        mockMvc.perform(post(QUOTE_PATH)
                        .with(learner(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody("UNKNOWN-" + shortToken(), null, null, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));

        mockMvc.perform(post(QUOTE_PATH)
                        .with(learner(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody(discontinued.code(), null, null, null)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("PRODUCT_DISCONTINUED"))
                .andExpect(jsonPath("$.product.code").value(discontinued.code()))
                .andExpect(jsonPath("$.product.name").value(discontinued.name()));
    }

    @Test
    void quoteRequiresActiveExamAccount() throws Exception {
        TestProduct product = insertOnSaleProduct("HSA", 200_000L, 3, 1);
        UUID activeLearner = createLearner(2, false);
        UUID inactiveLearner = createLearner(3, false);
        UUID deletedLearner = createLearner(2, true);
        UUID manageAccount = createLearner(2, false);
        String body = quoteBody(product.code(), null, null, null);

        mockMvc.perform(post(QUOTE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mockMvc.perform(post(QUOTE_PATH)
                        .with(asUser(manageAccount, "manage." + shortToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post(QUOTE_PATH)
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post(QUOTE_PATH)
                        .with(learner(inactiveLearner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post(QUOTE_PATH)
                        .with(learner(deletedLearner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post(QUOTE_PATH)
                        .with(learner(activeLearner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalPrice").value(200_000L));
    }

    @Test
    void minimumPaymentFloorAndZeroingThroughHttp() throws Exception {
        UUID userId = createLearner(2, false);
        TestProduct autoProduct = insertOnSaleProduct("HSA", 10_000L, 3, 1);
        TestProduct voucherProduct = insertOnSaleProduct("QDA", 10_000L, 3, 1);
        Instant now = Instant.now();

        // Auto -7,000 on 10,000 VND HSA exam clamps at vnpayMinAmount = 5,000 VND
        Promotion autoPromo = saveAuto("E26 Auto Floor " + shortToken(), DiscountType.AMOUNT, 7_000L, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS), null,
                PromotionScope.EXAM, PromotionStatus.ACTIVE);
        scopeItemRepository.saveAndFlush(PromotionScopeItem.forExam(Ids.newId(), autoPromo.getId(), autoProduct.examId()));

        JsonNode flooredAuto = quoteSuccess(userId, autoProduct.code(), null, null, null);
        assertThat(flooredAuto.path("discountApplied").asText()).isEqualTo("PROMOTION_ONLY");
        assertThat(flooredAuto.path("finalPrice").asLong()).isEqualTo(5_000L);
        assertThat(flooredAuto.path("promotion").path("discountVnd").asLong()).isEqualTo(5_000L);

        // Voucher dropping 10,000 -> 4,999 becomes 0 VND; 10,000 -> 5,000 stays 5,000 VND
        Promotion belowMinVoucher = saveVoucher("E26Z" + shortToken(), DiscountType.AMOUNT, 5_001L, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS), null, null, false,
                PromotionScope.EXAM, PromotionAudienceType.ALL, PromotionStatus.ACTIVE, null);
        scopeItemRepository.saveAndFlush(
                PromotionScopeItem.forExam(Ids.newId(), belowMinVoucher.getId(), voucherProduct.examId()));

        JsonNode zeroedQuote = quoteSuccess(userId, voucherProduct.code(), belowMinVoucher.getCode(), null, null);
        assertThat(zeroedQuote.path("discountApplied").asText()).isEqualTo("VOUCHER_ONLY");
        assertThat(zeroedQuote.path("finalPrice").asLong()).isZero();
        assertThat(zeroedQuote.path("voucher").path("discountVnd").asLong()).isEqualTo(10_000L);

        Promotion exactMinVoucher = saveVoucher("E26M" + shortToken(), DiscountType.AMOUNT, 5_000L, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS), null, null, false,
                PromotionScope.EXAM, PromotionAudienceType.ALL, PromotionStatus.ACTIVE, null);
        scopeItemRepository.saveAndFlush(
                PromotionScopeItem.forExam(Ids.newId(), exactMinVoucher.getId(), voucherProduct.examId()));

        JsonNode exactMinQuote = quoteSuccess(userId, voucherProduct.code(), exactMinVoucher.getCode(), null, null);
        assertThat(exactMinQuote.path("discountApplied").asText()).isEqualTo("VOUCHER_ONLY");
        assertThat(exactMinQuote.path("finalPrice").asLong()).isEqualTo(5_000L);
        assertThat(exactMinQuote.path("voucher").path("discountVnd").asLong()).isEqualTo(5_000L);

        // PriceQuoteRequest @Size(max = 100) validation on code & voucherCode (R1-31)
        mockMvc.perform(post(QUOTE_PATH)
                        .with(learner(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody("X".repeat(101), "V".repeat(101), null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode assertQuoteSuccess(UUID userId, String productCode, String voucherCode, long expectedFinalPrice)
            throws Exception {
        JsonNode body = quoteSuccess(userId, productCode, voucherCode, null, null);
        assertThat(body.path("finalPrice").asLong()).isEqualTo(expectedFinalPrice);
        return body;
    }

    private JsonNode quoteSuccess(
            UUID userId, String productCode, String voucherCode, Integer seenVersionNo, Long seenPrice)
            throws Exception {
        MvcResult res = mockMvc.perform(post(QUOTE_PATH)
                        .with(learner(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody(productCode, voucherCode, seenVersionNo, seenPrice)))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(res.getResponse().getContentAsString());
    }

    private void assertQuoteError(UUID userId, String productCode, String voucherCode, String expectedCode)
            throws Exception {
        mockMvc.perform(post(QUOTE_PATH)
                        .with(learner(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody(productCode, voucherCode, null, null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(expectedCode));
    }

    private String quoteBody(String code, String voucherCode, Integer seenVersionNo, Long seenPrice)
            throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        if (voucherCode != null) {
            payload.put("voucherCode", voucherCode);
        }
        if (seenVersionNo != null) {
            payload.put("seenVersionNo", seenVersionNo);
        }
        if (seenPrice != null) {
            payload.put("seenPrice", seenPrice);
        }
        return mapper.writeValueAsString(payload);
    }

    private RequestPostProcessor learner(UUID userId) {
        return testSecuritySupport.apply(new SimulatedPrincipal(userId, "learner." + userId, "STUDENT", false, List.of()));
    }

    private UUID createLearner(int statusCode, boolean deleted) {
        UUID userId = UUID.randomUUID();
        Long seq = jdbc.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        String username = "pq." + userId.toString().substring(0, 8);
        Timestamp now = Timestamp.from(Instant.now());
        Timestamp deletedAt = deleted ? now : null;
        jdbc.update("""
                INSERT INTO identity.user_account
                    (id, code, username, username_normalized, full_name, email, email_normalized,
                     source, status, must_change_password, deleted_at, created_at, created_by, updated_at, updated_by, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, false, ?, ?, ?, ?, ?, 0)
                """, userId, String.format("U%06d", seq), username, username, "Quote Learner",
                userId + "@test.local", userId + "@test.local", statusCode, deletedAt, now, userId, now, userId);
        userIds.add(userId);
        return userId;
    }

    private UUID createClassRoom() {
        UUID classId = UUID.randomUUID();
        String code = "CLS" + shortToken();
        jdbc.update("""
                INSERT INTO classroom.class_room
                    (id, code, code_normalized, name, status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 1, now(), now(), 0)
                """, classId, code, code.toLowerCase(), "Class " + code);
        classIds.add(classId);
        return classId;
    }

    private void addClassMember(UUID classId, UUID userId) {
        jdbc.update("""
                INSERT INTO classroom.class_member (class_id, user_id, joined_at, added_by)
                VALUES (?, ?, now(), ?)
                """, classId, userId, userId);
    }

    private UUID kindId(String kindCode) {
        return catalogLookup.findActiveItem("EXAM", kindCode).orElseThrow().id();
    }

    private TestProduct insertOnSaleProduct(String kindCode, long listPriceVnd, int attemptsGranted, int versionNo) {
        return insertProduct(kindCode, "ON_SALE", listPriceVnd, attemptsGranted, versionNo);
    }

    private TestProduct insertProduct(
            String kindCode, String saleState, long listPriceVnd, int attemptsGranted, int versionNo) {
        UUID examId = UUID.randomUUID();
        UUID examKindItemId = kindId(kindCode);
        String code = "PQ" + shortToken();
        String name = "Exam " + code;
        jdbc.update("""
                INSERT INTO commerce.storefront_product
                    (id, exam_id, code, code_normalized, version_no, sale_state, name, search_text, exam_item_id,
                     kind_code, scope, question_count, question_count_max, duration_minutes, duration_minutes_max,
                     build_mode, list_price_vnd, attempts_granted, card, detail, projection_hash, published_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'FULL', 50, 50, 75, 75, 'STANDARD', ?, ?, '{}'::jsonb,
                        '{}'::jsonb, 'test', now(), now())
                """, UUID.randomUUID(), examId, code, code.toLowerCase(), versionNo, saleState, name,
                name.toLowerCase(), examKindItemId, kindCode, listPriceVnd, attemptsGranted);
        examIds.add(examId);
        return new TestProduct(examId, examKindItemId, code, name);
    }

    private Promotion saveAuto(
            String name, DiscountType discountType, long discountValue, Long maxDiscountVnd,
            Instant startsAt, Instant endsAt, Integer totalLimit, PromotionScope scope, PromotionStatus status) {
        UUID id = Ids.newId();
        promotionIds.add(id);
        return promotionRepository.saveAndFlush(Promotion.auto(
                id, name, null, discountType, discountValue, maxDiscountVnd, startsAt, endsAt, totalLimit,
                scope, status, Instant.now(), null, null));
    }

    private Promotion saveVoucher(
            String code, DiscountType discountType, long discountValue, Long maxDiscountVnd,
            Instant startsAt, Instant endsAt, Integer totalLimit, Integer perUserLimit, boolean combinable,
            PromotionScope scope, PromotionAudienceType audience, PromotionStatus status, Instant deletedAt) {
        UUID id = Ids.newId();
        promotionIds.add(id);
        Promotion voucher = Promotion.voucher(
                id, "Voucher " + code, null, code, discountType, discountValue, maxDiscountVnd,
                startsAt, endsAt, totalLimit, perUserLimit, combinable, scope, audience, status,
                Instant.now(), null, null);
        if (deletedAt != null) {
            voucher.softDelete(deletedAt, null, null);
        }
        return promotionRepository.saveAndFlush(voucher);
    }

    private static String shortToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private record TestProduct(UUID examId, UUID examKindItemId, String code, String name) {
    }
}
