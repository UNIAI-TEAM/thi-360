package vn.unicom.thi360.commerce.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpaySigner;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudienceType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.security.SecurityProperties;

/**
 * Kiểm thử xử lý đối soát tiền về muộn Ca C (BS §14, AC-32..AC-35).
 */
class LateSuccessReviewIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private OutboxRelay outboxRelay;
    @Autowired private SecurityProperties securityProperties;

    private CommerceTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new CommerceTestFixtures(jdbc, mapper, catalogLookup, promotionRepository, testSecuritySupport);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM commerce.refund");
        fixtures.cleanup();
    }

    @Test
    void acceptCountsPaymentAndFulfills() throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var exam = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var order = fixtures.insertStubOrder(
                buyer, exam, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var p1 = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.CREATED);

        // Chuyển p1 sang FAILED rồi nhận IPN 00/00 -> Ca C (PENDING).
        callIpn(p1.txnRef(), 200_000L, "24", "02", "0");
        callIpn(p1.txnRef(), 200_000L, "00", "00", "90071001");

        // Cũng kiểm tra ACCEPT trên đơn CANCELLED có voucher bị trả lại (reclaim voucher + không đặt needs_review).
        var voucher = fixtures.createVoucher(
                "LSACC", DiscountType.AMOUNT, 50_000L, null, 5, 2, true,
                PromotionScope.ALL, PromotionAudienceType.ALL);
        String createRaw = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "ls-cancel-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", exam.code(),
                                "voucherCode", voucher.getCode(),
                                "expectedAccountId", buyer.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 150_000L))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var cancelNode = mapper.readTree(createRaw).path("order");
        UUID cancelOrderId = UUID.fromString(cancelNode.path("id").asText());
        String cancelOrderCode = cancelNode.path("code").asText();
        UUID cancelPaymentId = UUID.fromString(cancelNode.path("payments").get(0).path("id").asText());
        String cancelTxnRef = cancelNode.path("payments").get(0).path("txnRef").asText();
        fixtures.trackOrderId(cancelOrderId);

        mockMvc.perform(post("/api/v1/me/orders/" + cancelOrderCode + "/cancel")
                        .with(fixtures.asLearner(buyer)))
                .andExpect(status().isOk());
        callIpn(cancelTxnRef, 150_000L, "24", "02", "0");
        callIpn(cancelTxnRef, 150_000L, "00", "00", "90071002");

        UUID adminId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/admin/orders/" + order.code() + "/payments/" + p1.id() + "/late-success")
                        .with(asUser(adminId, "ops-recover", Permissions.ORDER_RECOVER))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "ls-acc-1-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "decision", "ACCEPT",
                                "reason", "Da doi soat sao ke VNPAY khop giao dich",
                                "evidenceRef", "RECON-20261121-01"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FULFILLED"))
                .andExpect(jsonPath("$.needsReview").value(false))
                .andExpect(jsonPath("$.payments[0].state").value("SUCCEEDED"))
                .andExpect(jsonPath("$.payments[0].duplicate").value(false))
                .andExpect(jsonPath("$.payments[0].lateSuccessReview").value("ACCEPTED"))
                .andExpect(jsonPath("$.grants.length()").value(1));

        // ACCEPT đơn CANCELLED -> thu hồi lượt voucher + FULFILLED, không gắn needs_review.
        mockMvc.perform(post("/api/v1/admin/orders/" + cancelOrderCode + "/payments/" + cancelPaymentId + "/late-success")
                        .with(asUser(adminId, "ops-recover", Permissions.ORDER_RECOVER))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "ls-acc-2-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "decision", "ACCEPT",
                                "reason", "Doi soat don da huy van thu tien",
                                "evidenceRef", "RECON-20261121-02"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FULFILLED"))
                .andExpect(jsonPath("$.needsReview").value(false));

        assertThat(jdbc.queryForObject(
                "SELECT attempts_total FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                Integer.class, buyer.id(), exam.examId())).isEqualTo(6);
        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.promotion_redemption WHERE order_id = ? AND promotion_id = ?",
                String.class, cancelOrderId, voucher.getId())).isEqualTo("CONFIRMED");

        while (outboxRelay.relayBatch() > 0) {
            // Đẩy outbox.
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit.audit_event WHERE module = 'commerce' AND action = 'PAYMENT_LATE_SUCCESS_ACCEPTED' AND target_id = ?",
                Integer.class, order.id().toString())).isEqualTo(1);
    }

    @Test
    void acceptReplayGrantsOnce() throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var exam = fixtures.insertOnSaleProduct("HSA", 200_000L, 4);
        var order = fixtures.insertStubOrder(
                buyer, exam, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var p1 = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.FAILED);
        jdbc.update("UPDATE commerce.payment SET late_success_review = 'PENDING', late_success_at = now() WHERE id = ?",
                p1.id());

        UUID adminId = UUID.randomUUID();
        String idemKey = "ls-replay-" + UUID.randomUUID();
        String body = mapper.writeValueAsString(Map.of(
                "decision", "ACCEPT",
                "reason", "Doi soat chap nhan",
                "evidenceRef", "RECON-REPLAY-1"));

        mockMvc.perform(post("/api/v1/admin/orders/" + order.code() + "/payments/" + p1.id() + "/late-success")
                        .with(asUser(adminId, "ops-recover", Permissions.ORDER_RECOVER))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FULFILLED"));

        // Phát lại cùng khóa và cùng thân: trả 200 nhưng KHÔNG cấp thêm quyền hay ghi lịch sử lặp.
        mockMvc.perform(post("/api/v1/admin/orders/" + order.code() + "/payments/" + p1.id() + "/late-success")
                        .with(asUser(adminId, "ops-recover", Permissions.ORDER_RECOVER))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FULFILLED"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.entitlement_grant WHERE order_id = ?",
                Integer.class, order.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT attempts_total FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                Integer.class, buyer.id(), exam.examId())).isEqualTo(4);

        // Cùng khóa nhưng đổi quyết định -> 409 IDEMPOTENCY_KEY_REUSED.
        mockMvc.perform(post("/api/v1/admin/orders/" + order.code() + "/payments/" + p1.id() + "/late-success")
                        .with(asUser(adminId, "ops-recover", Permissions.ORDER_RECOVER))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "decision", "REFUND",
                                "reason", "Doi soat chap nhan",
                                "evidenceRef", "RECON-REPLAY-1"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void refundDecisionMakesExcess() throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var exam = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var order = fixtures.insertStubOrder(
                buyer, exam, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var p1 = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.FAILED);
        jdbc.update("""
                UPDATE commerce.payment
                SET late_success_review = 'PENDING', late_success_at = now(),
                    vnp_transaction_no = '90072001', bank_code = 'NCB'
                WHERE id = ?
                """, p1.id());

        UUID recoverAdmin = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/admin/orders/" + order.code() + "/payments/" + p1.id() + "/late-success")
                        .with(asUser(recoverAdmin, "ops-recover", Permissions.ORDER_RECOVER))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "ls-ref-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "decision", "REFUND",
                                "reason", "Khach da huy giao dich, chon hoan tien",
                                "evidenceRef", "RECON-REFUND-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.payments[0].state").value("SUCCEEDED"))
                .andExpect(jsonPath("$.payments[0].duplicate").value(true))
                .andExpect(jsonPath("$.payments[0].excessReason").value("REJECTED_AFTER_RECONCILE"))
                .andExpect(jsonPath("$.payments[0].lateSuccessReview").value("REJECTED"))
                .andExpect(jsonPath("$.payments[0].refundableAmountVnd").value(200_000L))
                .andExpect(jsonPath("$.grants").isEmpty());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.entitlement_grant WHERE order_id = ?",
                Integer.class, order.id())).isZero();

        // Khoản này có thể hoàn ngay bằng quyền ORDER.REFUND mà không cấp/thu hồi bài.
        mockMvc.perform(post("/api/v1/admin/orders/" + order.code() + "/payments/" + p1.id() + "/refunds")
                        .with(asUser(UUID.randomUUID(), "ops-refund", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "ls-do-ref-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "amountVnd", 200_000L,
                                "kind", "API",
                                "reason", "Hoan khoan REJECTED_AFTER_RECONCILE",
                                "verifiedSystemError", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("SUCCEEDED"));

        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("PENDING_PAYMENT");

        while (outboxRelay.relayBatch() > 0) {
            // Đẩy outbox.
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit.audit_event WHERE module = 'commerce' AND action = 'PAYMENT_LATE_SUCCESS_REJECTED' AND target_id = ?",
                Integer.class, order.id().toString())).isEqualTo(1);
    }

    @Test
    void resolvedReviewIsRejected() throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var exam = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var order = fixtures.insertStubOrder(
                buyer, exam, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var pPending = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.FAILED);
        jdbc.update("UPDATE commerce.payment SET late_success_review = 'PENDING', late_success_at = now() WHERE id = ?",
                pPending.id());

        UUID adminId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/admin/orders/" + order.code() + "/payments/" + pPending.id() + "/late-success")
                        .with(asUser(adminId, "ops-recover", Permissions.ORDER_RECOVER))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "ls-res-1-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "decision", "REFUND",
                                "reason", "Chon hoan lan 1",
                                "evidenceRef", "EV-1"))))
                .andExpect(status().isOk());

        // Gửi yêu cầu khác với khóa mới khi đã REJECTED -> 409 LATE_SUCCESS_ALREADY_RESOLVED.
        mockMvc.perform(post("/api/v1/admin/orders/" + order.code() + "/payments/" + pPending.id() + "/late-success")
                        .with(asUser(adminId, "ops-recover", Permissions.ORDER_RECOVER))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "ls-res-2-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "decision", "ACCEPT",
                                "reason", "Thu lai",
                                "evidenceRef", "EV-2"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LATE_SUCCESS_ALREADY_RESOLVED"));

        // Khoản chưa từng ở PENDING (vd. FAILED thuần) -> 409 LATE_SUCCESS_ALREADY_RESOLVED.
        var pPlainFailed = fixtures.insertStubPayment(order, 2, 200_000L, PaymentState.FAILED);
        mockMvc.perform(post("/api/v1/admin/orders/" + order.code() + "/payments/" + pPlainFailed.id() + "/late-success")
                        .with(asUser(adminId, "ops-recover", Permissions.ORDER_RECOVER))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "ls-res-3-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "decision", "ACCEPT",
                                "reason", "Khong hop le",
                                "evidenceRef", "EV-3"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LATE_SUCCESS_ALREADY_RESOLVED"));
    }

    @Test
    void reasonAndEvidenceRequired() throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var exam = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var order = fixtures.insertStubOrder(
                buyer, exam, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var p1 = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.FAILED);
        jdbc.update("UPDATE commerce.payment SET late_success_review = 'PENDING', late_success_at = now() WHERE id = ?",
                p1.id());

        UUID adminId = UUID.randomUUID();
        // Thiếu reason -> 400.
        mockMvc.perform(post("/api/v1/admin/orders/" + order.code() + "/payments/" + p1.id() + "/late-success")
                        .with(asUser(adminId, "ops-recover", Permissions.ORDER_RECOVER))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "ls-val-1-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "decision", "ACCEPT",
                                "reason", "   ",
                                "evidenceRef", "EV-1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // Thiếu evidenceRef -> 400.
        mockMvc.perform(post("/api/v1/admin/orders/" + order.code() + "/payments/" + p1.id() + "/late-success")
                        .with(asUser(adminId, "ops-recover", Permissions.ORDER_RECOVER))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "ls-val-2-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "decision", "ACCEPT",
                                "reason", "Ly do hop le",
                                "evidenceRef", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // Thiếu Idempotency-Key -> 400 IDEMPOTENCY_KEY_REQUIRED.
        mockMvc.perform(post("/api/v1/admin/orders/" + order.code() + "/payments/" + p1.id() + "/late-success")
                        .with(asUser(adminId, "ops-recover", Permissions.ORDER_RECOVER))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "decision", "ACCEPT",
                                "reason", "Ly do hop le",
                                "evidenceRef", "EV-1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void onlyOrderRecoverMayDecide() throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var exam = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var order = fixtures.insertStubOrder(
                buyer, exam, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var p1 = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.FAILED);
        jdbc.update("UPDATE commerce.payment SET late_success_review = 'PENDING', late_success_at = now() WHERE id = ?",
                p1.id());

        String url = "/api/v1/admin/orders/" + order.code() + "/payments/" + p1.id() + "/late-success";
        String body = mapper.writeValueAsString(Map.of(
                "decision", "ACCEPT",
                "reason", "Thu quyen",
                "evidenceRef", "EV-AUTH"));

        // Không có phiên -> 401.
        mockMvc.perform(post(url)
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "ls-auth-1-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        // Có cả ORDER.VIEW và ORDER.REFUND nhưng thiếu ORDER.RECOVER -> 403.
        mockMvc.perform(post(url)
                        .with(asUser(UUID.randomUUID(), "viewer-refunder", Permissions.ORDER_VIEW, Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "ls-auth-2-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // Học viên -> 403.
        mockMvc.perform(post(url)
                        .with(fixtures.asLearner(buyer))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "ls-auth-3-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // Không ghi dữ liệu nào khi bị từ chối.
        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.payment WHERE id = ?", String.class, p1.id())).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject(
                "SELECT late_success_review FROM commerce.payment WHERE id = ?", String.class, p1.id()))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.entitlement_grant WHERE order_id = ?",
                Integer.class, order.id())).isZero();

        // Chỉ có duy nhất ORDER.RECOVER -> qua.
        mockMvc.perform(post(url)
                        .with(asUser(UUID.randomUUID(), "only-recover", Permissions.ORDER_RECOVER))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "ls-auth-ok-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FULFILLED"));
    }

    @Test
    void acceptOnReplacedOrderFlagsDoubleChargeOnlyWhenReplacementAlreadyPaid() throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var exam = fixtures.insertOnSaleProduct("HSA", 200_000L, 1);

        // O2 (replacement) đã FULFILLED; O1 (REPLACED bởi O2) có p1 đang chờ đối soát -> ACCEPT O1 gắn DOUBLE_CHARGE_REPLACED_ORDER.
        var o2 = fixtures.insertStubOrder(
                buyer, exam, OrderState.FULFILLED, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var o1 = fixtures.insertStubOrder(
                buyer, exam, OrderState.REPLACED, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        jdbc.update("UPDATE commerce.purchase_order SET replaced_by_order_id = ? WHERE id = ?", o2.id(), o1.id());
        var p1 = fixtures.insertStubPayment(o1, 1, 200_000L, PaymentState.FAILED);
        jdbc.update("UPDATE commerce.payment SET late_success_review = 'PENDING', late_success_at = now() WHERE id = ?",
                p1.id());

        mockMvc.perform(post("/api/v1/admin/orders/" + o1.code() + "/payments/" + p1.id() + "/late-success")
                        .with(asUser(UUID.randomUUID(), "ops-recover", Permissions.ORDER_RECOVER))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "ls-dbl-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "decision", "ACCEPT",
                                "reason", "Doi soat don thay the bi thu hai lan",
                                "evidenceRef", "EV-DBL-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FULFILLED"))
                .andExpect(jsonPath("$.needsReview").value(true))
                .andExpect(jsonPath("$.reviewReason").value("DOUBLE_CHARGE_REPLACED_ORDER"));
    }

    @Test
    void acceptWithSiblingPendingConvertsSiblingToAutoExcessAndFlagsPaidPredecessorDoubleCharge() throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var exam = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);

        // 1. Hai payment PENDING trên cùng một đơn -> ACCEPT p1 tự chuyển p2 thành SUCCEEDED + is_duplicate=true + AUTO_EXCESS.
        var orderSibling = fixtures.insertStubOrder(
                buyer, exam, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var p1 = fixtures.insertStubPayment(orderSibling, 1, 200_000L, PaymentState.FAILED);
        var p2 = fixtures.insertStubPayment(orderSibling, 2, 200_000L, PaymentState.FAILED);
        jdbc.update("UPDATE commerce.payment SET late_success_review = 'PENDING', late_success_at = now() WHERE id IN (?, ?)",
                p1.id(), p2.id());

        UUID adminId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/admin/orders/" + orderSibling.code() + "/payments/" + p1.id() + "/late-success")
                        .with(asUser(adminId, "ops-recover", Permissions.ORDER_RECOVER))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "ls-sib-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "decision", "ACCEPT",
                                "reason", "Chon p1 lam khoan duoc tinh",
                                "evidenceRef", "EV-SIB-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FULFILLED"));

        Map<String, Object> p2Row = jdbc.queryForMap(
                "SELECT state, is_duplicate, excess_reason, late_success_review FROM commerce.payment WHERE id = ?",
                p2.id());
        assertThat(p2Row.get("state")).isEqualTo("SUCCEEDED");
        assertThat(p2Row.get("is_duplicate")).isEqualTo(true);
        assertThat(p2Row.get("excess_reason")).isEqualTo("AFTER_COUNTED");
        assertThat(p2Row.get("late_success_review")).isEqualTo("AUTO_EXCESS");

        while (outboxRelay.relayBatch() > 0) {
            // Đẩy outbox.
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit.audit_event WHERE module = 'commerce' AND action = 'PAYMENT_EXCESS_RECEIVED' AND target_id = ?",
                Integer.class, orderSibling.id().toString())).isEqualTo(1);

        // 2. O1 (FULFILLED) bị thay thế bởi O2 (REPLACED bởi không ai, nhận tiền về muộn) -> ACCEPT O2 gắn DOUBLE_CHARGE_REPLACED_ORDER lên O1.
        var o2 = fixtures.insertStubOrder(
                buyer, exam, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var o1 = fixtures.insertStubOrder(
                buyer, exam, OrderState.FULFILLED, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        jdbc.update("UPDATE commerce.purchase_order SET replaced_by_order_id = ? WHERE id = ?", o2.id(), o1.id());
        var pO2 = fixtures.insertStubPayment(o2, 1, 200_000L, PaymentState.FAILED);
        jdbc.update("UPDATE commerce.payment SET late_success_review = 'PENDING', late_success_at = now() WHERE id = ?",
                pO2.id());

        mockMvc.perform(post("/api/v1/admin/orders/" + o2.code() + "/payments/" + pO2.id() + "/late-success")
                        .with(asUser(adminId, "ops-recover", Permissions.ORDER_RECOVER))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "ls-pred-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "decision", "ACCEPT",
                                "reason", "Don thay the O2 nhan tien sau khi O1 da FULFILLED",
                                "evidenceRef", "EV-PRED-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FULFILLED"))
                .andExpect(jsonPath("$.needsReview").value(false));

        Map<String, Object> o1Row = jdbc.queryForMap(
                "SELECT needs_review, review_reason FROM commerce.purchase_order WHERE id = ?", o1.id());
        assertThat(o1Row.get("needs_review")).isEqualTo(true);
        assertThat(o1Row.get("review_reason")).isEqualTo("DOUBLE_CHARGE_REPLACED_ORDER");
    }

    private void callIpn(String txnRef, long amountVnd, String responseCode, String txStatus, String txnNo)
            throws Exception {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", Long.toString(amountVnd * 100L));
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", "Thanh toan don test");
        params.put("vnp_PayDate", "20261121100100");
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TmnCode", MockVnpayGateway.DEFAULT_MOCK_TMN_CODE);
        params.put("vnp_TransactionNo", txnNo);
        params.put("vnp_TransactionStatus", txStatus);
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_SecureHash", VnpaySigner.signPayParams(params, MockVnpayGateway.DEFAULT_MOCK_SECRET));
        MockHttpServletRequestBuilder req = get("/api/v1/public/payments/vnpay/ipn");
        params.forEach(req::param);
        mockMvc.perform(req).andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("00"));
    }
}
