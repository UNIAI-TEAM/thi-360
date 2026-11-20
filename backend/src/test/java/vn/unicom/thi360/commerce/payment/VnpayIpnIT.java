package vn.unicom.thi360.commerce.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.order.infrastructure.OrderHistoryRepository;
import vn.unicom.thi360.commerce.payment.application.VnpayIpnIpAllowlist;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayProperties;
import vn.unicom.thi360.commerce.payment.gateway.VnpaySigner;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;

/**
 * AC-19..AC-29: xác thực chữ ký, {@code txn_ref}, số tiền, mã lỗi VNPAY ({@code FAILED} vs {@code UNKNOWN}),
 * {@code vnp_TransactionNo="0"} nhiều lần thất bại, rollback lỗi nội bộ trả {@code 99}, ghi {@code payment_event},
 * danh sách IP cho phép và miễn giới hạn tần suất của {@code /api/v1/public/**}.
 */
class VnpayIpnIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;

    @MockitoSpyBean
    private OrderHistoryRepository orderHistoryRepository;

    private CommerceTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        reset(orderHistoryRepository);
        fixtures = new CommerceTestFixtures(jdbc, mapper, catalogLookup, promotionRepository, testSecuritySupport);
    }

    @AfterEach
    void tearDown() {
        reset(orderHistoryRepository);
        fixtures.cleanup();
    }

    @Test
    void validatesSignatureTxnRefAmountAndPaymentOutcomeTransitions() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var order = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var p1 = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.CREATED);
        var p2 = fixtures.insertStubPayment(order, 2, 200_000L, PaymentState.CREATED);
        var p3 = fixtures.insertStubPayment(order, 3, 200_000L, PaymentState.CREATED);
        var p4 = fixtures.insertStubPayment(order, 4, 200_000L, PaymentState.CREATED);

        // 1. Sai chữ ký -> RspCode 97 + vẫn ghi payment_event.
        Map<String, String> badSig = signedParams(p1.txnRef(), 200_000L, "00", "00", "95000001");
        badSig.put("vnp_SecureHash", "0000bad0000signature");
        sendIpn(mockMvc, badSig, "198.51.100.1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("97"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.payment_event WHERE payment_id = ? AND signature_valid = false",
                Integer.class, p1.id())).isEqualTo(1);

        // 2. Không có txn_ref -> RspCode 01.
        sendIpn(mockMvc, signedParams("DH000000ZZZZZZ01", 200_000L, "00", "00", "95000002"), "198.51.100.1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("01"));

        // 3. Sai số tiền -> RspCode 04.
        sendIpn(mockMvc, signedParams(p1.txnRef(), 199_000L, "00", "00", "95000003"), "198.51.100.1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("04"));

        // 4. Mã thất bại 24 kèm vnp_TransactionNo="0" (hai lần ở p1 & p2 đều không đụng unique index).
        sendIpn(mockMvc, signedParams(p1.txnRef(), 200_000L, "24", "02", "0"), "198.51.100.1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"));
        sendIpn(mockMvc, signedParams(p2.txnRef(), 200_000L, "51", "02", "0"), "198.51.100.1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.payment WHERE id = ?", String.class, p1.id()))
                .isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.payment WHERE id = ?", String.class, p2.id()))
                .isEqualTo("FAILED");

        // 5. Mã mơ hồ / chưa có trong bảng lỗi (07, 99, 88) -> UNKNOWN (KHÔNG biến thành FAILED!).
        sendIpn(mockMvc, signedParams(p3.txnRef(), 200_000L, "07", "07", "0"), "198.51.100.1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.payment WHERE id = ?", String.class, p3.id()))
                .isEqualTo("UNKNOWN");

        // 6. Thành công 00/00 -> 00, cấp quyền, và gọi lặp lại trả 02 (idempotent).
        sendIpn(mockMvc, signedParams(p4.txnRef(), 200_000L, "00", "00", "95000004"), "198.51.100.1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject(
                "SELECT attempts_total FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                Integer.class, buyer.id(), product.examId())).isEqualTo(3);

        sendIpn(mockMvc, signedParams(p4.txnRef(), 200_000L, "00", "00", "95000004"), "198.51.100.1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("02"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.entitlement_grant WHERE order_id = ?",
                Integer.class, order.id())).isEqualTo(1);

        // AC-20: sai số tiền trên lần đã SUCCEEDED hoặc FAILED vẫn trả 04 (kiểm tra số tiền trước trạng thái).
        sendIpn(mockMvc, signedParams(p4.txnRef(), 199_000L, "00", "00", "95000004"), "198.51.100.1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("04"));
        sendIpn(mockMvc, signedParams(p1.txnRef(), 199_000L, "00", "00", "95000005"), "198.51.100.1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("04"));

        // AC-25: thiếu tham số vẫn trả HTTP 200 JSON {RspCode, Message}; ipn-allowed-ips rỗng cho phép mọi IP.
        mockMvc.perform(get("/api/v1/public/payments/vnpay/ipn")
                        .with(r -> {
                            r.setRemoteAddr("203.0.113.77");
                            return r;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("97"))
                .andExpect(jsonPath("$.Message").isNotEmpty());
    }

    @Test
    void mapsAllTenClearFailureCodesToFailedAndAmbiguousCodesToUnknown() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var order = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);

        List<String> clearFailureCodes = List.of("09", "10", "11", "12", "13", "24", "51", "65", "75", "79");
        int seq = 1;
        for (String code : clearFailureCodes) {
            var p = fixtures.insertStubPayment(order, seq++, 200_000L, PaymentState.CREATED);
            sendIpn(mockMvc, signedParams(p.txnRef(), 200_000L, code, "02", "0"), "203.0.113.88")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.RspCode").value("00"));
            assertThat(jdbc.queryForObject("SELECT state FROM commerce.payment WHERE id = ?", String.class, p.id()))
                    .as("responseCode %s must map to FAILED", code)
                    .isEqualTo("FAILED");
        }

        for (String unknownCode : List.of("07", "99", "88")) {
            var p = fixtures.insertStubPayment(order, seq++, 200_000L, PaymentState.CREATED);
            sendIpn(mockMvc, signedParams(p.txnRef(), 200_000L, unknownCode, "07", "0"), "203.0.113.88")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.RspCode").value("00"));
            assertThat(jdbc.queryForObject("SELECT state FROM commerce.payment WHERE id = ?", String.class, p.id()))
                    .as("responseCode %s must map to UNKNOWN", unknownCode)
                    .isEqualTo("UNKNOWN");
        }

        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void internalErrorRollsBackCompletelyReturns99AndSubsequentRetrySucceedsOnce() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var order = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var payment = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.CREATED);

        doThrow(new RuntimeException("Simulated transient DB failure"))
                .when(orderHistoryRepository).save(any());

        Map<String, String> params = signedParams(payment.txnRef(), 200_000L, "00", "00", "95000077");
        sendIpn(mockMvc, params, "198.51.100.1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("99"))
                .andExpect(jsonPath("$.Message").value("Unknown error"));

        // Transaction phải rollback hoàn toàn: payment còn CREATED, order còn PENDING_PAYMENT, 0 grants.
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.payment WHERE id = ?", String.class, payment.id()))
                .isEqualTo("CREATED");
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.entitlement_grant WHERE order_id = ?", Integer.class, order.id()))
                .isZero();

        reset(orderHistoryRepository);

        // VNPAY gọi lại -> 00 và chỉ tạo đúng 1 grant.
        sendIpn(mockMvc, params, "198.51.100.1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.entitlement_grant WHERE order_id = ?", Integer.class, order.id()))
                .isEqualTo(1);
    }

    @Test
    void handles130SequentialIpnCallsWithout429UnderDefaultRateLimit() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 1);
        var order = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var payment = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.CREATED);

        Map<String, String> params = signedParams(payment.txnRef(), 200_000L, "00", "00", "95000130");
        for (int i = 0; i < 130; i++) {
            String expected = i == 0 ? "00" : "02";
            sendIpn(mockMvc, params, "198.51.100.200")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.RspCode").value(expected));
        }
    }

    @Test
    void rejectsInvalidAllowlistConfigAtStartup() {
        VnpayProperties invalid = new VnpayProperties(
                "MOCK", "", "", "", "", List.of("not-a-valid-ip"), null, null, null);
        assertThatThrownBy(() -> new VnpayIpnIpAllowlist(invalid))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Nested
    @TestPropertySource(properties = {
        "thi360.vnpay.ipn-allowed-ips=203.0.113.10,198.51.100.0/24",
        "thi360.security.rate-limit.public-per-ip-limit=3"
    })
    class AllowlistAndRateLimitExemption {

        @Autowired
        private MockMvc scopedMockMvc;

        @Test
        void enforcesIpAllowlistAndExemptsIpnFromPublicRateLimit() throws Exception {
            var buyer = fixtures.createLearner();
            var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 1);
            var order = fixtures.insertStubOrder(
                    buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                    200_000L, Instant.now().plusSeconds(900), null);
            var payment = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.CREATED);

            Map<String, String> params = signedParams(payment.txnRef(), 200_000L, "00", "00", "95000099");

            // IP ngoài allowlist -> 97 và ghi payment_event; trạng thái chưa đổi.
            sendIpn(scopedMockMvc, params, "192.0.2.50")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.RspCode").value("97"));
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM commerce.payment_event WHERE payment_id = ? AND outcome = 'IP_REJECTED_97'",
                    Integer.class, payment.id())).isEqualTo(1);

            // Header X-Forwarded-For giả mạo từ nguồn không tin cậy (192.0.2.50) vẫn bị từ chối với 97!
            MockHttpServletRequestBuilder spoofed = get("/api/v1/public/payments/vnpay/ipn")
                    .header("X-Forwarded-For", "203.0.113.10")
                    .with(r -> {
                        r.setRemoteAddr("192.0.2.50");
                        return r;
                    });
            params.forEach(spoofed::param);
            scopedMockMvc.perform(spoofed)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.RspCode").value("97"));

            // IP trong allowlist và gọi vượt ngưỡng public-per-ip-limit=3 vẫn luôn 200 (miễn giới hạn tần suất, AC-29).
            for (int i = 0; i < 6; i++) {
                String expected = i == 0 ? "00" : "02";
                sendIpn(scopedMockMvc, params, "198.51.100.42")
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.RspCode").value(expected));
            }
        }
    }

    private static org.springframework.test.web.servlet.ResultActions sendIpn(
            MockMvc mvc, Map<String, String> params, String remoteAddr) throws Exception {
        MockHttpServletRequestBuilder req = get("/api/v1/public/payments/vnpay/ipn")
                .with(r -> {
                    r.setRemoteAddr(remoteAddr);
                    return r;
                });
        params.forEach(req::param);
        return mvc.perform(req);
    }

    private static Map<String, String> signedParams(
            String txnRef, long amountVnd, String responseCode, String txnStatus, String txnNo) {
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
        return params;
    }
}
