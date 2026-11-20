package vn.unicom.thi360.commerce.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudienceType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;

/**
 * E26 (AC-10): mức tối thiểu VNPAY ({@code 10.000đ} theo AD-10) — đúng 10.000đ vẫn mở VNPAY; khuyến mãi
 * tự động không hạ dưới 10.000đ; voucher kéo còn dưới 10.000đ (ví dụ 9.999đ) đưa đơn về 0đ ({@code FREE}).
 */
@TestPropertySource(properties = "thi360.commerce.vnpay-min-amount=10000")
class OrderAmountBoundaryIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;

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
    void clampsAutoPromoAtMinAmountAndZeroesSubMinimumVoucherDueToFreeOrder() throws Exception {
        var buyer1 = fixtures.createLearner();
        var buyer2 = fixtures.createLearner();
        var buyer3 = fixtures.createLearner();

        // 1. Đúng ngưỡng 10_000đ -> đơn VNPAY.
        var minPriceProduct = fixtures.insertOnSaleProduct("HSA", 10_000L, 1);
        mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer1))
                        .header("Idempotency-Key", "min-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", minPriceProduct.code(),
                                "expectedAccountId", buyer1.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 10_000L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.next").value("REDIRECT"))
                .andExpect(jsonPath("$.order.paymentMethod").value("VNPAY"))
                .andExpect(jsonPath("$.order.amountDueVnd").value(10_000L));

        // 2. Sản phẩm 15_000đ kèm auto-promo giảm 8_000đ -> kẹp sàn ở 10_000đ, vẫn qua VNPAY.
        var promoProduct = fixtures.insertOnSaleProduct("QDA", 15_000L, 1);
        var autoPromo = fixtures.createAutoPromotion(
                "Floor Clamp", DiscountType.AMOUNT, 8_000L, null,
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600),
                10, PromotionScope.ALL);
        mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer2))
                        .header("Idempotency-Key", "flr-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", promoProduct.code(),
                                "expectedAccountId", buyer2.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 10_000L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.next").value("REDIRECT"))
                .andExpect(jsonPath("$.order.amountDueVnd").value(10_000L));
        jdbc.update("UPDATE commerce.promotion SET status = 'STOPPED' WHERE id = ?", autoPromo.getId());

        // 3. Voucher giảm sản phẩm 15_000đ đi 5_001đ -> còn 9_999đ < 10_000đ -> ép về 0đ FREE và cấp quyền luôn.
        var freeClampedProduct = fixtures.insertOnSaleProduct("BCA", 15_000L, 2);
        var subMinVoucher = fixtures.createVoucher(
                "SUBMIN", DiscountType.AMOUNT, 5_001L, null,
                5, 1, false, PromotionScope.ALL, PromotionAudienceType.ALL);
        mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer3))
                        .header("Idempotency-Key", "submin-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", freeClampedProduct.code(),
                                "voucherCode", subMinVoucher.getCode(),
                                "expectedAccountId", buyer3.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 0L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.next").value("FULFILLED"))
                .andExpect(jsonPath("$.order.paymentMethod").value("FREE"))
                .andExpect(jsonPath("$.order.amountDueVnd").value(0L))
                .andExpect(jsonPath("$.order.state").value("FULFILLED"));
    }
}
