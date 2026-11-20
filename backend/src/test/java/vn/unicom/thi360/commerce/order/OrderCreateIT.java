package vn.unicom.thi360.commerce.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudienceType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;

/**
 * AC-3..AC-7, AC-9..AC-12, AC-31..AC-33: tạo đơn, idempotency, kiểm giá/tài khoản/voucher, chụp dữ liệu.
 */
class OrderCreateIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private VnpayGateway vnpayGateway;

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
    void createsPendingVnpayOrderWithSnapshotAndReservesPromotionAndHandlesIdempotency() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var autoPromo = fixtures.createAutoPromotion(
                "Auto 20%", DiscountType.PERCENT, 20L, null,
                Instant.now().minusSeconds(600), Instant.now().plusSeconds(3600),
                10, PromotionScope.ALL);

        String idkKey = "ord-create-" + UUID.randomUUID();
        String body = mapper.writeValueAsString(Map.of(
                "code", product.code(),
                "expectedAccountId", buyer.id(),
                "expectedVersionNo", 1,
                "expectedAmountDue", 160_000L));

        String raw = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", idkKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.next").value("REDIRECT"))
                .andExpect(jsonPath("$.paymentUrl").isNotEmpty())
                .andExpect(jsonPath("$.order.state").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.order.paymentMethod").value("VNPAY"))
                .andExpect(jsonPath("$.order.amountDueVnd").value(160_000L))
                .andExpect(jsonPath("$.order.promotionDiscountVnd").value(40_000L))
                .andExpect(jsonPath("$.order.discountApplied").value("PROMOTION_ONLY"))
                .andExpect(jsonPath("$.order.optionCodes[0]").value("OPT-A"))
                .andExpect(jsonPath("$.order.payments[0].txnRef").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = mapper.readTree(raw);
        String orderCode = json.path("order").path("code").asText();
        UUID orderId = UUID.fromString(json.path("order").path("id").asText());
        assertThat(orderCode).matches("^DH\\d{6}[A-Z0-9]{6}$");
        assertThat(json.path("order").path("payments").get(0).path("txnRef").asText())
                .isEqualTo(orderCode + "01");
        assertThat(json.path("paymentUrl").asText()).contains("/mock-vnpay?");

        Integer redemptionCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.promotion_redemption WHERE order_id = ? AND promotion_id = ? AND state = 'RESERVED'",
                Integer.class, orderId, autoPromo.getId());
        assertThat(redemptionCount).isEqualTo(1);

        // Replay cùng Idempotency-Key và cùng body trả kết quả cũ, không tạo đơn hay payment thứ hai.
        String replay = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", idkKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(replay).path("order").path("id").asText()).isEqualTo(orderId.toString());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.purchase_order WHERE user_id = ?", Integer.class, buyer.id()))
                .isEqualTo(1);

        // Cùng Idempotency-Key khác payload -> 409 IDEMPOTENCY_KEY_REUSED.
        String mutatedBody = mapper.writeValueAsString(Map.of(
                "code", product.code(),
                "expectedAccountId", buyer.id(),
                "expectedVersionNo", 1,
                "expectedAmountDue", 160_001L));
        mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", idkKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mutatedBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        // Thiếu Idempotency-Key -> 400 IDEMPOTENCY_KEY_REQUIRED.
        mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void returnsConflictOnAccountChangedOrPriceChangedWithFreshQuoteAnd410OnDiscontinued() throws Exception {
        var buyer = fixtures.createLearner();
        var otherBuyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);

        // 409 ACCOUNT_CHANGED khi phiên đổi tài khoản.
        mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "acc-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "expectedAccountId", otherBuyer.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 200_000L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_CHANGED"));

        // Đổi giá ở storefront_product -> 409 PRICE_CHANGED kèm quote mới, không tạo đơn.
        fixtures.updateStorefrontPriceAndVersion(product.examId(), 250_000L, 2, "ON_SALE");
        mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "price-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "expectedAccountId", buyer.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 200_000L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRICE_CHANGED"))
                .andExpect(jsonPath("$.quote.finalPrice").value(250_000L))
                .andExpect(jsonPath("$.quote.versionNo").value(2));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.purchase_order WHERE user_id = ?", Integer.class, buyer.id()))
                .isZero();

        // Ngừng bán -> 410 PRODUCT_DISCONTINUED.
        fixtures.updateStorefrontPriceAndVersion(product.examId(), 250_000L, 2, "DISCONTINUED");
        mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "gone-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "expectedAccountId", buyer.id(),
                                "expectedVersionNo", 2,
                                "expectedAmountDue", 250_000L))))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("PRODUCT_DISCONTINUED"))
                .andExpect(jsonPath("$.product.code").value(product.code()));
    }

    @Test
    void enforcesVoucherErrorsAndSkipsVoucherReservationWhenAutoPromoIsBetter() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var expiredVoucher = fixtures.createVoucher(
                "EXP", DiscountType.AMOUNT, 50_000L, null,
                Instant.now().minus(2, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.HOURS),
                10, 1, false, PromotionScope.ALL, PromotionAudienceType.ALL);

        // Voucher hết hạn -> 422 VOUCHER_EXPIRED, không tạo đơn.
        mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "vexp-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "voucherCode", expiredVoucher.getCode(),
                                "expectedAccountId", buyer.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 150_000L))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VOUCHER_EXPIRED"));

        // Voucher không cộng dồn kém hơn khuyến mãi tự động -> chỉ giữ khuyến mãi tự động, không giữ chỗ voucher.
        var autoPromo = fixtures.createAutoPromotion(
                "Auto 50%", DiscountType.PERCENT, 50L, null,
                Instant.now().minusSeconds(600), Instant.now().plusSeconds(3600),
                10, PromotionScope.ALL);
        var weakVoucher = fixtures.createVoucher(
                "WEAK", DiscountType.AMOUNT, 20_000L, null, 10, 1, false,
                PromotionScope.ALL, PromotionAudienceType.ALL);

        String raw = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "vnb-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "voucherCode", weakVoucher.getCode(),
                                "expectedAccountId", buyer.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 100_000L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.discountApplied").value("PROMOTION_ONLY"))
                .andExpect(jsonPath("$.order.voucherCode").isEmpty())
                .andReturn().getResponse().getContentAsString();

        UUID orderId = UUID.fromString(mapper.readTree(raw).path("order").path("id").asText());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.promotion_redemption WHERE order_id = ? AND promotion_id = ?",
                Integer.class, orderId, weakVoucher.getId())).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.promotion_redemption WHERE order_id = ? AND promotion_id = ?",
                Integer.class, orderId, autoPromo.getId())).isEqualTo(1);
    }
}
