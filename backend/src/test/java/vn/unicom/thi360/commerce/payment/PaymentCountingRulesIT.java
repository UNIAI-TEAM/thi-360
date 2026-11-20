package vn.unicom.thi360.commerce.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpaySigner;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;

/**
 * Kiểm chứng đầy đủ ma trận đếm khoản thanh toán BS §14 (Ca A, Ca B, Ca C, Ca D và {@code AUTO_EXCESS}).
 */
class PaymentCountingRulesIT extends AbstractIntegrationTest {

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
    void caseA_onlyFirstSucceededPaymentIsCountedLaterSuccessIsMarkedDuplicate() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var order = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var p1 = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.CREATED);
        var p2 = fixtures.insertStubPayment(order, 2, 200_000L, PaymentState.CREATED);

        sendIpn(p1.txnRef(), 200_000L, "00", "00", "94000001").andExpect(jsonPath("$.RspCode").value("00"));
        sendIpn(p2.txnRef(), 200_000L, "00", "00", "94000002").andExpect(jsonPath("$.RspCode").value("00"));

        assertThat(jdbc.queryForObject("SELECT is_duplicate FROM commerce.payment WHERE id = ?", Boolean.class, p1.id()))
                .isFalse();
        assertThat(jdbc.queryForObject("SELECT is_duplicate FROM commerce.payment WHERE id = ?", Boolean.class, p2.id()))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT excess_reason FROM commerce.payment WHERE id = ?", String.class, p2.id()))
                .isEqualTo("AFTER_COUNTED");
        assertThat(jdbc.queryForObject("SELECT attempts_total FROM commerce.entitlement WHERE user_id = ?", Integer.class, buyer.id()))
                .isEqualTo(3);
    }

    @Test
    void caseB_failedAttemptFollowedBySecondAttemptSuccessCountsOnlySecondAttempt() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var order = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var p1 = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.CREATED);
        var p2 = fixtures.insertStubPayment(order, 2, 200_000L, PaymentState.CREATED);

        sendIpn(p1.txnRef(), 200_000L, "24", "02", "0").andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.payment WHERE id = ?", String.class, p1.id()))
                .isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("PENDING_PAYMENT");

        sendIpn(p2.txnRef(), 200_000L, "00", "00", "94000011").andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.payment WHERE id = ?", String.class, p2.id()))
                .isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT is_duplicate FROM commerce.payment WHERE id = ?", Boolean.class, p2.id()))
                .isFalse();
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("FULFILLED");
    }

    @Test
    void caseC_lateSuccessOnFailedPaymentMarksPendingThenAutoExcessWhenAnotherAttemptSucceeds() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var order = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var p1 = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.FAILED);
        var p2 = fixtures.insertStubPayment(order, 2, 200_000L, PaymentState.CREATED);

        // Chưa có khoản được tính -> lần 1 giữ FAILED, late_success_review = PENDING, KHÔNG cấp quyền!
        sendIpn(p1.txnRef(), 200_000L, "00", "00", "94000021").andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.payment WHERE id = ?", String.class, p1.id()))
                .isEqualTo("FAILED");
        assertThat(jdbc.queryForObject(
                "SELECT late_success_review FROM commerce.payment WHERE id = ?", String.class, p1.id()))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject(
                "SELECT needs_review FROM commerce.purchase_order WHERE id = ?", Boolean.class, order.id()))
                .isTrue();

        // Lần 2 về 00/00 -> lần 2 được tính và cấp quyền; lần 1 tự chuyển AUTO_EXCESS + is_duplicate=true!
        sendIpn(p2.txnRef(), 200_000L, "00", "00", "94000022").andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.payment WHERE id = ?", String.class, p1.id()))
                .isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT is_duplicate FROM commerce.payment WHERE id = ?", Boolean.class, p1.id()))
                .isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT late_success_review FROM commerce.payment WHERE id = ?", String.class, p1.id()))
                .isEqualTo("AUTO_EXCESS");
    }

    @Test
    void caseC_lateSuccessArrivingAfterCountedPaymentImmediatelyBecomesAutoExcess() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var order = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var p1 = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.FAILED);
        var p2 = fixtures.insertStubPayment(order, 2, 200_000L, PaymentState.CREATED);

        sendIpn(p2.txnRef(), 200_000L, "00", "00", "94000031").andExpect(jsonPath("$.RspCode").value("00"));
        sendIpn(p1.txnRef(), 200_000L, "00", "00", "94000032").andExpect(jsonPath("$.RspCode").value("00"));

        assertThat(jdbc.queryForObject("SELECT state FROM commerce.payment WHERE id = ?", String.class, p1.id()))
                .isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT is_duplicate FROM commerce.payment WHERE id = ?", Boolean.class, p1.id()))
                .isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT late_success_review FROM commerce.payment WHERE id = ?", String.class, p1.id()))
                .isEqualTo("AUTO_EXCESS");
        assertThat(jdbc.queryForObject(
                "SELECT excess_reason FROM commerce.payment WHERE id = ?", String.class, p1.id()))
                .isEqualTo("AFTER_COUNTED");
    }

    private org.springframework.test.web.servlet.ResultActions sendIpn(
            String txnRef, long amountVnd, String responseCode, String txnStatus, String txnNo) throws Exception {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", Long.toString(amountVnd * 100L));
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", "Thanh toan don test");
        params.put("vnp_PayDate", "20261121100100");
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TmnCode", MockVnpayGateway.DEFAULT_MOCK_TMN_CODE);
        params.put("vnp_TransactionNo", txnNo);
        params.put("vnp_TransactionStatus", txnStatus);
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_SecureHash", VnpaySigner.signPayParams(params, MockVnpayGateway.DEFAULT_MOCK_SECRET));
        MockHttpServletRequestBuilder req = get("/api/v1/public/payments/vnpay/ipn");
        params.forEach(req::param);
        return mockMvc.perform(req).andExpect(status().isOk());
    }
}
