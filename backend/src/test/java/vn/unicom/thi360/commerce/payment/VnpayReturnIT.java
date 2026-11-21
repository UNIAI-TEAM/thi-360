package vn.unicom.thi360.commerce.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.PayRequest;
import vn.unicom.thi360.commerce.payment.gateway.VnpaySigner;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.support.ForbiddenKeysAssert;

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

        ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(okBody);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(okBody);
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

        // 2. Chữ ký sai -> 422 VNPAY_SIGNATURE_INVALID, không lộ orderCode và không cấp quyền, vẫn ghi payment_event RETURN signature_valid=false.
        Map<String, String> badParams = new TreeMap<>(okParams);
        badParams.put("vnp_Amount", "100");
        MockHttpServletRequestBuilder badReq = get("/api/v1/public/payments/vnpay/return");
        badParams.forEach(badReq::param);
        String badBody = mockMvc.perform(badReq)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VNPAY_SIGNATURE_INVALID"))
                .andReturn().getResponse().getContentAsString();
        assertThat(badBody)
                .doesNotContain("orderCode")
                .doesNotContain(order.code());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.entitlement_grant WHERE order_id = ?",
                Integer.class, order.id())).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.payment_event WHERE payment_id = ? AND source = 'RETURN' AND signature_valid = false",
                Integer.class, payment.id())).isEqualTo(1);

        // 2b. R1-2: phát lại paymentUrl (có vnp_Command=pay, thiếu vnp_ResponseCode/TransactionStatus) tới /return -> 422 VNPAY_SIGNATURE_INVALID.
        String payUrl = vnpayGateway.pay(new PayRequest(
                payment.txnRef(), order.code(), 200_000L,
                "Thanh toan don " + order.code(), "vn",
                "http://localhost:3000/checkout/result",
                "127.0.0.1", "20261121100000", "20261121101500"));
        MockHttpServletRequestBuilder replayReq = get("/api/v1/public/payments/vnpay/return");
        parseQueryParams(payUrl).forEach(replayReq::param);
        String replayBody = mockMvc.perform(replayReq)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VNPAY_SIGNATURE_INVALID"))
                .andReturn().getResponse().getContentAsString();
        assertThat(replayBody)
                .doesNotContain("orderCode")
                .doesNotContain(order.code());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.payment_event WHERE payment_id = ? AND source = 'RETURN' AND signature_valid = false AND outcome = 'RETURN_INVALID_CALLBACK_PARAMS'",
                Integer.class, payment.id())).isEqualTo(1);

        // 2c. R1-2: chữ ký hợp lệ nhưng vnp_Amount lệch với payment -> 422 VNPAY_SIGNATURE_INVALID.
        Map<String, String> signedWrongAmount = signedParams(payment.txnRef(), 100_000L, "00", "00");
        MockHttpServletRequestBuilder wrongAmountReq = get("/api/v1/public/payments/vnpay/return");
        signedWrongAmount.forEach(wrongAmountReq::param);
        mockMvc.perform(wrongAmountReq)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VNPAY_SIGNATURE_INVALID"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.payment_event WHERE payment_id = ? AND source = 'RETURN' AND signature_valid = false AND outcome = 'RETURN_INVALID_AMOUNT'",
                Integer.class, payment.id())).isEqualTo(1);

        // 3. R1-27: ưu tiên trạng thái đã lưu (DB FAILED thắng tham số query 00/00 đã ký).
        var failedPayment = fixtures.insertStubPayment(order, 2, 200_000L, PaymentState.FAILED);
        MockHttpServletRequestBuilder failedPrecedenceReq = get("/api/v1/public/payments/vnpay/return");
        signedParams(failedPayment.txnRef(), 200_000L, "00", "00").forEach(failedPrecedenceReq::param);
        mockMvc.perform(failedPrecedenceReq)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderCode").value(order.code()))
                .andExpect(jsonPath("$.paymentState").value("FAILED"));

        // 4. vnp_TxnRef không tồn tại (nhưng chữ ký đúng) -> 404 ORDER_NOT_FOUND, vẫn ghi payment_event RETURN.
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

    private static Map<String, String> parseQueryParams(String url) {
        String query = URI.create(url).getRawQuery();
        Map<String, String> map = new TreeMap<>();
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
            String v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
            map.put(k, v);
        }
        return map;
    }
}
