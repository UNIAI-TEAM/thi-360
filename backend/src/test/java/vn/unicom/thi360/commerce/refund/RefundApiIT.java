package vn.unicom.thi360.commerce.refund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.RefundRequest;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.RefundResult;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.LearnerAccount;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.StubOrder;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.StubPayment;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.security.SecurityProperties;

/**
 * Kiểm thử tích hợp cho API tạo và xem chi tiết hoàn tiền (AC-7, AC-8, AC-9, AC-11, AC-12, GET /refunds/{id}).
 */
class RefundApiIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private FulfillmentService fulfillmentService;
    @Autowired private SecurityProperties securityProperties;
    @Autowired private OutboxRelay outboxRelay;

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
        cleanRefundData();
        fixtures.cleanup();
    }

    @Test
    void fullApiRefundSendsType02RecordsScaledPaymentEventWritesAuditAndGetReturnsSame() throws Exception {
        LearnerAccount buyer = fixtures.createLearner(true, "vi");
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        StubOrder order = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        StubPayment payment = insertSucceededPayment(order, 1, 200_000L, "90010011");
        assertThat(fulfillmentService.fulfill(order.id())).isTrue();

        UUID adminId = UUID.randomUUID();
        String responseJson = mockMvc.perform(post("/api/v1/admin/orders/" + order.code()
                        + "/payments/" + payment.id() + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND, Permissions.ORDER_VIEW))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(200_000L, "API", "Khach yeu cau hoan toan bo", true, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(order.id().toString()))
                .andExpect(jsonPath("$.orderCode").value(order.code()))
                .andExpect(jsonPath("$.paymentId").value(payment.id().toString()))
                .andExpect(jsonPath("$.paymentTxnRef").value(payment.txnRef()))
                .andExpect(jsonPath("$.amountVnd").value(200_000L))
                .andExpect(jsonPath("$.kind").value("API"))
                .andExpect(jsonPath("$.state").value("SUCCEEDED"))
                .andExpect(jsonPath("$.vnpResponseCode").value("00"))
                .andExpect(jsonPath("$.vnpTransactionStatus").value("05"))
                .andExpect(jsonPath("$.requestedBy").value("ops-admin"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode created = mapper.readTree(responseJson);
        UUID refundId = UUID.fromString(created.path("id").asText());

        ArgumentCaptor<RefundRequest> reqCaptor = ArgumentCaptor.forClass(RefundRequest.class);
        verify(vnpayGateway, times(1)).refund(reqCaptor.capture());
        RefundRequest captured = reqCaptor.getValue();
        assertThat(captured.transactionType()).isEqualTo("02");
        assertThat(captured.amountVnd()).isEqualTo(200_000L);
        assertThat(captured.txnRef()).isEqualTo(payment.txnRef());
        assertThat(captured.transactionNo()).isEqualTo("90010011");
        assertThat(captured.transactionDate()).isEqualTo("20261121100000");
        assertThat(captured.createBy()).isEqualTo("ops-admin");

        Map<String, Object> paymentEvent = jdbc.queryForMap(
                "SELECT source, signature_valid, outcome, raw->>'vnp_Amount' AS vnp_amount "
                        + "FROM commerce.payment_event WHERE payment_id = ? AND source = 'REFUND_API'",
                payment.id());
        assertThat(paymentEvent.get("signature_valid")).isEqualTo(true);
        assertThat(paymentEvent.get("outcome")).isEqualTo("REFUND_SUCCEEDED_00");
        assertThat(paymentEvent.get("vnp_amount")).isEqualTo("20000000");

        mockMvc.perform(get("/api/v1/admin/refunds/" + refundId)
                        .with(asUser(adminId, "viewer-admin", Permissions.ORDER_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(refundId.toString()))
                .andExpect(jsonPath("$.state").value("SUCCEEDED"))
                .andExpect(jsonPath("$.orderCode").value(order.code()))
                .andExpect(jsonPath("$.amountVnd").value(200_000L));

        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.order_history WHERE order_id = ? AND from_state = 'FULFILLED' AND to_state = 'REFUNDED' AND reason = ?",
                Integer.class, order.id(), "REFUNDED:" + refundId))
                .isEqualTo(1);

        while (outboxRelay.relayBatch() > 0) {
            // Đẩy sự kiện audit.
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit.audit_event WHERE module = 'commerce' AND action = 'REFUND_REQUESTED' AND target_id = ?",
                Integer.class, refundId.toString()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit.audit_event WHERE module = 'commerce' AND action = 'REFUND_SUCCEEDED' AND target_id = ?",
                Integer.class, refundId.toString()))
                .isEqualTo(1);
    }

    @Test
    void partialApiRefundsSendType03RejectOverLimitAndHandleIdempotencyReplayAndConflict() throws Exception {
        LearnerAccount buyer = fixtures.createLearner(true, "vi");
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 4);
        StubOrder order = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        StubPayment payment = insertSucceededPayment(order, 1, 200_000L, "90010022");
        assertThat(fulfillmentService.fulfill(order.id())).isTrue();

        UUID adminId = UUID.randomUUID();
        String firstKey = "partial-1-" + UUID.randomUUID();
        String firstRaw = mockMvc.perform(post("/api/v1/admin/orders/" + order.code()
                        + "/payments/" + payment.id() + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", firstKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(80_000L, "API", "Hoan mot phan lan 1", true, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("SUCCEEDED"))
                .andExpect(jsonPath("$.amountVnd").value(80_000L))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String firstRefundId = mapper.readTree(firstRaw).path("id").asText();

        // Phát lại idempotent cùng khóa và cùng thân: trả lại kết quả cũ, không gọi lại VNPAY.
        mockMvc.perform(post("/api/v1/admin/orders/" + order.code()
                        + "/payments/" + payment.id() + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", firstKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(80_000L, "API", "Hoan mot phan lan 1", true, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(firstRefundId));

        // Tái sử dụng cùng Idempotency-Key nhưng đổi số tiền -> 409 IDEMPOTENCY_KEY_REUSED.
        mockMvc.perform(post("/api/v1/admin/orders/" + order.code()
                        + "/payments/" + payment.id() + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", firstKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(90_000L, "API", "Hoan mot phan lan 1", true, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        // Lần hoàn một phần thứ 2 vẫn dùng transactionType = 03 và đơn vẫn ở FULFILLED.
        mockMvc.perform(post("/api/v1/admin/orders/" + order.code()
                        + "/payments/" + payment.id() + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "partial-2-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(50_000L, "API", "Hoan mot phan lan 2", true, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("SUCCEEDED"))
                .andExpect(jsonPath("$.amountVnd").value(50_000L));

        ArgumentCaptor<RefundRequest> captor = ArgumentCaptor.forClass(RefundRequest.class);
        verify(vnpayGateway, times(2)).refund(captor.capture());
        assertThat(captor.getAllValues()).extracting(RefundRequest::transactionType).containsExactly("03", "03");

        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("FULFILLED");

        // Còn lại 70_000 VND: biên 0, âm -> 400 VALIDATION_FAILED; 70_001 & 80_000 -> 409 REFUND_AMOUNT_EXCEEDED; đúng 70_000 -> 201 SUCCEEDED.
        mockMvc.perform(post("/api/v1/admin/orders/" + order.code()
                        + "/payments/" + payment.id() + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "amt-zero-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(0L, "API", "So tien 0", true, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/admin/orders/" + order.code()
                        + "/payments/" + payment.id() + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "amt-neg-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(-1_000L, "API", "So tien am", true, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // Thiếu hoặc rỗng reason -> 400 VALIDATION_FAILED (AC-12).
        mockMvc.perform(post("/api/v1/admin/orders/" + order.code()
                        + "/payments/" + payment.id() + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "blank-rsn-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(10_000L, "API", "   ", true, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/admin/orders/" + order.code()
                        + "/payments/" + payment.id() + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "null-rsn-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(10_000L, "API", null, true, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/admin/orders/" + order.code()
                        + "/payments/" + payment.id() + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "over-1-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(70_001L, "API", "Vuot 1 dong", true, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_AMOUNT_EXCEEDED"));

        mockMvc.perform(post("/api/v1/admin/orders/" + order.code()
                        + "/payments/" + payment.id() + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "over-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(80_000L, "API", "Vuot so du", true, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_AMOUNT_EXCEEDED"));

        // Thiếu Idempotency-Key -> 400 IDEMPOTENCY_KEY_REQUIRED.
        mockMvc.perform(post("/api/v1/admin/orders/" + order.code()
                        + "/payments/" + payment.id() + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(20_000L, "API", "Thieu khoa", true, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        // Hoàn đúng phần còn lại 70_000 VND -> 201 SUCCEEDED, hoàn tất đơn REFUNDED.
        mockMvc.perform(post("/api/v1/admin/orders/" + order.code()
                        + "/payments/" + payment.id() + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "exact-rem-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(70_000L, "API", "Hoan not so con lai", true, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("SUCCEEDED"))
                .andExpect(jsonPath("$.amountVnd").value(70_000L));
        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("REFUNDED");
    }

    @Test
    void guardsAndGatewayResponsesHandleSystemErrorNonSucceeded9402InvalidSignatureAnd91() throws Exception {
        LearnerAccount buyer = fixtures.createLearner(true, "vi");
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        UUID adminId = UUID.randomUUID();

        // Thiếu xác minh lỗi hệ thống -> 422 REFUND_SYSTEM_ERROR_NOT_VERIFIED.
        StubOrder o1 = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        StubPayment p1 = insertSucceededPayment(o1, 1, 200_000L, "90010031");
        mockMvc.perform(post("/api/v1/admin/orders/" + o1.code() + "/payments/" + p1.id() + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "noverify-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(200_000L, "API", "Chua xac minh", false, null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REFUND_SYSTEM_ERROR_NOT_VERIFIED"));

        // Khoản thanh toán chưa thành công -> 409 REFUND_PAYMENT_NOT_SUCCEEDED.
        StubPayment pCreated = fixtures.insertStubPayment(o1, 2, 200_000L, PaymentState.CREATED);
        mockMvc.perform(post("/api/v1/admin/orders/" + o1.code() + "/payments/" + pCreated.id() + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "notpaid-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(200_000L, "API", "Chua thu", true, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_PAYMENT_NOT_SUCCEEDED"));

        // Mã 94 -> PROCESSING và chặn hoàn mới trên cùng khoản thanh toán.
        doReturn(refundResult(true, "94", "", p1.txnRef(), 200_000L, "90010031", "02"))
                .when(vnpayGateway).refund(any());
        mockMvc.perform(post("/api/v1/admin/orders/" + o1.code() + "/payments/" + p1.id() + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "code94-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(200_000L, "API", "Dang xu ly 94", true, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("PROCESSING"))
                .andExpect(jsonPath("$.vnpResponseCode").value("94"));

        mockMvc.perform(post("/api/v1/admin/orders/" + o1.code() + "/payments/" + p1.id() + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "code94-dup-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(200_000L, "API", "Thu lai khi dang PROCESSING", true, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_ACTIVE_EXISTS"));

        // Mã 02 (AD-116 mặc định UNKNOWN) -> UNKNOWN và chặn hoàn mới.
        StubOrder o2 = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        StubPayment p2 = insertSucceededPayment(o2, 1, 200_000L, "90010032");
        doReturn(refundResult(true, "02", "02", p2.txnRef(), 200_000L, "90010032", "02"))
                .when(vnpayGateway).refund(any());
        mockMvc.perform(post("/api/v1/admin/orders/" + o2.code() + "/payments/" + p2.id() + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "code02-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(200_000L, "API", "Loi 02 tu VNPAY", true, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("UNKNOWN"))
                .andExpect(jsonPath("$.vnpResponseCode").value("02"));

        mockMvc.perform(post("/api/v1/admin/orders/" + o2.code() + "/payments/" + p2.id() + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "code02-dup-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(200_000L, "API", "Thu lai khi dang UNKNOWN", true, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_ACTIVE_EXISTS"));

        // Chữ ký phản hồi không hợp lệ -> UNKNOWN (AC-13) và ghi payment_event với signature_valid=false.
        StubOrder o3 = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        StubPayment p3 = insertSucceededPayment(o3, 1, 200_000L, "90010033");
        doReturn(refundResult(false, "00", "05", p3.txnRef(), 200_000L, "90010033", "02"))
                .when(vnpayGateway).refund(any());
        mockMvc.perform(post("/api/v1/admin/orders/" + o3.code() + "/payments/" + p3.id() + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "badsig-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(200_000L, "API", "Sai chu ky", true, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("UNKNOWN"));

        Map<String, Object> badSigEvent = jdbc.queryForMap(
                "SELECT signature_valid, outcome FROM commerce.payment_event WHERE payment_id = ? AND source = 'REFUND_API'",
                p3.id());
        assertThat(badSigEvent.get("signature_valid")).isEqualTo(false);
        assertThat(badSigEvent.get("outcome")).isEqualTo("REFUND_INVALID_SIGNATURE");

        // Mã 91 (hết hạn hoàn) -> FAILED và không chặn lần hoàn khác trên đơn mới.
        StubOrder o4 = fixtures.insertStubOrder(
                buyer, product, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        StubPayment p4 = insertSucceededPayment(o4, 1, 200_000L, "90010034");
        doReturn(refundResult(true, "91", "09", p4.txnRef(), 200_000L, "90010034", "02"))
                .when(vnpayGateway).refund(any());
        String o4Raw = mockMvc.perform(post("/api/v1/admin/orders/" + o4.code() + "/payments/" + p4.id() + "/refunds")
                        .with(asUser(adminId, "ops-admin", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "code91-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundBody(200_000L, "API", "Giao dich khong tim thay 91", true, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("FAILED"))
                .andExpect(jsonPath("$.vnpResponseCode").value("91"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String o4RefundId = mapper.readTree(o4Raw).path("id").asText();

        while (outboxRelay.relayBatch() > 0) {
            // Đẩy sự kiện audit.
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit.audit_event WHERE module = 'commerce' AND action = 'REFUND_FAILED' AND target_id = ?",
                Integer.class, o4RefundId))
                .isEqualTo(1);
    }

    private StubPayment insertSucceededPayment(StubOrder order, int seq, long amountVnd, String txnNo) {
        StubPayment payment = fixtures.insertStubPayment(order, seq, amountVnd, PaymentState.SUCCEEDED);
        jdbc.update("""
                UPDATE commerce.payment
                SET vnp_transaction_no = ?, bank_code = 'NCB', pay_date = '20261121100100',
                    response_code = '00', transaction_status = '00', updated_at = now()
                WHERE id = ?
                """, txnNo, payment.id());
        return payment;
    }

    private String refundBody(
            long amountVnd, String kind, String reason, boolean verifiedSystemError, String externalRef)
            throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("amountVnd", amountVnd);
        payload.put("kind", kind);
        payload.put("reason", reason);
        payload.put("verifiedSystemError", verifiedSystemError);
        if (externalRef != null) {
            payload.put("externalRef", externalRef);
        }
        return mapper.writeValueAsString(payload);
    }

    private static RefundResult refundResult(
            boolean signatureValid,
            String responseCode,
            String transactionStatus,
            String txnRef,
            long amountVnd,
            String txnNo,
            String transactionType) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("vnp_ResponseCode", responseCode);
        raw.put("vnp_TransactionStatus", transactionStatus);
        raw.put("vnp_Amount", Long.toString(amountVnd * 100L));
        raw.put("vnp_TmnCode", MockVnpayGateway.DEFAULT_MOCK_TMN_CODE);
        return new RefundResult(
                signatureValid,
                "req-" + UUID.randomUUID(),
                responseCode,
                "Mock refund " + responseCode,
                txnRef,
                amountVnd,
                "NCB",
                "20261121100500",
                txnNo,
                transactionType,
                transactionStatus,
                "Refund test",
                raw);
    }

    private void cleanRefundData() {
        jdbc.update("DELETE FROM commerce.refund WHERE order_id IN (SELECT id FROM commerce.purchase_order)");
    }
}
