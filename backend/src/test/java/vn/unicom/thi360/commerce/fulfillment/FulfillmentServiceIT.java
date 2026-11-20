package vn.unicom.thi360.commerce.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.fulfillment.application.FulfillmentService;
import vn.unicom.thi360.commerce.fulfillment.application.RefundProgress;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.promotion.application.PromotionRedemptionService;
import vn.unicom.thi360.commerce.promotion.application.PromotionRedemptionService.ReserveRequest;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.shared.outbox.OutboxRelay;

/**
 * AC-37 & AC-38: cấp quyền đủ lượt theo từng dòng, idempotent, bỏ qua khi {@link RefundProgress} báo đang hoàn tiền,
 * và ghi lại lỗi khi grant thất bại để retry.
 */
class FulfillmentServiceIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private PromotionRedemptionService promotionRedemptionService;
    @Autowired private FulfillmentService fulfillmentService;
    @Autowired private OutboxRelay outboxRelay;

    @MockitoBean
    private RefundProgress refundProgress;

    @MockitoSpyBean
    private EntitlementGrantService entitlementGrantService;

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
    void fulfillsPaidOrderIdempotentlySkipsRefundedAndRecordsTransientFailure() {
        var buyer = fixtures.createLearner();
        var p1 = fixtures.insertOnSaleProduct("HSA", 200_000L, 4);
        var p2 = fixtures.insertOnSaleProduct("QDA", 200_000L, 2);

        var okOrder = fixtures.insertStubOrder(
                buyer, p1, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        when(refundProgress.hasNonRejectedRefund(okOrder.id())).thenReturn(false);

        assertThat(fulfillmentService.fulfill(okOrder.id())).isTrue();
        assertThat(fulfillmentService.fulfill(okOrder.id())).isTrue(); // idempotent call #2

        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, okOrder.id()))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject(
                "SELECT attempts_total FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                Integer.class, buyer.id(), p1.examId())).isEqualTo(4);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.entitlement_grant WHERE order_id = ?", Integer.class, okOrder.id()))
                .isEqualTo(1);

        // Đơn đang có refund chưa bị từ chối -> bỏ qua, giữ PAID.
        var refundOrder = fixtures.insertStubOrder(
                buyer, p2, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        when(refundProgress.hasNonRejectedRefund(refundOrder.id())).thenReturn(true);
        assertThat(fulfillmentService.fulfill(refundOrder.id())).isFalse();
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, refundOrder.id()))
                .isEqualTo("PAID");

        // Khi grant lỗi -> tăng fulfillment_tries, lưu fulfillment_error, giữ PAID; thử lại khi hết lỗi -> FULFILLED.
        when(refundProgress.hasNonRejectedRefund(refundOrder.id())).thenReturn(false);
        AtomicBoolean fail = new AtomicBoolean(true);
        doAnswer(inv -> {
            if (fail.get()) {
                throw new IllegalStateException("Simulated grant failure");
            }
            return inv.callRealMethod();
        }).when(entitlementGrantService).grantForOrder(any());

        assertThat(fulfillmentService.fulfill(refundOrder.id())).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT fulfillment_tries FROM commerce.purchase_order WHERE id = ?", Integer.class, refundOrder.id()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT fulfillment_error FROM commerce.purchase_order WHERE id = ?", String.class, refundOrder.id()))
                .contains("Simulated grant failure");

        fail.set(false);
        assertThat(fulfillmentService.fulfill(refundOrder.id())).isTrue();
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, refundOrder.id()))
                .isEqualTo("FULFILLED");
    }

    @Test
    void confirmsReservedRedemptionForwardsGrantSnapshotAndAccumulatesRepeatPurchase() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 4);
        var autoPromo = fixtures.createAutoPromotion(
                "AC29 Promo", DiscountType.AMOUNT, 50_000L, null,
                Instant.now().minusSeconds(600), Instant.now().plusSeconds(3600),
                5, PromotionScope.ALL);

        // AC-29: đơn PAID mang RESERVED promotion_redemption cùng option_codes, benefits, result_view đầy đủ.
        var order1 = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 150_000L, null, Instant.now());
        jdbc.update("""
                UPDATE commerce.purchase_order
                SET promotion_id = ?, promotion_discount_vnd = 50000, discount_applied = 'PROMOTION_ONLY',
                    option_codes = ARRAY['OPT-X', 'OPT-Y']::text[],
                    benefits = '{"aiExplanation":true,"strengthWeakness":false,"practice":true}'::jsonb,
                    result_view = 'SCORE_ONLY'
                WHERE id = ?
                """, autoPromo.getId(), order1.id());
        jdbc.update("""
                UPDATE commerce.purchase_order_item
                SET option_codes = ARRAY['OPT-X', 'OPT-Y']::text[],
                    benefits = '{"aiExplanation":true,"strengthWeakness":false,"practice":true}'::jsonb,
                    result_view = 'SCORE_ONLY'
                WHERE order_id = ?
                """, order1.id());
        promotionRedemptionService.reserveForOrder(
                buyer.id(), order1.id(), order1.code(), List.of(new ReserveRequest(autoPromo.getId(), 50_000L)));
        when(refundProgress.hasNonRejectedRefund(any())).thenReturn(false);

        assertThat(fulfillmentService.fulfill(order1.id())).isTrue();

        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.promotion_redemption WHERE order_id = ? AND promotion_id = ?",
                String.class, order1.id(), autoPromo.getId()))
                .isEqualTo("CONFIRMED");
        String grantOptionCodes = jdbc.queryForObject(
                "SELECT array_to_string(option_codes, ',') FROM commerce.entitlement_grant WHERE order_id = ?",
                String.class, order1.id());
        assertThat(grantOptionCodes).isEqualTo("OPT-X,OPT-Y");
        String grantBenefitsJson = jdbc.queryForObject(
                "SELECT benefits::text FROM commerce.entitlement_grant WHERE order_id = ?",
                String.class, order1.id());
        JsonNode grantBenefits = mapper.readTree(grantBenefitsJson);
        assertThat(grantBenefits.path("aiExplanation").asBoolean()).isTrue();
        assertThat(grantBenefits.path("strengthWeakness").asBoolean()).isFalse();
        assertThat(grantBenefits.path("practice").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT result_view FROM commerce.entitlement_grant WHERE order_id = ?",
                String.class, order1.id()))
                .isEqualTo("SCORE_ONLY");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.order_history WHERE order_id = ? AND from_state = 'PAID' AND to_state = 'FULFILLED'",
                Integer.class, order1.id()))
                .isEqualTo(1);
        while (outboxRelay.relayBatch() > 0) {
            // Đẩy hết sự kiện audit sang bảng audit.audit_event.
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit.audit_event WHERE module = 'commerce' AND action = 'ENTITLEMENT_GRANTED' AND target_id = ?",
                Integer.class, order1.id().toString()))
                .isEqualTo(1);

        // AC-40: mua thêm lần thứ 2 cùng (buyer, product) giữ nguyên 1 dòng entitlement với attempts_total cộng dồn và 2 grant.
        var order2 = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        jdbc.update("UPDATE commerce.purchase_order SET attempts_granted = 3 WHERE id = ?", order2.id());
        jdbc.update("UPDATE commerce.purchase_order_item SET attempts_granted = 3 WHERE order_id = ?", order2.id());

        assertThat(fulfillmentService.fulfill(order2.id())).isTrue();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                Integer.class, buyer.id(), product.examId()))
                .isEqualTo(1);
        UUID entitlementId = jdbc.queryForObject(
                "SELECT id FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                UUID.class, buyer.id(), product.examId());
        assertThat(jdbc.queryForObject(
                "SELECT attempts_total FROM commerce.entitlement WHERE id = ?",
                Integer.class, entitlementId))
                .isEqualTo(7);
        List<UUID> grantOrderIds = jdbc.queryForList(
                "SELECT order_id FROM commerce.entitlement_grant WHERE entitlement_id = ?",
                UUID.class, entitlementId);
        assertThat(grantOrderIds).hasSize(2).containsExactlyInAnyOrder(order1.id(), order2.id());
    }
}
