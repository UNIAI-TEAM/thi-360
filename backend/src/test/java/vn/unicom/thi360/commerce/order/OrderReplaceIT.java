package vn.unicom.thi360.commerce.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

        UUID secondOrderId = UUID.fromString(mapper.readTree(replacedRaw).path("order").path("id").asText());
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
}
