package vn.unicom.thi360.mobilecontract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpaySigner;
import vn.unicom.thi360.commerce.pricing.VoucherQuoteRateLimiter;
import vn.unicom.thi360.commerce.promotion.application.PromotionRedemptionService;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;
import vn.unicom.thi360.commerce.promotion.domain.Promotion;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudience;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudienceType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScopeItem;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionAudienceRepository;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionScopeItemRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.shared.ids.Ids;

public class MobileCommerceFixturesIT extends AbstractIntegrationTest {

    public static final class Fixtures {
        public static final Set<String> SET = Set.of(
                "api/price-quote-200.json",
                "api/price-quote-200-voucher.json",
                "api/price-quote-200-changed.json",
                "api/price-quote-404-product-not-found.json",
                "api/price-quote-410-product-discontinued.json",
                "api/price-quote-422-voucher-invalid.json",
                "api/price-quote-422-voucher-expired.json",
                "api/price-quote-422-voucher-exhausted.json",
                "api/price-quote-422-voucher-user-limit.json",
                "api/price-quote-422-voucher-not-applicable.json",
                "api/price-quote-422-voucher-not-eligible.json",
                "api/price-quote-429-rate-limited.json",
                "api/orders-create-201-redirect.json",
                "api/orders-create-201-reused.json",
                "api/orders-create-201-fulfilled.json",
                "api/orders-create-201-checking.json",
                "api/orders-create-409-price-changed.json",
                "api/orders-create-409-account-changed.json",
                "api/orders-create-409-product-unavailable.json",
                "api/orders-create-409-idempotency-key-reused.json",
                "api/orders-create-410-product-discontinued.json",
                "api/orders-create-422-voucher-invalid.json",
                "api/orders-create-422-payment-attempts-exceeded.json",
                "api/orders-list-200.json",
                "api/orders-list-200-empty.json",
                "api/orders-detail-200-pending-payment.json",
                "api/orders-detail-200-paid.json",
                "api/orders-detail-200-fulfilled.json",
                "api/orders-detail-200-replaced.json",
                "api/orders-detail-200-expired.json",
                "api/orders-detail-200-cancelled.json",
                "api/orders-detail-200-refunded.json",
                "api/orders-detail-200-payment-failed.json",
                "api/orders-detail-200-free-voucher.json",
                "api/orders-detail-200-duplicate-payment.json",
                "api/orders-detail-404-order-not-found.json",
                "api/orders-pay-200.json",
                "api/orders-pay-404-order-not-found.json",
                "api/orders-pay-404-product-not-found.json",
                "api/orders-pay-409-order-not-pending.json",
                "api/orders-pay-409-order-expired.json",
                "api/orders-pay-409-product-unavailable.json",
                "api/orders-pay-410-product-discontinued.json",
                "api/orders-pay-422-payment-attempts-exceeded.json",
                "api/orders-cancel-200.json",
                "api/orders-cancel-409-order-not-pending.json",
                "api/vnpay-return-200-succeeded.json",
                "api/vnpay-return-200-failed.json",
                "api/vnpay-return-200-pending.json",
                "api/vnpay-return-404-order-not-found.json",
                "api/vnpay-return-422-vnpay-signature-invalid.json");

        private Fixtures() {}
    }

    public static final Set<String> FIXTURES = Fixtures.SET;

    private static final Set<String> QUOTE_DYNAMIC = Set.of(
            "productCode",
            "productName",
            "$.voucher.id",
            "$.voucher.code",
            "$.voucher.name",
            "$.promotion.id",
            "$.promotion.name",
            "$.promotion.endsAt");

    private static final Set<String> ORDER_DYNAMIC = Set.of(
            "id",
            "code",
            "userId",
            "buyerUsername",
            "buyerFullName",
            "paymentUrl",
            "txnRef",
            "productCode",
            "productName",
            "examId",
            "examCode",
            "examName",
            "voucherId",
            "voucherCode",
            "replacedByOrderId",
            "replacedByOrderCode",
            "expiresAt",
            "paidAt",
            "fulfilledAt",
            "cancelledAt",
            "refundedAt",
            "createdAt",
            "updatedAt",
            "at",
            "vnpCreateDate",
            "vnpExpireDate",
            "vnpTransactionNo",
            "$.history[*].reason",
            "$.order.history[*].reason");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private PromotionScopeItemRepository scopeItemRepository;
    @Autowired private PromotionAudienceRepository audienceRepository;
    @Autowired private PromotionRedemptionService redemptionService;
    @Autowired private StringRedisTemplate redisTemplate;

