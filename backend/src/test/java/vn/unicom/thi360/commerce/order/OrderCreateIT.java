package vn.unicom.thi360.commerce.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpaySigner;
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

        // Replay cùng Idempotency-Key và cùng body trả kết quả cũ (kể cả paymentUrl), không tạo đơn hay payment thứ hai.
        String replay = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", idkKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode replayJson = mapper.readTree(replay);
        assertThat(replayJson.path("order").path("id").asText()).isEqualTo(orderId.toString());
        assertThat(replayJson.path("paymentUrl").asText()).isEqualTo(json.path("paymentUrl").asText());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.purchase_order WHERE user_id = ?", Integer.class, buyer.id()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.payment WHERE order_id = ?", Integer.class, orderId))
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

        // R1-24a: chỉ đổi giá (giữ nguyên versionNo = 1) -> 409 PRICE_CHANGED kèm quote mới, không tạo đơn.
        fixtures.updateStorefrontPriceAndVersion(product.examId(), 250_000L, 1, "ON_SALE");
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
                .andExpect(jsonPath("$.quote.versionNo").value(1));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.purchase_order WHERE user_id = ?", Integer.class, buyer.id()))
                .isZero();

        // R1-24b: chỉ tăng versionNo = 2 (giữ nguyên giá 250_000đ) -> 409 PRICE_CHANGED kèm quote mới, không tạo đơn.
        fixtures.updateStorefrontPriceAndVersion(product.examId(), 250_000L, 2, "ON_SALE");
        mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "ver-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "expectedAccountId", buyer.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 250_000L))))
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

    @Test
    void createsAndFulfillsOrderWhenDetailBenefitsNoteIsNull() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);

        // Mô phỏng đúng JSON do StorefrontProjector tạo khi exam_listing.benefit_note là null ("note": null).
        String existingDetail = jdbc.queryForObject(
                "SELECT detail::text FROM commerce.storefront_product WHERE exam_id = ?",
                String.class, product.examId());
        ObjectNode detailRoot = (ObjectNode) mapper.readTree(existingDetail);
        ObjectNode benefitsNode = mapper.createObjectNode();
        benefitsNode.put("aiExplanation", true);
        benefitsNode.put("strengthWeakness", false);
        benefitsNode.put("practice", true);
        benefitsNode.put("attempts", 3);
        benefitsNode.putNull("note");
        detailRoot.set("benefits", benefitsNode);
        jdbc.update("UPDATE commerce.storefront_product SET detail = ?::jsonb WHERE exam_id = ?",
                mapper.writeValueAsString(detailRoot), product.examId());

        String storedDetail = jdbc.queryForObject(
                "SELECT detail::text FROM commerce.storefront_product WHERE exam_id = ?",
                String.class, product.examId());
        JsonNode storedBenefits = mapper.readTree(storedDetail).path("benefits");
        assertThat(storedBenefits.has("note") && storedBenefits.get("note").isNull()).isTrue();

        String raw = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "null-note-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "expectedAccountId", buyer.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 200_000L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.state").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.order.benefits.attempts").value(3))
                .andExpect(jsonPath("$.order.benefits.note").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        JsonNode created = mapper.readTree(raw);
        UUID orderId = UUID.fromString(created.path("order").path("id").asText());
        String txnRef = created.path("order").path("payments").get(0).path("txnRef").asText();

        // Thanh toán IPN -> cấp quyền FULFILLED không NPE ở GrantLine / EntitlementGrantService.
        Map<String, String> ipnParams = new TreeMap<>();
        ipnParams.put("vnp_Amount", Long.toString(200_000L * 100L));
        ipnParams.put("vnp_BankCode", "NCB");
        ipnParams.put("vnp_CardType", "ATM");
        ipnParams.put("vnp_OrderInfo", "Thanh toan don test");
        ipnParams.put("vnp_PayDate", "20261121100100");
        ipnParams.put("vnp_ResponseCode", "00");
        ipnParams.put("vnp_TmnCode", MockVnpayGateway.DEFAULT_MOCK_TMN_CODE);
        ipnParams.put("vnp_TransactionNo", "97000001");
        ipnParams.put("vnp_TransactionStatus", "00");
        ipnParams.put("vnp_TxnRef", txnRef);
        ipnParams.put("vnp_SecureHash", VnpaySigner.signPayParams(ipnParams, MockVnpayGateway.DEFAULT_MOCK_SECRET));
        MockHttpServletRequestBuilder ipnReq = get("/api/v1/public/payments/vnpay/ipn");
        ipnParams.forEach(ipnReq::param);
        mockMvc.perform(ipnReq)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"));

        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, orderId))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.entitlement_grant WHERE order_id = ?", Integer.class, orderId))
                .isEqualTo(1);
    }

    @Test
    void rejectsInvalidCreateOrderPayloadWithLocalizedValidationMessages() throws Exception {
        var buyer = fixtures.createLearner();

        // R1-17: code trống -> 400 VALIDATION_FAILED với thông điệp từ messages.properties.
        mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "val1-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", "   ",
                                "expectedAccountId", buyer.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 100_000L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("code"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Mã sản phẩm không được để trống"));

        // code vượt 100 ký tự -> 400 VALIDATION_FAILED với thông điệp validation.order.code.size.
        mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "val2-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", "X".repeat(101),
                                "expectedAccountId", buyer.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 100_000L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("code"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Mã sản phẩm tối đa 100 ký tự"));

        // voucherCode vượt 100 ký tự -> 400 VALIDATION_FAILED với thông điệp validation.order.voucherCode.size.
        mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "val3-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", "HSA01",
                                "voucherCode", "V".repeat(101),
                                "expectedAccountId", buyer.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 100_000L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("voucherCode"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Mã voucher tối đa 100 ký tự"));
    }

    @Test
    void emitsVnLocaleForVietnameseLearnersAndEnLocaleForEnglishLearners() throws Exception {
        var viLearner = fixtures.createLearner(true, "vi");
        var enLearner = fixtures.createLearner(true, "en");
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);

        String viRaw = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(viLearner))
                        .header("Idempotency-Key", "loc-vi-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "expectedAccountId", viLearner.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 200_000L))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(parseQueryParams(mapper.readTree(viRaw).path("paymentUrl").asText()).get("vnp_Locale"))
                .isEqualTo("vn");

        String enRaw = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(enLearner))
                        .header("Idempotency-Key", "loc-en-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "expectedAccountId", enLearner.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 200_000L))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(parseQueryParams(mapper.readTree(enRaw).path("paymentUrl").asText()).get("vnp_Locale"))
                .isEqualTo("en");
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
