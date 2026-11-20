package vn.unicom.thi360.commerce.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.pricing.PriceCalculator;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudienceType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;

/**
 * AC-38 đồng thời (20 tài khoản tranh 1 lượt voucher cuối), cộng kiểm thử race khuyến mãi tự động (R1-15):
 * 1 đơn thành công, 19 đơn còn lại nhận 422 {@code VOUCHER_EXHAUSTED}, và {@code used_count == total_limit};
 * khuyến mãi tự động hết lượt giữa bước tính giá và giữ chỗ trả 409 {@code PRICE_CHANGED}.
 */
class OrderVoucherConcurrencyIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private PriceCalculator priceCalculator;

    private CommerceTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        reset(priceCalculator);
        fixtures = new CommerceTestFixtures(jdbc, mapper, catalogLookup, promotionRepository, testSecuritySupport);
    }

    @AfterEach
    void tearDown() {
        reset(priceCalculator);
        fixtures.cleanup();
    }

    @Test
    void onlyOneLearnerClaimsLastVoucherSlotUnderConcurrentOrderCreation() throws Exception {
        int threads = 20;
        List<CommerceTestFixtures.LearnerAccount> buyers = new ArrayList<>(threads);
        for (int i = 0; i < threads; i++) {
            buyers.add(fixtures.createLearner());
        }
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var lastSlotVoucher = fixtures.createVoucher(
                "RACE1", DiscountType.AMOUNT, 50_000L, null,
                1, 1, false, PromotionScope.ALL, PromotionAudienceType.ALL);

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<OrderAttemptResult> results = new ArrayList<>(threads);
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<OrderAttemptResult>> futures = new ArrayList<>(threads);
            for (CommerceTestFixtures.LearnerAccount buyer : buyers) {
                futures.add(pool.submit(() -> submitOrder(
                        buyer, product.code(), lastSlotVoucher.getCode(), 150_000L, ready, start)));
            }
            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<OrderAttemptResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
        }

        List<OrderAttemptResult> winners = results.stream().filter(r -> r.status() == 201).toList();
        List<OrderAttemptResult> losers = results.stream().filter(r -> r.status() != 201).toList();
        assertThat(winners).hasSize(1);
        assertThat(losers).hasSize(19).allSatisfy(r -> {
            assertThat(r.status()).isEqualTo(422);
            assertThat(r.code()).isEqualTo("VOUCHER_EXHAUSTED");
        });

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.purchase_order WHERE voucher_id = ?",
                Integer.class, lastSlotVoucher.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.promotion_redemption WHERE promotion_id = ? AND state = 'RESERVED'",
                Integer.class, lastSlotVoucher.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT used_count FROM commerce.promotion WHERE id = ?",
                Integer.class, lastSlotVoucher.getId())).isEqualTo(1);
    }

    @Test
    void concurrentSingleSlotAutoPromotionAcrossProductsReturns409PriceChangedToLosers() throws Exception {
        var buyer1 = fixtures.createLearner();
        var buyer2 = fixtures.createLearner();
        var product1 = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var product2 = fixtures.insertOnSaleProduct("QDA", 200_000L, 2);
        var autoPromo = fixtures.createAutoPromotion(
                "Auto Race 1 Slot", DiscountType.AMOUNT, 50_000L, null,
                Instant.now().minusSeconds(600), Instant.now().plusSeconds(3600),
                1, PromotionScope.ALL);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<OrderAttemptResult> results = new ArrayList<>(2);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<OrderAttemptResult> f1 = pool.submit(() ->
                    submitOrder(buyer1, product1.code(), null, 150_000L, ready, start));
            Future<OrderAttemptResult> f2 = pool.submit(() ->
                    submitOrder(buyer2, product2.code(), null, 150_000L, ready, start));
            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            results.add(f1.get(30, TimeUnit.SECONDS));
            results.add(f2.get(30, TimeUnit.SECONDS));
        }

        OrderAttemptResult winner = results.stream().filter(r -> r.status() == 201).findFirst().orElseThrow();
        OrderAttemptResult loser = results.stream().filter(r -> r.status() != 201).findFirst().orElseThrow();
        assertThat(winner.status()).isEqualTo(201);
        assertThat(loser.status()).isEqualTo(409);
        assertThat(loser.code()).isEqualTo("PRICE_CHANGED");
        assertThat(loser.quoteFinalPrice()).isEqualTo(200_000L);
        assertThat(jdbc.queryForObject(
                "SELECT used_count FROM commerce.promotion WHERE id = ?", Integer.class, autoPromo.getId()))
                .isEqualTo(1);
    }

    @Test
    void exhaustedAutoPromotionBetweenQuoteAndReserveReturns409PriceChangedWithFreshQuote() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var autoPromo = fixtures.createAutoPromotion(
                "Auto TOCTOU", DiscountType.AMOUNT, 50_000L, null,
                Instant.now().minusSeconds(600), Instant.now().plusSeconds(3600),
                1, PromotionScope.ALL);

        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        AtomicBoolean exhaustedOnce = new AtomicBoolean(false);
        doAnswer(inv -> {
            Object calc = inv.callRealMethod();
            if (exhaustedOnce.compareAndSet(false, true)) {
                requiresNew.executeWithoutResult(st -> jdbc.update(
                        "UPDATE commerce.promotion SET used_count = 1 WHERE id = ?", autoPromo.getId()));
            }
            return calc;
        }).when(priceCalculator).calculateQuote(any());

        mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "toctou-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "expectedAccountId", buyer.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 150_000L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRICE_CHANGED"))
                .andExpect(jsonPath("$.quote.finalPrice").value(200_000L));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.purchase_order WHERE user_id = ?", Integer.class, buyer.id()))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.promotion_redemption WHERE promotion_id = ?",
                Integer.class, autoPromo.getId())).isZero();
    }

    private OrderAttemptResult submitOrder(
            CommerceTestFixtures.LearnerAccount buyer,
            String productCode,
            String voucherCode,
            long expectedAmountDue,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("code", productCode);
        if (voucherCode != null) {
            payload.put("voucherCode", voucherCode);
        }
        payload.put("expectedAccountId", buyer.id());
        payload.put("expectedVersionNo", 1);
        payload.put("expectedAmountDue", expectedAmountDue);
        String body = mapper.writeValueAsString(payload);
        ready.countDown();
        assertThat(start.await(15, TimeUnit.SECONDS)).isTrue();
        MockHttpServletResponse response = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "race-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse();
        JsonNode root = mapper.readTree(response.getContentAsString());
        Long quoteFinalPrice = root.has("quote") ? root.path("quote").path("finalPrice").asLong() : null;
        return new OrderAttemptResult(response.getStatus(), root.path("code").asText(null), quoteFinalPrice);
    }

    private record OrderAttemptResult(int status, String code, Long quoteFinalPrice) {
    }
}