    @MockitoSpyBean private EntitlementGrantService entitlementGrantService;

    private CommerceTestFixtures fixtures;
    private MobileFixtureCertifier certifier;
    private final List<UUID> rateLimitedUserIds = new ArrayList<>();
    private final List<UUID> extraBundleIds = new ArrayList<>();
    private final List<UUID> extraStorefrontIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        reset(entitlementGrantService);
        fixtures = new CommerceTestFixtures(jdbc, objectMapper, catalogLookup, promotionRepository, testSecuritySupport);
        certifier = new MobileFixtureCertifier(objectMapper);
        rateLimitedUserIds.clear();
        extraBundleIds.clear();
        extraStorefrontIds.clear();
    }

    @AfterEach
    void tearDown() {
        reset(entitlementGrantService);
        for (UUID userId : rateLimitedUserIds) {
            redisTemplate.delete(VoucherQuoteRateLimiter.KEY_PREFIX + userId);
            var keys = redisTemplate.keys(VoucherQuoteRateLimiter.KEY_PREFIX + "*" + userId);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
        rateLimitedUserIds.clear();
        fixtures.cleanup();
        for (UUID rowId : extraStorefrontIds) {
            jdbc.update("DELETE FROM commerce.storefront_product WHERE id = ?", rowId);
        }
        extraStorefrontIds.clear();
        for (UUID bundleId : extraBundleIds) {
            jdbc.update("DELETE FROM commerce.bundle WHERE id = ?", bundleId);
        }
        extraBundleIds.clear();
    }

    @Test
    void allCommerceContractFixturesMatchLiveResponses() throws Exception {
        certifyPriceQuoteFixtures();
        certifyOrderCreateListDetailPayCancelFixtures();
        certifyVnpayReturnFixtures();
        certifier.assertCertifiedExactly(FIXTURES);
    }

    private void certifyPriceQuoteFixtures() throws Exception {
        var buyer = fixtures.createLearner();
        var otherBuyer = fixtures.createLearner();
        var rlBuyer = fixtures.createLearner();
        rateLimitedUserIds.addAll(List.of(buyer.id(), otherBuyer.id(), rlBuyer.id()));

        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var discontinued = fixtures.insertProduct("HSA", "DISCONTINUED", 150_000L, 2, 1, List.of());
        UUID qdaKindId = catalogLookup.findActiveItem("EXAM", "QDA").orElseThrow().id();
        Instant now = Instant.now();

        Promotion validVoucher = fixtures.createVoucher(
                "MCV", DiscountType.AMOUNT, 40_000L, null, 10, 5, false, PromotionScope.ALL, PromotionAudienceType.ALL);
        Promotion expiredVoucher = fixtures.createVoucher(
                "EXP", DiscountType.AMOUNT, 20_000L, null,
                now.minus(2, ChronoUnit.DAYS), now.minus(1, ChronoUnit.MINUTES),
                null, null, false, PromotionScope.ALL, PromotionAudienceType.ALL);
        Promotion exhaustedVoucher = fixtures.createVoucher(
                "EXH", DiscountType.AMOUNT, 20_000L, null, 1, null, false, PromotionScope.ALL, PromotionAudienceType.ALL);
        var exhaustedOrder = fixtures.insertStubOrder(
                otherBuyer, product, OrderState.PAID, PaymentMethod.VNPAY, 180_000L, null, now);
        redemptionService.reserve(exhaustedVoucher.getId(), otherBuyer.id(), exhaustedOrder.id(), 20_000L);

        Promotion userLimitVoucher = fixtures.createVoucher(
                "ULM", DiscountType.AMOUNT, 20_000L, null, 10, 1, false, PromotionScope.ALL, PromotionAudienceType.ALL);
        var userLimitOrder = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 180_000L, null, now);
        redemptionService.reserve(userLimitVoucher.getId(), buyer.id(), userLimitOrder.id(), 20_000L);

        Promotion wrongScopeVoucher = fixtures.createVoucher(
                "SCP", DiscountType.AMOUNT, 20_000L, null, null, null, false,
                PromotionScope.EXAM_KIND, PromotionAudienceType.ALL);
        scopeItemRepository.saveAndFlush(PromotionScopeItem.forExamKind(Ids.newId(), wrongScopeVoucher.getId(), qdaKindId));

        Promotion notEligibleVoucher = fixtures.createVoucher(
                "ELG", DiscountType.AMOUNT, 20_000L, null, null, null, false,
                PromotionScope.ALL, PromotionAudienceType.SELECTED);
        audienceRepository.saveAndFlush(PromotionAudience.forUser(Ids.newId(), notEligibleVoucher.getId(), otherBuyer.id()));

        certifier.certify("api/price-quote-200.json", 200,
                postQuote(buyer, product.code(), null, null, null), QUOTE_DYNAMIC);
        certifier.certify("api/price-quote-200-voucher.json", 200,
                postQuote(buyer, product.code(), validVoucher.getCode(), null, null), QUOTE_DYNAMIC);
        certifier.certify("api/price-quote-200-changed.json", 200,
                postQuote(buyer, product.code(), null, 1, 180_000L), QUOTE_DYNAMIC);
        certifier.certify("api/price-quote-404-product-not-found.json", 404,
                postQuote(buyer, "NO-SUCH-" + CommerceTestFixtures.shortToken(), null, null, null), Set.of("traceId"));
        certifier.certify("api/price-quote-410-product-discontinued.json", 410,
                postQuote(buyer, discontinued.code(), null, null, null),
                Set.of("traceId", "$.product.code", "$.product.name"));

        certifier.certify("api/price-quote-422-voucher-invalid.json", 422,
                postQuote(buyer, product.code(), "NO-SUCH-VOUCHER", null, null), Set.of("traceId"));
        certifier.certify("api/price-quote-422-voucher-expired.json", 422,
                postQuote(buyer, product.code(), expiredVoucher.getCode(), null, null), Set.of("traceId"));
        certifier.certify("api/price-quote-422-voucher-exhausted.json", 422,
                postQuote(buyer, product.code(), exhaustedVoucher.getCode(), null, null), Set.of("traceId"));
        certifier.certify("api/price-quote-422-voucher-user-limit.json", 422,
                postQuote(buyer, product.code(), userLimitVoucher.getCode(), null, null), Set.of("traceId"));
        certifier.certify("api/price-quote-422-voucher-not-applicable.json", 422,
                postQuote(buyer, product.code(), wrongScopeVoucher.getCode(), null, null), Set.of("traceId"));
        certifier.certify("api/price-quote-422-voucher-not-eligible.json", 422,
                postQuote(buyer, product.code(), notEligibleVoucher.getCode(), null, null), Set.of("traceId"));

        for (int i = 0; i < 10; i++) {
            postQuote(rlBuyer, product.code(), validVoucher.getCode(), null, null);
        }
        certifier.certify("api/price-quote-429-rate-limited.json", 429,
                postQuote(rlBuyer, product.code(), validVoucher.getCode(), null, null), Set.of("traceId"));
    }

    private void certifyOrderCreateListDetailPayCancelFixtures() throws Exception {
        var buyer = fixtures.createLearner();
        var emptyBuyer = fixtures.createLearner();
        var freeBuyer1 = fixtures.createLearner();
        var freeBuyer2 = fixtures.createLearner();
        var exceededBuyer = fixtures.createLearner();

        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var unavailableBundle = insertUnavailableBundle("HSA", 200_000L, 3);
        var discontinued = fixtures.insertProduct("HSA", "DISCONTINUED", 200_000L, 3, 1, List.of());
        var freeVoucher = fixtures.createVoucher(
                "FREE", DiscountType.PERCENT, 100L, null, 10, 2, false, PromotionScope.ALL, PromotionAudienceType.ALL);

        String redirectKey = "ord-redir-" + UUID.randomUUID();
        Map<String, Object> redirectReq = Map.of(
                "code", product.code(),
                "expectedAccountId", buyer.id(),
                "expectedVersionNo", 1,
                "expectedAmountDue", 200_000L);
        MvcResult createRedirect = postOrder(buyer, redirectKey, redirectReq);
        certifier.certify("api/orders-create-201-redirect.json", 201, createRedirect, ORDER_DYNAMIC);
        JsonNode createdOrder = objectMapper.readTree(createRedirect.getResponse().getContentAsString()).path("order");
        String pendingOrderCode = createdOrder.path("code").asText();

        MvcResult createReused = postOrder(buyer, "ord-reuse-" + UUID.randomUUID(), redirectReq);
        certifier.certify("api/orders-create-201-reused.json", 201, createReused, ORDER_DYNAMIC);

        certifier.certify("api/orders-create-409-idempotency-key-reused.json", 409,
                postOrder(buyer, redirectKey, Map.of(
                        "code", product.code(),
                        "expectedAccountId", buyer.id(),
                        "expectedVersionNo", 1,
                        "expectedAmountDue", 199_000L)),
                Set.of("traceId"));

        certifier.certify("api/orders-create-409-price-changed.json", 409,
                postOrder(buyer, "ord-pc-" + UUID.randomUUID(), Map.of(
                        "code", product.code(),
                        "expectedAccountId", buyer.id(),
                        "expectedVersionNo", 1,
                        "expectedAmountDue", 150_000L)),
                Set.of("traceId", "$.quote.productCode", "$.quote.productName"));

        certifier.certify("api/orders-create-409-account-changed.json", 409,
                postOrder(buyer, "ord-ac-" + UUID.randomUUID(), Map.of(
                        "code", product.code(),
                        "expectedAccountId", UUID.randomUUID(),
                        "expectedVersionNo", 1,
                        "expectedAmountDue", 200_000L)),
                Set.of("traceId"));

        certifier.certify("api/orders-create-409-product-unavailable.json", 409,
                postOrder(buyer, "ord-unav-" + UUID.randomUUID(), Map.of(
                        "code", unavailableBundle.code(),
                        "expectedAccountId", buyer.id(),
                        "expectedVersionNo", 1,
                        "expectedAmountDue", 200_000L)),
                Set.of("traceId"));

        certifier.certify("api/orders-create-410-product-discontinued.json", 410,
                postOrder(buyer, "ord-disc-" + UUID.randomUUID(), Map.of(
                        "code", discontinued.code(),
                        "expectedAccountId", buyer.id(),
                        "expectedVersionNo", 1,
                        "expectedAmountDue", 200_000L)),
                Set.of("traceId", "$.product.code", "$.product.name"));

        certifier.certify("api/orders-create-422-voucher-invalid.json", 422,
                postOrder(buyer, "ord-vinv-" + UUID.randomUUID(), Map.of(
                        "code", product.code(),
                        "voucherCode", "INVALID-CODE",
                        "expectedAccountId", buyer.id(),
                        "expectedVersionNo", 1,
                        "expectedAmountDue", 200_000L)),
                Set.of("traceId"));

        AtomicBoolean failGrant = new AtomicBoolean(false);
        doAnswer(inv -> {
            if (failGrant.get()) {
                throw new IllegalStateException("Simulated transient grant failure");
            }
            return inv.callRealMethod();
        }).when(entitlementGrantService).grantForOrder(any());

        MvcResult createFulfilled = postOrder(freeBuyer1, "ord-free-ok-" + UUID.randomUUID(), Map.of(
                "code", product.code(),
                "voucherCode", freeVoucher.getCode(),
                "expectedAccountId", freeBuyer1.id(),
                "expectedVersionNo", 1,
                "expectedAmountDue", 0L));
        certifier.certify("api/orders-create-201-fulfilled.json", 201, createFulfilled, ORDER_DYNAMIC);
        String freeFulfilledCode = objectMapper.readTree(createFulfilled.getResponse().getContentAsString())
                .path("order").path("code").asText();

        failGrant.set(true);
        MvcResult createChecking = postOrder(freeBuyer2, "ord-free-chk-" + UUID.randomUUID(), Map.of(
                "code", product.code(),
                "voucherCode", freeVoucher.getCode(),
                "expectedAccountId", freeBuyer2.id(),
                "expectedVersionNo", 1,
                "expectedAmountDue", 0L));
        failGrant.set(false);
        certifier.certify("api/orders-create-201-checking.json", 201, createChecking, ORDER_DYNAMIC);

        var exceededProduct = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        MvcResult exceededCreated = postOrder(exceededBuyer, "ord-ex-1-" + UUID.randomUUID(), Map.of(
                "code", exceededProduct.code(),
                "expectedAccountId", exceededBuyer.id(),
                "expectedVersionNo", 1,
                "expectedAmountDue", 200_000L));
        JsonNode exceededOrderJson = objectMapper.readTree(exceededCreated.getResponse().getContentAsString()).path("order");
        UUID exceededOrderId = UUID.fromString(exceededOrderJson.path("id").asText());
        String exceededOrderCode = exceededOrderJson.path("code").asText();
        fixtures.insertStubPayment(new CommerceTestFixtures.StubOrder(exceededOrderId, exceededOrderCode),
                99, 200_000L, PaymentState.FAILED);

        certifier.certify("api/orders-create-422-payment-attempts-exceeded.json", 422,
                postOrder(exceededBuyer, "ord-ex-2-" + UUID.randomUUID(), Map.of(
                        "code", exceededProduct.code(),
                        "expectedAccountId", exceededBuyer.id(),
                        "expectedVersionNo", 1,
                        "expectedAmountDue", 200_000L)),
                Set.of("traceId"));

        certifier.certify("api/orders-pay-422-payment-attempts-exceeded.json", 422,
                mockMvc.perform(post("/api/v1/me/orders/" + exceededOrderCode + "/pay")
                                .with(fixtures.asLearner(exceededBuyer)))
                        .andReturn(),
                Set.of("traceId"));

        certifier.certify("api/orders-list-200.json", 200,
                mockMvc.perform(get("/api/v1/me/orders").with(fixtures.asLearner(buyer))).andReturn(),
                ORDER_DYNAMIC);
        certifier.certify("api/orders-list-200-empty.json", 200,
                mockMvc.perform(get("/api/v1/me/orders").with(fixtures.asLearner(emptyBuyer))).andReturn(),
                Set.of());

        certifier.certify("api/orders-detail-200-pending-payment.json", 200,
                mockMvc.perform(get("/api/v1/me/orders/" + pendingOrderCode).with(fixtures.asLearner(buyer))).andReturn(),
                ORDER_DYNAMIC);

        certifier.certify("api/orders-detail-200-free-voucher.json", 200,
                mockMvc.perform(get("/api/v1/me/orders/" + freeFulfilledCode).with(fixtures.asLearner(freeBuyer1))).andReturn(),
                ORDER_DYNAMIC);

        var detailBuyer = fixtures.createLearner();
        Instant now = Instant.now();

        var paidOrder = fixtures.insertStubOrder(
                detailBuyer, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, now.plusSeconds(900), now);
        fixtures.insertStubPayment(paidOrder, 1, 200_000L, PaymentState.SUCCEEDED);
        certifier.certify("api/orders-detail-200-paid.json", 200,
                mockMvc.perform(get("/api/v1/me/orders/" + paidOrder.code()).with(fixtures.asLearner(detailBuyer))).andReturn(),
                ORDER_DYNAMIC);

        var fulfilledOrder = fixtures.insertStubOrder(
                detailBuyer, product, OrderState.FULFILLED, PaymentMethod.VNPAY, 200_000L, now.plusSeconds(900), now);
        jdbc.update("UPDATE commerce.purchase_order SET fulfilled_at = now() WHERE id = ?", fulfilledOrder.id());
        fixtures.insertStubPayment(fulfilledOrder, 1, 200_000L, PaymentState.SUCCEEDED);
        certifier.certify("api/orders-detail-200-fulfilled.json", 200,
                mockMvc.perform(get("/api/v1/me/orders/" + fulfilledOrder.code()).with(fixtures.asLearner(detailBuyer))).andReturn(),
                ORDER_DYNAMIC);

        var replacedOrder = fixtures.insertStubOrder(
                detailBuyer, product, OrderState.REPLACED, PaymentMethod.VNPAY, 200_000L, now.plusSeconds(900), null);
        jdbc.update("UPDATE commerce.purchase_order SET replaced_by_order_id = ? WHERE id = ?",
                fulfilledOrder.id(), replacedOrder.id());
        fixtures.insertStubPayment(replacedOrder, 1, 200_000L, PaymentState.CREATED);
        certifier.certify("api/orders-detail-200-replaced.json", 200,
                mockMvc.perform(get("/api/v1/me/orders/" + replacedOrder.code()).with(fixtures.asLearner(detailBuyer))).andReturn(),
                ORDER_DYNAMIC);

        var expiredOrder = fixtures.insertStubOrder(
                detailBuyer, product, OrderState.EXPIRED, PaymentMethod.VNPAY, 200_000L, now.minusSeconds(60), null);
        fixtures.insertStubPayment(expiredOrder, 1, 200_000L, PaymentState.CREATED);
        certifier.certify("api/orders-detail-200-expired.json", 200,
                mockMvc.perform(get("/api/v1/me/orders/" + expiredOrder.code()).with(fixtures.asLearner(detailBuyer))).andReturn(),
                ORDER_DYNAMIC);

        var cancelledOrder = fixtures.insertStubOrder(
                detailBuyer, product, OrderState.CANCELLED, PaymentMethod.VNPAY, 200_000L, now.plusSeconds(900), null);
        fixtures.insertStubPayment(cancelledOrder, 1, 200_000L, PaymentState.CREATED);
        certifier.certify("api/orders-detail-200-cancelled.json", 200,
                mockMvc.perform(get("/api/v1/me/orders/" + cancelledOrder.code()).with(fixtures.asLearner(detailBuyer))).andReturn(),
                ORDER_DYNAMIC);

        var refundedOrder = fixtures.insertStubOrder(
                detailBuyer, product, OrderState.REFUNDED, PaymentMethod.VNPAY, 200_000L, now.plusSeconds(900), now);
        jdbc.update("UPDATE commerce.purchase_order SET fulfilled_at = now() WHERE id = ?", refundedOrder.id());
        fixtures.insertStubPayment(refundedOrder, 1, 200_000L, PaymentState.SUCCEEDED);
        certifier.certify("api/orders-detail-200-refunded.json", 200,
                mockMvc.perform(get("/api/v1/me/orders/" + refundedOrder.code()).with(fixtures.asLearner(detailBuyer))).andReturn(),
                ORDER_DYNAMIC);

        var failedPayOrder = fixtures.insertStubOrder(
                detailBuyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY, 200_000L, now.plusSeconds(900), null);
        fixtures.insertStubPayment(failedPayOrder, 1, 200_000L, PaymentState.FAILED);
        certifier.certify("api/orders-detail-200-payment-failed.json", 200,
                mockMvc.perform(get("/api/v1/me/orders/" + failedPayOrder.code()).with(fixtures.asLearner(detailBuyer))).andReturn(),
                ORDER_DYNAMIC);

        var dupBuyer = fixtures.createLearner();
        var dupOrder = fixtures.insertStubOrder(
                dupBuyer, product, OrderState.FULFILLED, PaymentMethod.VNPAY, 200_000L, now.plusSeconds(900), now);
        jdbc.update("UPDATE commerce.purchase_order SET fulfilled_at = now() WHERE id = ?", dupOrder.id());
        fixtures.insertStubPayment(dupOrder, 1, 200_000L, PaymentState.SUCCEEDED);
        var dupPay2 = fixtures.insertStubPayment(dupOrder, 2, 200_000L, PaymentState.CREATED);
        callIpn(dupPay2.txnRef(), 200_000L, "93000991");
        certifier.certify("api/orders-detail-200-duplicate-payment.json", 200,
                mockMvc.perform(get("/api/v1/me/orders/" + dupOrder.code()).with(fixtures.asLearner(dupBuyer))).andReturn(),
                ORDER_DYNAMIC);

        certifier.certify("api/orders-detail-404-order-not-found.json", 404,
                mockMvc.perform(get("/api/v1/me/orders/DH999999ZZZZZZ").with(fixtures.asLearner(buyer))).andReturn(),
                Set.of("traceId"));

        certifier.certify("api/orders-pay-200.json", 200,
                mockMvc.perform(post("/api/v1/me/orders/" + pendingOrderCode + "/pay")
                                .with(fixtures.asLearner(buyer)))
                        .andReturn(),
                ORDER_DYNAMIC);

        certifier.certify("api/orders-pay-404-order-not-found.json", 404,
                mockMvc.perform(post("/api/v1/me/orders/DH999999ZZZZZZ/pay")
                                .with(fixtures.asLearner(buyer)))
                        .andReturn(),
                Set.of("traceId"));

        var payErrBuyer = fixtures.createLearner();
        var missingProd = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var missingProdOrder = fixtures.insertStubOrder(
                payErrBuyer, missingProd, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY, 200_000L, now.plusSeconds(900), null);
        jdbc.update("DELETE FROM commerce.storefront_product WHERE exam_id = ?", missingProd.examId());
        certifier.certify("api/orders-pay-404-product-not-found.json", 404,
                mockMvc.perform(post("/api/v1/me/orders/" + missingProdOrder.code() + "/pay")
                                .with(fixtures.asLearner(payErrBuyer)))
                        .andReturn(),
                Set.of("traceId"));

        var expiredPayOrder = fixtures.insertStubOrder(
                payErrBuyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY, 200_000L, now.minusSeconds(30), null);
        certifier.certify("api/orders-pay-409-order-expired.json", 409,
                mockMvc.perform(post("/api/v1/me/orders/" + expiredPayOrder.code() + "/pay")
                                .with(fixtures.asLearner(payErrBuyer)))
                        .andReturn(),
                Set.of("traceId"));

        String unavPayOrderCode = insertPendingBundleOrder(
                payErrBuyer, unavailableBundle, 200_000L, now.plusSeconds(900));
        certifier.certify("api/orders-pay-409-product-unavailable.json", 409,
                mockMvc.perform(post("/api/v1/me/orders/" + unavPayOrderCode + "/pay")
                                .with(fixtures.asLearner(payErrBuyer)))
                        .andReturn(),
                Set.of("traceId"));

        var discPayOrder = fixtures.insertStubOrder(
                payErrBuyer, discontinued, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY, 200_000L, now.plusSeconds(900), null);
        certifier.certify("api/orders-pay-410-product-discontinued.json", 410,
                mockMvc.perform(post("/api/v1/me/orders/" + discPayOrder.code() + "/pay")
                                .with(fixtures.asLearner(payErrBuyer)))
                        .andReturn(),
                Set.of("traceId", "$.product.code", "$.product.name"));

        certifier.certify("api/orders-cancel-200.json", 200,
                mockMvc.perform(post("/api/v1/me/orders/" + pendingOrderCode + "/cancel")
                                .with(fixtures.asLearner(buyer)))
                        .andReturn(),
                ORDER_DYNAMIC);

        certifier.certify("api/orders-cancel-409-order-not-pending.json", 409,
                mockMvc.perform(post("/api/v1/me/orders/" + pendingOrderCode + "/cancel")
                                .with(fixtures.asLearner(buyer)))
                        .andReturn(),
                Set.of("traceId"));

        certifier.certify("api/orders-pay-409-order-not-pending.json", 409,
                mockMvc.perform(post("/api/v1/me/orders/" + pendingOrderCode + "/pay")
                                .with(fixtures.asLearner(buyer)))
                        .andReturn(),
                Set.of("traceId"));
    }

    private void certifyVnpayReturnFixtures() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var order = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var payment = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.CREATED);

        certifier.certify("api/vnpay-return-200-succeeded.json", 200,
                getVnpayReturn(signedVnpayParams(payment.txnRef(), 200_000L, "00", "00")),
                Set.of("orderCode"));

        certifier.certify("api/vnpay-return-200-failed.json", 200,
                getVnpayReturn(signedVnpayParams(payment.txnRef(), 200_000L, "24", "02")),
                Set.of("orderCode"));

        certifier.certify("api/vnpay-return-200-pending.json", 200,
                getVnpayReturn(signedVnpayParams(payment.txnRef(), 200_000L, "99", "01")),
                Set.of("orderCode"));

        certifier.certify("api/vnpay-return-404-order-not-found.json", 404,
                getVnpayReturn(signedVnpayParams("DH999999ZZZZZZ01", 200_000L, "00", "00")),
                Set.of("traceId"));

        Map<String, String> tampered = new TreeMap<>(signedVnpayParams(payment.txnRef(), 200_000L, "00", "00"));
        tampered.put("vnp_Amount", "100");
        certifier.certify("api/vnpay-return-422-vnpay-signature-invalid.json", 422,
                getVnpayReturn(tampered),
                Set.of("traceId"));
    }

    private MvcResult postQuote(
            CommerceTestFixtures.LearnerAccount learner,
            String code,
            String voucherCode,
            Integer seenVersionNo,
            Long seenPrice)
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
        return mockMvc.perform(post("/api/v1/me/price-quote")
                        .with(fixtures.asLearner(learner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andReturn();
    }

    private MvcResult postOrder(
            CommerceTestFixtures.LearnerAccount learner, String idempotencyKey, Map<String, Object> payload)
            throws Exception {
        return mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(learner))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andReturn();
    }

    private MvcResult getVnpayReturn(Map<String, String> params) throws Exception {
        MockHttpServletRequestBuilder req = get("/api/v1/public/payments/vnpay/return");
        params.forEach(req::param);
        return mockMvc.perform(req).andReturn();
    }

    private void callIpn(String txnRef, long amountVnd, String txnNo) throws Exception {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", Long.toString(amountVnd * 100L));
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", "Thanh toan don test");
        params.put("vnp_PayDate", "20261121100100");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TmnCode", MockVnpayGateway.DEFAULT_MOCK_TMN_CODE);
        params.put("vnp_TransactionNo", txnNo);
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_SecureHash", VnpaySigner.signPayParams(params, MockVnpayGateway.DEFAULT_MOCK_SECRET));
        MockHttpServletRequestBuilder req = get("/api/v1/public/payments/vnpay/ipn");
        params.forEach(req::param);
        mockMvc.perform(req).andExpect(status().isOk());
    }

    private static Map<String, String> signedVnpayParams(
            String txnRef, long amountVnd, String responseCode, String txnStatus) {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", Long.toString(amountVnd * 100L));
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", "Thanh toan don test");
        params.put("vnp_PayDate", "20261121100100");
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TmnCode", MockVnpayGateway.DEFAULT_MOCK_TMN_CODE);
        params.put("vnp_TransactionNo", "92000001");
        params.put("vnp_TransactionStatus", txnStatus);
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_SecureHash", VnpaySigner.signPayParams(params, MockVnpayGateway.DEFAULT_MOCK_SECRET));
        return params;
    }

    private record UnavailableBundle(UUID bundleId, String code, String name) {}

    private UnavailableBundle insertUnavailableBundle(String kindCode, long listPriceVnd, int attemptsPerExam) {
        UUID bundleId = Ids.newId();
        UUID examKindItemId = catalogLookup.findActiveItem("EXAM", kindCode).orElseThrow().id();
        String code = "BDUNAV" + CommerceTestFixtures.shortToken();
        String name = "Bộ đề tạm ngưng " + code;
        java.sql.Timestamp now = java.sql.Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO commerce.bundle
                    (id, code, code_normalized, name, exam_kind_item_id, list_price_vnd, attempts_per_exam,
                     benefit_ai_explanation, benefit_strength_weakness, benefit_practice, result_view, status,
                     has_unpublished_changes, serving_version, revision, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, true, true, false, 'FULL_SOLUTION', 'PUBLISHED',
                        false, 1, 1, ?, ?)
                """,
                bundleId, code, code.toLowerCase(), name, examKindItemId, listPriceVnd, attemptsPerExam, now, now);
        extraBundleIds.add(bundleId);

        UUID sfId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO commerce.storefront_product
                    (id, exam_id, bundle_id, product_type, code, code_normalized, version_no, sale_state, name,
                     search_text, exam_item_id, kind_code, scope, question_count, question_count_max,
                     duration_minutes, duration_minutes_max, max_points, max_points_max, build_mode,
                     list_price_vnd, attempts_granted, card, detail, projection_hash, published_at, updated_at)
                VALUES (?, NULL, ?, 'BUNDLE', ?, ?, 1, 'UNAVAILABLE', ?, ?, ?, ?, 'BUNDLE',
                        50, 50, 75, 75, 100.00, 100.00, 'STANDARD', ?, ?, '{}'::jsonb, '{}'::jsonb, ?, ?, ?)
                """,
                sfId, bundleId, code, code.toLowerCase(), name, (name + " " + code).toLowerCase(),
                examKindItemId, kindCode, listPriceVnd, attemptsPerExam,
                "hash-" + CommerceTestFixtures.shortToken(), now, now);
        extraStorefrontIds.add(sfId);
        return new UnavailableBundle(bundleId, code, name);
    }

    private String insertPendingBundleOrder(
            CommerceTestFixtures.LearnerAccount buyer, UnavailableBundle bundle, long amountDueVnd, Instant expiresAt) {
        UUID orderId = Ids.newId();
        String orderCode = "DH261121" + CommerceTestFixtures.shortToken().substring(0, 6);
        java.sql.Timestamp now = java.sql.Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO commerce.purchase_order
                    (id, code, user_id, buyer_username, buyer_full_name, product_type, bundle_id, bundle_version_no,
                     option_codes, product_code, product_name, list_price_vnd, discount_applied, amount_due_vnd,
                     attempts_granted, benefits, result_view, payment_method, state, expires_at,
                     created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 'BUNDLE', ?, 1, '{}'::text[], ?, ?, ?, 'NONE', ?, 3,
                        '{"aiExplanation":true}'::jsonb, 'FULL_SOLUTION', 'VNPAY', 'PENDING_PAYMENT', ?, ?, ?, 0)
                """,
                orderId, orderCode, buyer.id(), buyer.username(), buyer.fullName(),
                bundle.bundleId(), bundle.code(), bundle.name(), amountDueVnd, amountDueVnd,
                java.sql.Timestamp.from(expiresAt), now, now);
        fixtures.trackOrderId(orderId);
        return orderCode;
    }
}
