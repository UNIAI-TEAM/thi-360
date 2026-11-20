package vn.unicom.thi360.commerce.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.fulfillment.application.FulfillmentRetryWorker;
import vn.unicom.thi360.commerce.fulfillment.application.RefundProgress;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpaySigner;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;

/**
 * AC-30 & AC-37: khi cấp quyền sau IPN lỗi, đơn giữ {@code PAID}, lưu {@code fulfillment_error}, tăng
 * {@code fulfillment_tries}, IPN vẫn trả {@code 00}; {@link FulfillmentRetryWorker} thử lại trong 30 phút
 * và bỏ qua đơn quá hạn; khi {@code thi360.commerce.scheduling.enabled=false} thì không tạo bean worker.
 */
@TestPropertySource(properties = "thi360.commerce.scheduling.enabled=true")
class FulfillmentRetryWorkerIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private FulfillmentRetryWorker retryWorker;

    @MockitoSpyBean
    private EntitlementGrantService entitlementGrantService;

    @MockitoSpyBean
    private RefundProgress refundProgress;

    private CommerceTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        reset(entitlementGrantService, refundProgress);
        fixtures = new CommerceTestFixtures(jdbc, mapper, catalogLookup, promotionRepository, testSecuritySupport);
    }

    @AfterEach
    void tearDown() {
        reset(entitlementGrantService, refundProgress);
        fixtures.cleanup();
    }

    @Test
    void retriesPaidOrdersWithin30MinuteWindowAndSkipsOlderPaidOrders() {
        var buyer = fixtures.createLearner();
        var recentProduct = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var staleProduct = fixtures.insertOnSaleProduct("QDA", 200_000L, 3);

        var recentPaid = fixtures.insertStubOrder(
                buyer, recentProduct, OrderState.PAID, PaymentMethod.VNPAY,
                200_000L, null, Instant.now().minus(5, ChronoUnit.MINUTES));
        var stalePaid = fixtures.insertStubOrder(
                buyer, staleProduct, OrderState.PAID, PaymentMethod.VNPAY,
                200_000L, null, Instant.now().minus(35, ChronoUnit.MINUTES));

        retryWorker.retryPendingFulfillments();

        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, recentPaid.id()))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, stalePaid.id()))
                .isEqualTo("PAID");
    }

    @Test
    void ipnStillReturns00WhenFulfillmentFailsAndWorkerCompletesRecovery() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var order = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var payment = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.CREATED);

        AtomicBoolean failGrant = new AtomicBoolean(true);
        doAnswer(inv -> {
            if (failGrant.get()) {
                throw new IllegalStateException("Simulated grant failure during IPN");
            }
            return inv.callRealMethod();
        }).when(entitlementGrantService).grantForOrder(any());

        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", Long.toString(200_000L * 100L));
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", "Thanh toan don test");
        params.put("vnp_PayDate", "20261121100100");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TmnCode", MockVnpayGateway.DEFAULT_MOCK_TMN_CODE);
        params.put("vnp_TransactionNo", "96000001");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TxnRef", payment.txnRef());
        params.put("vnp_SecureHash", VnpaySigner.signPayParams(params, MockVnpayGateway.DEFAULT_MOCK_SECRET));
        MockHttpServletRequestBuilder req = get("/api/v1/public/payments/vnpay/ipn");
        params.forEach(req::param);

        mockMvc.perform(req)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"));

        assertThat(jdbc.queryForObject("SELECT state FROM commerce.payment WHERE id = ?", String.class, payment.id()))
                .isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("PAID");
        assertThat(jdbc.queryForObject(
                "SELECT fulfillment_tries FROM commerce.purchase_order WHERE id = ?", Integer.class, order.id()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT fulfillment_error FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .contains("Simulated grant failure during IPN");

        failGrant.set(false);
        retryWorker.retryPendingFulfillments();

        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject(
                "SELECT fulfillment_error FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isNull();
        assertThat(jdbc.queryForObject(
                "SELECT attempts_total FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                Integer.class, buyer.id(), product.examId())).isEqualTo(3);
    }

    @Test
    void doesNotStarveHealthyOrderBehind20RefundBlockedOrdersWithinRetryWindow() {
        var healthyBuyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);

        Set<UUID> blockedOrderIds = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            var blockedBuyer = fixtures.createLearner();
            var blocked = fixtures.insertStubOrder(
                    blockedBuyer, product, OrderState.PAID, PaymentMethod.VNPAY,
                    200_000L, null, Instant.now().minus(10, ChronoUnit.MINUTES).minusSeconds(20L - i));
            blockedOrderIds.add(blocked.id());
        }
        var healthyNewer = fixtures.insertStubOrder(
                healthyBuyer, product, OrderState.PAID, PaymentMethod.VNPAY,
                200_000L, null, Instant.now().minus(1, ChronoUnit.MINUTES));

        doAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return blockedOrderIds.contains(id);
        }).when(refundProgress).hasNonRejectedRefund(any());

        retryWorker.retryPendingFulfillments();

        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, healthyNewer.id()))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject(
                "SELECT attempts_total FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                Integer.class, healthyBuyer.id(), product.examId()))
                .isEqualTo(2);
        for (UUID blockedId : blockedOrderIds) {
            assertThat(jdbc.queryForObject(
                    "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, blockedId))
                    .isEqualTo("PAID");
        }
    }

    @Nested
    @TestPropertySource(properties = "thi360.commerce.scheduling.enabled=false")
    class SchedulingDisabled {

        @Autowired
        private ApplicationContext disabledContext;

        @Test
        void fulfillmentRetryWorkerBeanIsAbsentWhenSchedulingDisabled() {
            assertThat(disabledContext.getBeansOfType(FulfillmentRetryWorker.class)).isEmpty();
        }
    }
}
