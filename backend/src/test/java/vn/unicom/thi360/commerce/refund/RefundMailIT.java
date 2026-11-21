package vn.unicom.thi360.commerce.refund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.fulfillment.application.FulfillmentService;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.QueryDrResult;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.RefundResult;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.security.SecurityProperties;

/**
 * AC-25: chỉ xếp hàng thư {@code REFUND_DONE} cho tài khoản đã xác minh email ({@code emailVerified=true})
 * với đúng ngôn ngữ {@code vi}/{@code en} qua hoàn API, `/query` -> {@code SUCCEEDED}, và hoàn thủ công {@code MANUAL}.
 */
class RefundMailIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private FulfillmentService fulfillmentService;
    @Autowired private SecurityProperties securityProperties;

    @MockitoSpyBean
    private VnpayGateway vnpayGateway;

    private CommerceTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        Mockito.reset(vnpayGateway);
        fixtures = new CommerceTestFixtures(jdbc, mapper, catalogLookup, promotionRepository, testSecuritySupport);
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(vnpayGateway);
        jdbc.update("DELETE FROM commerce.refund");
        fixtures.cleanup();
    }

    @Test
    void queuesLocalizedRefundDoneMailOnlyForEmailVerifiedLearnersAcrossAllSucceededPaths() throws Exception {
        var viVerifiedApi = fixtures.createLearner(true, "vi");
        var enVerifiedQuery = fixtures.createLearner(true, "en");
        var viVerifiedManual = fixtures.createLearner(true, "vi");
        var unverified = fixtures.createLearner(false, "vi");
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        UUID adminId = UUID.randomUUID();

        // 1. Tài khoản vi đã xác minh -> hoàn API thành công ngay -> xếp 1 thư vi.
        var oVi = fixtures.insertStubOrder(
                viVerifiedApi, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var pVi = insertSucceededPayment(oVi, 1, 200_000L, "90070011");
        assertThat(fulfillmentService.fulfill(oVi.id())).isTrue();
        createRefund(adminId, oVi.code(), pVi.id(), 200_000L, "API", null, "SUCCEEDED");

        Map<String, Object> viMail = jdbc.queryForMap(
                "SELECT kind, to_email, locale, subject, text, html FROM shared.email_message WHERE user_id = ? AND kind = 'REFUND_DONE'",
                viVerifiedApi.id());
        assertThat(viMail.get("kind")).isEqualTo("REFUND_DONE");
        assertThat(viMail.get("locale")).isEqualTo("vi");
        assertThat(viMail.get("to_email")).isEqualTo(viVerifiedApi.email());
        assertThat(viMail.get("text").toString()).contains(oVi.code()).contains("200.000");

        // 2. Tài khoản en đã xác minh -> timeout UNKNOWN (0 thư), sau đó /query trả 00/05 -> xếp 1 thư en.
        var oEn = fixtures.insertStubOrder(
                enVerifiedQuery, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var pEn = insertSucceededPayment(oEn, 1, 200_000L, "90070012");
        doThrow(new RuntimeException("Timeout")).when(vnpayGateway).refund(any());
        String qRefundId = createRefund(adminId, oEn.code(), pEn.id(), 200_000L, "API", null, "UNKNOWN");
        assertThat(mailCount(enVerifiedQuery.id())).isZero();

        doReturn(queryDrResult(pEn.txnRef(), 200_000L, "90070012")).when(vnpayGateway).queryDr(any());
        mockMvc.perform(post("/api/v1/admin/refunds/" + qRefundId + "/query")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SUCCEEDED"));

        Map<String, Object> enMail = jdbc.queryForMap(
                "SELECT kind, to_email, locale, subject, text, html FROM shared.email_message WHERE user_id = ?",
                enVerifiedQuery.id());
        assertThat(enMail.get("kind")).isEqualTo("REFUND_DONE");
        assertThat(enMail.get("locale")).isEqualTo("en");
        assertThat(enMail.get("to_email")).isEqualTo(enVerifiedQuery.email());
        assertThat(enMail.get("text").toString()).contains(oEn.code()).contains("200,000 VND");

        // 3. Tài khoản vi đã xác minh -> API FAILED (0 thư), sau đó MANUAL SUCCEEDED -> 1 thư REFUND_DONE.
        var oMan = fixtures.insertStubOrder(
                viVerifiedManual, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var pMan = insertSucceededPayment(oMan, 1, 200_000L, "90070013");
        doReturn(refundResult("91", "09", pMan.txnRef(), 200_000L, "90070013")).when(vnpayGateway).refund(any());
        createRefund(adminId, oMan.code(), pMan.id(), 200_000L, "API", null, "FAILED");
        assertThat(mailCount(viVerifiedManual.id())).isZero();

        createRefund(adminId, oMan.code(), pMan.id(), 200_000L, "MANUAL", "VCB-MAIL-1", "SUCCEEDED");
        assertThat(mailCount(viVerifiedManual.id())).isEqualTo(1);

        // 4. Tài khoản chưa xác minh email -> 0 thư nhưng đơn vẫn chuyển REFUNDED.
        Mockito.reset(vnpayGateway);
        var oUnverified = fixtures.insertStubOrder(
                unverified, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var pUnverified = insertSucceededPayment(oUnverified, 1, 200_000L, "90070014");
        assertThat(fulfillmentService.fulfill(oUnverified.id())).isTrue();
        createRefund(adminId, oUnverified.code(), pUnverified.id(), 200_000L, "API", null, "SUCCEEDED");

        assertThat(mailCount(unverified.id())).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, oUnverified.id()))
                .isEqualTo("REFUNDED");
    }

    private int mailCount(UUID userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM shared.email_message WHERE user_id = ? AND kind = 'REFUND_DONE'",
                Integer.class, userId);
        return count == null ? 0 : count;
    }

    private CommerceTestFixtures.StubPayment insertSucceededPayment(
            CommerceTestFixtures.StubOrder order, int seq, long amountVnd, String txnNo) {
        var payment = fixtures.insertStubPayment(order, seq, amountVnd, PaymentState.SUCCEEDED);
        jdbc.update("""
                UPDATE commerce.payment
                SET vnp_transaction_no = ?, bank_code = 'NCB', pay_date = '20261121100100',
                    response_code = '00', transaction_status = '00', updated_at = now()
                WHERE id = ?
                """, txnNo, payment.id());
        return payment;
    }

    private String createRefund(
            UUID adminId, String orderCode, UUID paymentId, long amountVnd, String kind,
            String externalRef, String expectedState) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amountVnd", amountVnd);
        body.put("kind", kind);
        body.put("reason", "Kiem tra thu hoan tien");
        body.put("verifiedSystemError", true);
        if (externalRef != null) {
            body.put("externalRef", externalRef);
        }
        String raw = mockMvc.perform(post("/api/v1/admin/orders/" + orderCode + "/payments/" + paymentId + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "mail-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value(expectedState))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return mapper.readTree(raw).path("id").asText();
    }

    private static RefundResult refundResult(
            String responseCode, String txStatus, String txnRef, long amountVnd, String txnNo) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("vnp_ResponseCode", responseCode);
        raw.put("vnp_TransactionStatus", txStatus);
        raw.put("vnp_TmnCode", MockVnpayGateway.DEFAULT_MOCK_TMN_CODE);
        return new RefundResult(
                true, "req-" + UUID.randomUUID(), responseCode, "Mock " + responseCode,
                txnRef, amountVnd, "NCB", "20261121100500", txnNo, "02", txStatus, "Refund test", raw);
    }

    private static QueryDrResult queryDrResult(String txnRef, long amountVnd, String txnNo) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("vnp_ResponseCode", "00");
        raw.put("vnp_TransactionStatus", "05");
        raw.put("vnp_TmnCode", MockVnpayGateway.DEFAULT_MOCK_TMN_CODE);
        return new QueryDrResult(
                true, "qreq-" + UUID.randomUUID(), "00", "Success",
                txnRef, amountVnd, "NCB", "20261121100500", txnNo, "02", "05", "QueryDr", "", "", raw);
    }
}
