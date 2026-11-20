package vn.unicom.thi360.commerce.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.order.infrastructure.StorefrontProductLockRepository;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudienceType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;

/**
 * AC-13 (E13): đơn chờ cùng giá và voucher thì dùng lại, tạo lần thanh toán mới; khác thì đơn cũ thành
 * {@code REPLACED}, liên kết sang đơn mới và trả lượt voucher.
 */
class OrderReplaceIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private StorefrontProductLockRepository storefrontProductLockRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private CommerceTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new CommerceTestFixtures(jdbc, mapper, catalogLookup, promotionRepository, testSecuritySupport);
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanup();
    }

    @Test
    void reusesPendingOrderWhenSamePriceAndVoucherAndReplacesWhenChanged() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var oneShotVoucher = fixtures.createVoucher(
                "E13ONE", DiscountType.AMOUNT, 50_000L, null,
                1, 1, true, PromotionScope.ALL, PromotionAudienceType.ALL);
        var otherVoucher = fixtures.createVoucher(
                "E13TWO", DiscountType.AMOUNT, 70_000L, null,
                5, 1, true, PromotionScope.ALL, PromotionAudienceType.ALL);

        // 1. Tạo đơn chờ lần 1 với oneShotVoucher.
        String firstRaw = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "o1-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "voucherCode", oneShotVoucher.getCode(),
                                "expectedAccountId", buyer.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 150_000L))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode firstJson = mapper.readTree(firstRaw).path("order");
        UUID firstOrderId = UUID.fromString(firstJson.path("id").asText());
        String firstCode = firstJson.path("code").asText();

        // 2. Tạo lại cùng sản phẩm, cùng giá, cùng voucher -> dùng lại đơn cũ, tăng payment attempt_seq = 2.
        String reusedRaw = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "o2-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "voucherCode", oneShotVoucher.getCode(),
                                "expectedAccountId", buyer.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 150_000L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.id").value(firstOrderId.toString()))
                .andExpect(jsonPath("$.order.state").value("PENDING_PAYMENT"))
                .andReturn().getResponse().getContentAsString();

        assertThat(mapper.readTree(reusedRaw).path("order").path("payments")).hasSize(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.purchase_order WHERE user_id = ?", Integer.class, buyer.id()))
                .isEqualTo(1);

        // 3. Đổi sang voucher khác -> đơn 1 thành REPLACED, liên kết sang đơn mới, oneShotVoucher được RELEASED.
        String replacedRaw = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "o3-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "voucherCode", otherVoucher.getCode(),
                                "expectedAccountId", buyer.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 130_000L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.state").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.order.amountDueVnd").value(130_000L))
                .andReturn().getResponse().getContentAsString();

        JsonNode secondOrderJson = mapper.readTree(replacedRaw).path("order");
        UUID secondOrderId = UUID.fromString(secondOrderJson.path("id").asText());
        String secondCode = secondOrderJson.path("code").asText();
        assertThat(secondOrderId).isNotEqualTo(firstOrderId);

        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, firstOrderId))
                .isEqualTo("REPLACED");
        assertThat(jdbc.queryForObject(
                "SELECT replaced_by_order_id FROM commerce.purchase_order WHERE id = ?", UUID.class, firstOrderId))
                .isEqualTo(secondOrderId);
        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.promotion_redemption WHERE order_id = ? AND promotion_id = ?",
                String.class, firstOrderId, oneShotVoucher.getId()))
                .isEqualTo("RELEASED");

        // R1-20: chủ đơn xem GET /api/v1/me/orders/{code} (kể cả chữ thường) cho đơn REPLACED thấy replacedByOrderCode.
        mockMvc.perform(get("/api/v1/me/orders/" + firstCode.toLowerCase(Locale.ROOT))
                        .with(fixtures.asLearner(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(firstCode))
                .andExpect(jsonPath("$.state").value("REPLACED"))
                .andExpect(jsonPath("$.replacedByOrderId").value(secondOrderId.toString()))
                .andExpect(jsonPath("$.replacedByOrderCode").value(secondCode));

        // 4. Khi đơn đã hết hạn (expires_at quá khứ), mua lại cùng giá phải REPLACED (không dùng lại đơn quá hạn).
        jdbc.update("UPDATE commerce.purchase_order SET expires_at = now() - interval '1 minute' WHERE id = ?",
                secondOrderId);
        String freshAfterExpiry = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "o4-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "voucherCode", otherVoucher.getCode(),
                                "expectedAccountId", buyer.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 130_000L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.state").value("PENDING_PAYMENT"))
                .andReturn().getResponse().getContentAsString();

        UUID thirdOrderId = UUID.fromString(mapper.readTree(freshAfterExpiry).path("order").path("id").asText());
        assertThat(thirdOrderId).isNotEqualTo(secondOrderId);
        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, secondOrderId))
                .isEqualTo("REPLACED");
    }

    /** AC-9 concurrency: 10 concurrent POST /me/orders with distinct Idempotency-Keys -> zero 500s, strictly 1 PENDING_PAYMENT. */
    @Test
    void concurrentCreateRequestsLeaveSinglePendingOrder() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        int threads = 10;
        String payload = mapper.writeValueAsString(Map.of(
                "code", product.code(),
                "expectedAccountId", buyer.id(),
                "expectedVersionNo", 1,
                "expectedAmountDue", 200_000L));

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<Integer> job = () -> {
                    ready.countDown();
                    start.await(15, TimeUnit.SECONDS);
                    return mockMvc.perform(post("/api/v1/me/orders")
                                    .with(fixtures.asLearner(buyer))
                                    .header("Idempotency-Key", "conc-rep-" + UUID.randomUUID())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(payload))
                            .andReturn().getResponse().getStatus();
                };
                futures.add(pool.submit(job));
            }
            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<Integer> f : futures) {
                statuses.add(f.get(30, TimeUnit.SECONDS));
            }
        }

        assertThat(statuses).hasSize(threads).allSatisfy(s -> assertThat(s).isLessThan(500));
        assertThat(statuses).contains(201);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.purchase_order WHERE user_id = ? AND state = 'PENDING_PAYMENT'",
                Integer.class, buyer.id())).isEqualTo(1);
    }

    /** AC-39: learner holding the last slot of total_limit=1 in a PENDING_PAYMENT order still quotes & orders; others fail 422 VOUCHER_EXHAUSTED. */
    @Test
    void pendingOrderHoldingLastVoucherSlotAllowsOwnQuoteAndOrderButBlocksOthers() throws Exception {
        var buyerA = fixtures.createLearner();
        var buyerB = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var lastSlotVoucher = fixtures.createVoucher(
                "AC39", DiscountType.AMOUNT, 50_000L, null,
                1, 1, false, PromotionScope.ALL, PromotionAudienceType.ALL);

        // 1. Learner A creates PENDING_PAYMENT order taking the last slot of lastSlotVoucher.
        mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyerA))
                        .header("Idempotency-Key", "ac39-a1-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "voucherCode", lastSlotVoucher.getCode(),
                                "expectedAccountId", buyerA.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 150_000L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.amountDueVnd").value(150_000L));

        // 2. Learner A calls /me/price-quote and /me/orders with the same voucher -> both succeed!
        mockMvc.perform(post("/api/v1/me/price-quote")
                        .with(fixtures.asLearner(buyerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "voucherCode", lastSlotVoucher.getCode()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalPrice").value(150_000L))
                .andExpect(jsonPath("$.voucher.code").value(lastSlotVoucher.getCode()));

        mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyerA))
                        .header("Idempotency-Key", "ac39-a2-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "voucherCode", lastSlotVoucher.getCode(),
                                "expectedAccountId", buyerA.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 150_000L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.amountDueVnd").value(150_000L));

        // 3. Learner B calls /me/price-quote and /me/orders with the same voucher -> both get 422 VOUCHER_EXHAUSTED!
        mockMvc.perform(post("/api/v1/me/price-quote")
                        .with(fixtures.asLearner(buyerB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "voucherCode", lastSlotVoucher.getCode()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VOUCHER_EXHAUSTED"));

        mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyerB))
                        .header("Idempotency-Key", "ac39-b1-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "voucherCode", lastSlotVoucher.getCode(),
                                "expectedAccountId", buyerB.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 150_000L))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VOUCHER_EXHAUSTED"));
    }

    /** R1-14: thay thế đơn chờ giữ voucher B sang voucher A với A < B (unsigned UUID) khóa tăng dần toàn cục sạch sẽ. */
    @Test
    void replacesPendingOrderFromHigherUuidVoucherToLowerUuidVoucherWithoutDeadlock() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var v1 = fixtures.createVoucher(
                "ORD1", DiscountType.AMOUNT, 30_000L, null,
                5, 1, false, PromotionScope.ALL, PromotionAudienceType.ALL);
        var v2 = fixtures.createVoucher(
                "ORD2", DiscountType.AMOUNT, 60_000L, null,
                5, 1, false, PromotionScope.ALL, PromotionAudienceType.ALL);

        boolean v1Smaller = compareUuidUnsigned(v1.getId(), v2.getId()) < 0;
        var voucherA = v1Smaller ? v1 : v2;
        var voucherB = v1Smaller ? v2 : v1;
        long dueWithB = 200_000L - voucherB.getDiscountValue();
        long dueWithA = 200_000L - voucherA.getDiscountValue();

        List<UUID> lockedOrder = new TransactionTemplate(transactionManager)
                .execute(status -> storefrontProductLockRepository.lockPromotionsInOrder(
                        List.of(voucherB.getId(), voucherA.getId())));
        assertThat(lockedOrder).containsExactly(voucherA.getId(), voucherB.getId());

        String firstRaw = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "r14-b-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "voucherCode", voucherB.getCode(),
                                "expectedAccountId", buyer.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", dueWithB))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID firstOrderId = UUID.fromString(mapper.readTree(firstRaw).path("order").path("id").asText());

        String secondRaw = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "r14-a-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "voucherCode", voucherA.getCode(),
                                "expectedAccountId", buyer.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", dueWithA))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.state").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.order.voucherCode").value(voucherA.getCode()))
                .andReturn().getResponse().getContentAsString();
        UUID secondOrderId = UUID.fromString(mapper.readTree(secondRaw).path("order").path("id").asText());

        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, firstOrderId))
                .isEqualTo("REPLACED");
        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.promotion_redemption WHERE order_id = ? AND promotion_id = ?",
                String.class, firstOrderId, voucherB.getId()))
                .isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.promotion_redemption WHERE order_id = ? AND promotion_id = ?",
                String.class, secondOrderId, voucherA.getId()))
                .isEqualTo("RESERVED");
    }

    private static int compareUuidUnsigned(UUID a, UUID b) {
        int msb = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return msb != 0 ? msb : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }
}
