package vn.unicom.thi360.commerce.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
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
 * AC-14 & AC-15: tiếp tục thanh toán {@code /pay} giữ giá đã chụp và chặn khi hết hạn/ngừng bán;
 * hủy {@code /cancel} trả lượt voucher.
 */
class OrderPayCancelIT extends AbstractIntegrationTest {

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
    void payReusesCapturedAmountEvenAfterPromotionExpiresAndRejectsExpiredOrDiscontinued() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var promo = fixtures.createAutoPromotion(
                "Short Promo", DiscountType.AMOUNT, 50_000L, null,
                Instant.now().minusSeconds(300), Instant.now().plusSeconds(3600),
                5, PromotionScope.ALL);

        String createdRaw = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "pay1-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "expectedAccountId", buyer.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 150_000L))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode createdOrder = mapper.readTree(createdRaw).path("order");
        UUID orderId = UUID.fromString(createdOrder.path("id").asText());
        String orderCode = createdOrder.path("code").asText();

        // Cho khuyến mãi hết hạn nhưng đơn vẫn trong hạn 15 phút -> /pay vẫn giữ 150_000đ đã chốt!
        jdbc.update("UPDATE commerce.promotion SET ends_at = now() - interval '5 minutes' WHERE id = ?", promo.getId());
        String payRaw = mockMvc.perform(post("/api/v1/me/orders/" + orderCode + "/pay")
                        .with(fixtures.asLearner(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.next").value("REDIRECT"))
                .andExpect(jsonPath("$.txnRef").value(orderCode + "02"))
                .andExpect(jsonPath("$.paymentUrl").value(org.hamcrest.Matchers.containsString("vnp_Amount=15000000")))
                .andReturn().getResponse().getContentAsString();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.payment WHERE order_id = ?", Integer.class, orderId))
                .isEqualTo(2);

        // R1-21: đặt expires_at = now + 3 phút -> vnp_ExpireDate trong paymentUrl và DB khớp chính xác expires_at GMT+7.
        Instant clampedExpiry = Instant.now().plusSeconds(180).truncatedTo(ChronoUnit.SECONDS);
        String expectedExpireStr = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .format(clampedExpiry.atZone(ZoneId.of("Asia/Ho_Chi_Minh")));
        jdbc.update("UPDATE commerce.purchase_order SET expires_at = ? WHERE id = ?",
                Timestamp.from(clampedExpiry), orderId);

        String payIdkKey = "pay-idem-" + UUID.randomUUID();
        String clampedRaw = mockMvc.perform(post("/api/v1/me/orders/" + orderCode + "/pay")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", payIdkKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.txnRef").value(orderCode + "03"))
                .andReturn().getResponse().getContentAsString();
        JsonNode clampedJson = mapper.readTree(clampedRaw);
        assertThat(parseQueryParams(clampedJson.path("paymentUrl").asText()).get("vnp_ExpireDate"))
                .isEqualTo(expectedExpireStr);
        assertThat(jdbc.queryForObject(
                "SELECT vnp_expire_date FROM commerce.payment WHERE txn_ref = ?", String.class, orderCode + "03"))
                .isEqualTo(expectedExpireStr);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.payment WHERE order_id = ?", Integer.class, orderId))
                .isEqualTo(3);

        // R1-22: replay cùng Idempotency-Key cho /pay trả đúng txnRef + paymentUrl cũ, không thêm payment.
        String payReplayRaw = mockMvc.perform(post("/api/v1/me/orders/" + orderCode + "/pay")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", payIdkKey))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode payReplayJson = mapper.readTree(payReplayRaw);
        assertThat(payReplayJson.path("txnRef").asText()).isEqualTo(clampedJson.path("txnRef").asText());
        assertThat(payReplayJson.path("paymentUrl").asText()).isEqualTo(clampedJson.path("paymentUrl").asText());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.payment WHERE order_id = ?", Integer.class, orderId))
                .isEqualTo(3);

        // Sản phẩm ngừng bán -> /pay trả 410 PRODUCT_DISCONTINUED, không thêm payment.
        fixtures.updateStorefrontPriceAndVersion(product.examId(), 200_000L, 1, "DISCONTINUED");
        mockMvc.perform(post("/api/v1/me/orders/" + orderCode + "/pay")
                        .with(fixtures.asLearner(buyer)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("PRODUCT_DISCONTINUED"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.payment WHERE order_id = ?", Integer.class, orderId))
                .isEqualTo(3);

        // Đơn hết hạn -> /pay trả 409 ORDER_EXPIRED, không thêm payment.
        fixtures.updateStorefrontPriceAndVersion(product.examId(), 200_000L, 1, "ON_SALE");
        jdbc.update("UPDATE commerce.purchase_order SET expires_at = now() - interval '1 second' WHERE id = ?", orderId);
        mockMvc.perform(post("/api/v1/me/orders/" + orderCode + "/pay")
                        .with(fixtures.asLearner(buyer)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_EXPIRED"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.payment WHERE order_id = ?", Integer.class, orderId))
                .isEqualTo(3);
    }

    @Test
    void cancelReleasesVoucherForReuseAndRejectsNonPendingOrders() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var voucher = fixtures.createVoucher(
                "CAN1", DiscountType.AMOUNT, 40_000L, null,
                1, 1, true, PromotionScope.ALL, PromotionAudienceType.ALL);

        String raw = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "can1-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "voucherCode", voucher.getCode(),
                                "expectedAccountId", buyer.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 160_000L))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderCode = mapper.readTree(raw).path("order").path("code").asText();

        mockMvc.perform(post("/api/v1/me/orders/" + orderCode + "/cancel")
                        .with(fixtures.asLearner(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CANCELLED"));

        // Hủy lần 2 -> 409 ORDER_NOT_PENDING; gọi /pay trên đơn CANCELLED -> 409 ORDER_NOT_PENDING và 0 payment mới.
        mockMvc.perform(post("/api/v1/me/orders/" + orderCode + "/cancel")
                        .with(fixtures.asLearner(buyer)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PENDING"));

        UUID cancelledOrderId = UUID.fromString(mapper.readTree(raw).path("order").path("id").asText());
        int paymentsBeforeCancelledPay = jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.payment WHERE order_id = ?", Integer.class, cancelledOrderId);
        mockMvc.perform(post("/api/v1/me/orders/" + orderCode + "/pay")
                        .with(fixtures.asLearner(buyer)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PENDING"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.payment WHERE order_id = ?", Integer.class, cancelledOrderId))
                .isEqualTo(paymentsBeforeCancelledPay);

        // Lượt voucher đã được trả nên dùng lại được ngay ở đơn mới.
        mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "can2-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "voucherCode", voucher.getCode(),
                                "expectedAccountId", buyer.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 160_000L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.state").value("PENDING_PAYMENT"));
    }

    private static Map<String, String> parseQueryParams(String url) {
        String query = URI.create(url).getRawQuery();
        Map<String, String> map = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
            String v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
            map.put(k, v);
        }
        return map;
    }
}
