package vn.unicom.thi360.commerce.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;

/**
 * AC-37 & AC-38: cấp quyền đủ lượt theo từng dòng, idempotent, bỏ qua khi {@link RefundProgress} báo đang hoàn tiền,
 * và ghi lại lỗi khi grant thất bại để retry.
 */
class FulfillmentServiceIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private FulfillmentService fulfillmentService;

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
}
