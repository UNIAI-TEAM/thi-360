package vn.unicom.thi360.commerce.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.security.SecurityProperties;

/**
 * Ma trận phân quyền đầy đủ cho mọi endpoint quản trị của CM-5 (AC-31):
 * không phiên -> 401; thiếu quyền (kể cả giữ 2 quyền {@code ORDER.*} còn lại) -> 403 và không ghi DB;
 * giữ đúng một quyền yêu cầu -> qua kiểm tra phân quyền.
 */
class OrderPermissionMatrixIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
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
    void enforcesSingleRequiredPermissionOnAllElevenCm5AdminEndpointsWithNoDbMutationOnReject() throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var exam = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);

        // Đơn PAID dùng cho recover + chi tiết + danh sách + buyers.
        var paidOrder = fixtures.insertStubOrder(
                buyer, exam, OrderState.PAID, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var succeededPayment = fixtures.insertStubPayment(paidOrder, 1, 200_000L, PaymentState.SUCCEEDED);
        jdbc.update("""
                UPDATE commerce.payment
                SET vnp_transaction_no = '90090001', bank_code = 'NCB', pay_date = '20261121100100',
                    response_code = '00', transaction_status = '00'
                WHERE id = ?
                """, succeededPayment.id());

        // Refund UNKNOWN dùng cho getRefund và markFailed, cộng thêm refund UNKNOWN riêng cho query và payment sạch cho create refund.
        var unknownRefundOrder = fixtures.insertStubOrder(
                buyer, exam, OrderState.FULFILLED, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var unknownRefundPay = fixtures.insertStubPayment(unknownRefundOrder, 1, 200_000L, PaymentState.SUCCEEDED);
        UUID unknownRefundId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO commerce.refund
                    (id, vnp_request_id, idempotency_key, payment_id, order_id, amount_vnd, kind, state,
                     reason, verified_system_error, requested_by, requested_by_id, requested_at,
                     created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 200000, 'API', 'UNKNOWN', 'Mat ket noi', true, 'ops', ?, now(), now(), now(), 0)
                """,
                unknownRefundId,
                UUID.randomUUID().toString().replace("-", ""),
                "perm-unk-" + UUID.randomUUID(),
                unknownRefundPay.id(),
                unknownRefundOrder.id(),
                UUID.randomUUID());

        var queryRefundOrder = fixtures.insertStubOrder(
                buyer, exam, OrderState.FULFILLED, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var queryRefundPay = fixtures.insertStubPayment(queryRefundOrder, 1, 200_000L, PaymentState.SUCCEEDED);
        UUID queryRefundId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO commerce.refund
                    (id, vnp_request_id, idempotency_key, payment_id, order_id, amount_vnd, kind, state,
                     reason, verified_system_error, requested_by, requested_by_id, requested_at,
                     created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 200000, 'API', 'UNKNOWN', 'Tra cuu', true, 'ops', ?, now(), now(), now(), 0)
                """,
                queryRefundId,
                UUID.randomUUID().toString().replace("-", ""),
                "perm-qry-" + UUID.randomUUID(),
                queryRefundPay.id(),
                queryRefundOrder.id(),
                UUID.randomUUID());

        var refundableOrder = fixtures.insertStubOrder(
                buyer, exam, OrderState.FULFILLED, PaymentMethod.VNPAY, 200_000L, null, Instant.now());
        var refundablePay = fixtures.insertStubPayment(refundableOrder, 1, 200_000L, PaymentState.SUCCEEDED);
        jdbc.update("""
                UPDATE commerce.payment
                SET vnp_transaction_no = '90090002', bank_code = 'NCB', pay_date = '20261121100100',
                    response_code = '00', transaction_status = '00'
                WHERE id = ?
                """, refundablePay.id());

        // Đơn Ca C chờ late-success.
        var lateOrder = fixtures.insertStubOrder(
                buyer, exam, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var latePayment = fixtures.insertStubPayment(lateOrder, 1, 200_000L, PaymentState.FAILED);
        jdbc.update("UPDATE commerce.payment SET late_success_review = 'PENDING', late_success_at = now() WHERE id = ?",
                latePayment.id());

        String refundBody = mapper.writeValueAsString(Map.of(
                "amountVnd", 100_000L,
                "kind", "API",
                "reason", "Kiem tra quyen hoan",
                "verifiedSystemError", true));
        String markFailedBody = mapper.writeValueAsString(Map.of(
                "reason", "Xac nhan that bai",
                "evidenceRef", "EV-PERM-MF"));
        String recoverBody = mapper.writeValueAsString(Map.of("reason", "Kiem tra quyen recover"));
        String lateSuccessBody = mapper.writeValueAsString(Map.of(
                "decision", "REFUND",
                "reason", "Kiem tra quyen late-success",
                "evidenceRef", "EV-PERM-LS"));

        List<EndpointSpec> specs = List.of(
                EndpointSpec.get("/api/v1/admin/orders", Permissions.ORDER_VIEW),
                EndpointSpec.get("/api/v1/admin/orders/attention", Permissions.ORDER_VIEW),
                EndpointSpec.get("/api/v1/admin/orders/" + paidOrder.code(), Permissions.ORDER_VIEW),
                EndpointSpec.get("/api/v1/admin/exams/" + exam.examId() + "/buyers", Permissions.ORDER_VIEW),
                EndpointSpec.get("/api/v1/admin/refunds/" + unknownRefundId, Permissions.ORDER_VIEW),
                EndpointSpec.get("/api/v1/admin/reconciliation-reports/latest", Permissions.ORDER_VIEW),
                EndpointSpec.post(
                        "/api/v1/admin/orders/" + refundableOrder.code() + "/payments/" + refundablePay.id() + "/refunds",
                        Permissions.ORDER_REFUND,
                        refundBody,
                        true),
                EndpointSpec.post(
                        "/api/v1/admin/refunds/" + queryRefundId + "/query",
                        Permissions.ORDER_REFUND,
                        null,
                        false),
                EndpointSpec.post(
                        "/api/v1/admin/refunds/" + unknownRefundId + "/mark-failed",
                        Permissions.ORDER_REFUND,
                        markFailedBody,
                        false),
                EndpointSpec.post(
                        "/api/v1/admin/orders/" + paidOrder.code() + "/fulfill",
                        Permissions.ORDER_RECOVER,
                        recoverBody,
                        true),
                EndpointSpec.post(
                        "/api/v1/admin/orders/" + lateOrder.code() + "/payments/" + latePayment.id() + "/late-success",
                        Permissions.ORDER_RECOVER,
                        lateSuccessBody,
                        true));

        String origin = securityProperties.adminOrigins().getFirst();
        int initialRefundCount = countRows("commerce.refund");
        int initialGrantCount = countRows("commerce.entitlement_grant");
        int initialHistoryCount = countRows("commerce.order_history");

        // 1. Không có phiên -> 401 UNAUTHENTICATED; thiếu quyền (có 2 quyền còn lại) & học viên -> 403 FORBIDDEN; 0 ghi DB.
        for (EndpointSpec spec : specs) {
            mockMvc.perform(spec.buildRequest(origin, false))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

            String[] otherTwo = otherOrderPermissions(spec.requiredPermission());
            mockMvc.perform(spec.buildRequest(origin, false)
                            .with(asUser(UUID.randomUUID(), "missing-perm", otherTwo)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));

            mockMvc.perform(spec.buildRequest(origin, false)
                            .with(fixtures.asLearner(buyer)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        assertThat(countRows("commerce.refund")).isEqualTo(initialRefundCount);
        assertThat(countRows("commerce.entitlement_grant")).isEqualTo(initialGrantCount);
        assertThat(countRows("commerce.order_history")).isEqualTo(initialHistoryCount);
        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.refund WHERE id = ?", String.class, unknownRefundId))
                .isEqualTo("UNKNOWN");
        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, paidOrder.id()))
                .isEqualTo("PAID");
        assertThat(jdbc.queryForObject(
                "SELECT late_success_review FROM commerce.payment WHERE id = ?", String.class, latePayment.id()))
                .isEqualTo("PENDING");

        // 2. Chỉ có đúng quyền yêu cầu -> vượt qua kiểm tra phân quyền (200/201).
        for (EndpointSpec spec : specs) {
            int statusCode = mockMvc.perform(spec.buildRequest(origin, true)
                            .with(asUser(UUID.randomUUID(), "single-perm", spec.requiredPermission())))
                    .andReturn()
                    .getResponse()
                    .getStatus();
            assertThat(statusCode)
                    .as("%s with %s must pass authz", spec.path(), spec.requiredPermission())
                    .isNotIn(401, 403)
                    .isIn(200, 201);
        }
    }

    private int countRows(String table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    private static String[] otherOrderPermissions(String required) {
        return switch (required) {
            case Permissions.ORDER_VIEW -> new String[] {Permissions.ORDER_REFUND, Permissions.ORDER_RECOVER};
            case Permissions.ORDER_REFUND -> new String[] {Permissions.ORDER_VIEW, Permissions.ORDER_RECOVER};
            case Permissions.ORDER_RECOVER -> new String[] {Permissions.ORDER_VIEW, Permissions.ORDER_REFUND};
            default -> throw new IllegalArgumentException("Unexpected permission: " + required);
        };
    }

    private record EndpointSpec(
            String method,
            String path,
            String requiredPermission,
            String body,
            boolean needsIdempotencyKey) {

        static EndpointSpec get(String path, String requiredPermission) {
            return new EndpointSpec("GET", path, requiredPermission, null, false);
        }

        static EndpointSpec post(String path, String requiredPermission, String body, boolean needsIdempotencyKey) {
            return new EndpointSpec("POST", path, requiredPermission, body, needsIdempotencyKey);
        }

        MockHttpServletRequestBuilder buildRequest(String origin, boolean freshKey) {
            MockHttpServletRequestBuilder builder = "GET".equals(method)
                    ? MockMvcRequestBuilders.get(path)
                    : MockMvcRequestBuilders.post(path);
            if ("POST".equals(method)) {
                builder.header(HttpHeaders.ORIGIN, origin);
                if (needsIdempotencyKey) {
                    builder.header("Idempotency-Key",
                            (freshKey ? "perm-ok-" : "perm-blk-") + UUID.randomUUID());
                }
                if (body != null) {
                    builder.contentType(MediaType.APPLICATION_JSON).content(body);
                }
            }
            return builder;
        }
    }
}
