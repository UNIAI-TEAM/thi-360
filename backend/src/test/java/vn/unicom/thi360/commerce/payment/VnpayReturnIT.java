package vn.unicom.thi360.commerce.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
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
import vn.unicom.thi360.shared.security.AuthCookieService;

/**
 * AC-22: {@code GET /api/v1/public/payments/vnpay/return} công khai — chữ ký đúng trả {@code {orderCode, paymentState}}
 * không đổi trạng thái đơn/giao dịch và không lộ thông tin người mua; chữ ký sai trả 422 {@code VNPAY_SIGNATURE_INVALID};
 * luôn ghi {@code payment_event} nguồn {@code RETURN}.
 */
class VnpayReturnIT extends AbstractIntegrationTest {

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
    void verifiesReturnSignatureWithoutMutatingOrderOrLeakingBuyerPii() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var order = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var payment = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.CREATED);

        // 1. Chữ ký đúng + có cookie hết hạn vẫn hoạt động công khai, trả đúng orderCode & paymentState, không mutate DB.
        Map<String, String> okParams = signedParams(payment.txnRef(), 200_000L, "00", "00");
        MockHttpServletRequestBuilder okReq = get("/api/v1/public/payments/vnpay/return")
                .cookie(new Cookie(AuthCookieService.ACCESS_TOKEN_COOKIE, "stale.expired.jwt"));
        okParams.forEach(okReq::param);
        String okBody = mockMvc.perform(okReq)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderCode").value(order.code()))
                .andExpect(jsonPath("$.paymentState").value("SUCCEEDED"))
                .andReturn().getResponse().getContentAsString();

        assertThat(okBody)
                .doesNotContain(buyer.id().toString())
                .doesNotContain(buyer.username())
                .doesNotContain(buyer.email());
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.payment WHERE id = ?", String.class, payment.id()))
                .isEqualTo("CREATED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.payment_event WHERE payment_id = ? AND source = 'RETURN' AND signature_valid = true",
                Integer.class, payment.id())).isEqualTo(1);

        // 2. Chữ ký sai -> 422 VNPAY_SIGNATURE_INVALID nhưng vẫn ghi payment_event RETURN signature_valid=false.
        Map<String, String> badParams = new TreeMap<>(okParams);
        badParams.put("vnp_Amount", "100");
        MockHttpServletRequestBuilder badReq = get("/api/v1/public/payments/vnpay/return");
        badParams.forEach(badReq::param);
        mockMvc.perform(badReq)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VNPAY_SIGNATURE_INVALID"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.payment_event WHERE payment_id = ? AND source = 'RETURN' AND signature_valid = false",
                Integer.class, payment.id())).isEqualTo(1);

        // 3. vnp_TxnRef không tồn tại (nhưng chữ ký đúng) -> 404 ORDER_NOT_FOUND, vẫn ghi payment_event RETURN.
        Map<String, String> missingParams = signedParams("DH999999ZZZZZZ01", 200_000L, "00", "00");
        MockHttpServletRequestBuilder missingReq = get("/api/v1/public/payments/vnpay/return");
        missingParams.forEach(missingReq::param);
        mockMvc.perform(missingReq)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    private static Map<String, String> signedParams(
            String txnRef, long amountVnd, String responseCode, String txnStatus) {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", Long.toString(amountVnd * 100L));
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", "Thanh toan don test");
        params.put("vnp_PayDate", "20261121100100");
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TmnCode", MockVnpayGateway.DEFAULT_MOCK_TMN_CODE);
        params.put("vnp_TransactionNo", "92000001");
        params.put("vnp_TransactionStatus", txnStatus);
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_SecureHash", VnpaySigner.signPayParams(params, MockVnpayGateway.DEFAULT_MOCK_SECRET));
        return params;
    }
}
