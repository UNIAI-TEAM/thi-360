package vn.unicom.thi360.commerce.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.bundle.devdata.BundleDevDataBuilder;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpaySigner;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.refund.api.CreateRefundRequest;
import vn.unicom.thi360.commerce.refund.domain.RefundKind;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.LearnerAccount;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.security.SecurityProperties;
import vn.unicom.thi360.support.PublishedExamSetSupport;

/**
 * Slice 3/4 ITs cho hoàn tiền đơn bộ đề 3 môn SP2E {@code DS-BO-SP2E-3MON}: thu hồi lượt còn lại trên
 * cả 3 bài thi thành phần và xếp mail {@code REFUND_DONE} không NPE (AC-20, AC-27).
 */
class BundleRefundIT extends AbstractIntegrationTest {

    private static final String BUNDLE_CODE = "DS-BO-SP2E-3MON";
    private static final String MATH_CODE = "DS-SP2E-MATH-2026";
    private static final String VAN_CODE = "DS-SP2E-VAN-OLD";
    private static final String ANH_CODE = "DS-SP2E-ANH-2026";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PublishedExamSetSupport.useDatabase(registry, POSTGRES);
    }

    @Autowired private ApplicationContext context;
    @Autowired private BundleDevDataBuilder bundleDevDataBuilder;
    @Autowired private PublishedExamPort publishedExamPort;
    @Autowired private EntitlementPort entitlementPort;
    @Autowired private SecurityProperties securityProperties;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;

    private CommerceTestFixtures fixtures;
    private final List<UUID> trackedLearnerIds = new ArrayList<>();
    private final List<UUID> trackedActorIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        PublishedExamSetSupport.ensureBuilt(context);
        bundleDevDataBuilder.build();
        fixtures = new CommerceTestFixtures(jdbc, mapper, catalogLookup, promotionRepository, testSecuritySupport);
    }

    @AfterEach
    void tearDown() {
        for (UUID userId : trackedLearnerIds) {
            jdbc.update("""
                    DELETE FROM commerce.refund
                    WHERE order_id IN (SELECT id FROM commerce.purchase_order WHERE user_id = ?)
                    """, userId);
        }
        trackedLearnerIds.clear();
        fixtures.cleanup();
        trackedActorIds.forEach(id -> jdbc.update("DELETE FROM identity.user_account WHERE id = ?", id));
        trackedActorIds.clear();
    }

    @Test
    void fullRefundRevokesAllThreeMemberGrantsAndQueuesMailWithoutNpe() throws Exception {
        LearnerAccount learner = createTrackedLearner();
        JsonNode created = createAndPayBundleOrder(learner);
        UUID orderId = UUID.fromString(created.path("order").path("id").asText());
        String orderCode = created.path("order").path("code").asText();
        UUID paymentId = jdbc.queryForObject(
                "SELECT id FROM commerce.payment WHERE order_id = ? AND state = 'SUCCEEDED'",
                UUID.class,
                orderId);

        issueFullRefund(orderCode, paymentId);

        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, orderId))
                .isEqualTo("REFUNDED");

        List<Map<String, Object>> grants = jdbc.queryForList("""
                SELECT exam_id, attempts, attempts_revoked, revoked_at
                FROM commerce.entitlement_grant
                WHERE order_id = ?
                """, orderId);
        assertThat(grants).hasSize(3);
        assertThat(grants).allSatisfy(row -> {
            assertThat(((Number) row.get("attempts_revoked")).intValue()).isEqualTo(3);
            assertThat(row.get("revoked_at")).isNotNull();
        });

        List<Map<String, Object>> entitlements = jdbc.queryForList("""
                SELECT attempts_total, attempts_revoked
                FROM commerce.entitlement
                WHERE user_id = ?
                """, learner.id());
        assertThat(entitlements).hasSize(3);
        assertThat(entitlements).allSatisfy(row -> {
            assertThat(((Number) row.get("attempts_total")).intValue()).isEqualTo(3);
            assertThat(((Number) row.get("attempts_revoked")).intValue()).isEqualTo(3);
        });

        List<String> mailKinds = jdbc.queryForList(
                "SELECT kind FROM shared.email_message WHERE user_id = ? ORDER BY created_at ASC",
                String.class,
                learner.id());
        assertThat(mailKinds).containsExactly("ORDER_FULFILLED", "REFUND_DONE");
    }

    @Test
    void refundAfterPartialUseOnOneExamRevokesRemainingAttemptsAcrossBundle() throws Exception {
        LearnerAccount learner = createTrackedLearner();
        JsonNode created = createAndPayBundleOrder(learner);
        UUID orderId = UUID.fromString(created.path("order").path("id").asText());
        String orderCode = created.path("order").path("code").asText();
        UUID paymentId = jdbc.queryForObject(
                "SELECT id FROM commerce.payment WHERE order_id = ? AND state = 'SUCCEEDED'",
                UUID.class,
                orderId);

        UUID mathId = examIdByCode(MATH_CODE);
        UUID vanId = examIdByCode(VAN_CODE);
        UUID anhId = examIdByCode(ANH_CODE);
        UUID mathEntitlementId = entitlementIdFor(learner.id(), mathId);
        UUID vanEntitlementId = entitlementIdFor(learner.id(), vanId);
        UUID anhEntitlementId = entitlementIdFor(learner.id(), anhId);

        // Dùng 1 lượt và giữ chỗ 1 lượt trên Toán trước khi hoàn tiền.
        UUID usedAttempt = UUID.randomUUID();
        entitlementPort.reserve(learner.id(), mathEntitlementId, usedAttempt);
        entitlementPort.confirmUse(usedAttempt);
        UUID reservedAttempt = UUID.randomUUID();
        entitlementPort.reserve(learner.id(), mathEntitlementId, reservedAttempt);

        issueFullRefund(orderCode, paymentId);

        // Lượt đang giữ chỗ trước khi hoàn vẫn confirmUse được; không còn lượt nào để reserve mới trên cả 3 môn.
        entitlementPort.confirmUse(reservedAttempt);
        assertThatThrownBy(() -> entitlementPort.reserve(learner.id(), mathEntitlementId, UUID.randomUUID()))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> entitlementPort.reserve(learner.id(), vanEntitlementId, UUID.randomUUID()))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> entitlementPort.reserve(learner.id(), anhEntitlementId, UUID.randomUUID()))
                .isInstanceOf(ConflictException.class);

        Map<String, Object> mathRow = jdbc.queryForMap(
                "SELECT attempts_used, attempts_reserved, attempts_revoked FROM commerce.entitlement WHERE id = ?",
                mathEntitlementId);
        assertThat(((Number) mathRow.get("attempts_used")).intValue()).isEqualTo(2);
        assertThat(((Number) mathRow.get("attempts_reserved")).intValue()).isZero();
        assertThat(((Number) mathRow.get("attempts_revoked")).intValue()).isEqualTo(1);

        for (UUID otherEntitlement : List.of(vanEntitlementId, anhEntitlementId)) {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT attempts_used, attempts_revoked FROM commerce.entitlement WHERE id = ?",
                    otherEntitlement);
            assertThat(((Number) row.get("attempts_used")).intValue()).isZero();
            assertThat(((Number) row.get("attempts_revoked")).intValue()).isEqualTo(3);
        }
    }

    private void issueFullRefund(String orderCode, UUID paymentId) throws Exception {
        UUID adminId = UUID.randomUUID();
        trackedActorIds.add(adminId);
        RequestPostProcessor refundAdmin = fixtures.asManager(
                adminId, "refund-admin", Permissions.ORDER_VIEW, Permissions.ORDER_REFUND);
        String body = mapper.writeValueAsString(new CreateRefundRequest(
                99_000L, RefundKind.API, "Hoàn toàn bộ đơn bộ đề SP2E", Boolean.TRUE, null));
        mockMvc.perform(post("/api/v1/admin/orders/{code}/payments/{paymentId}/refunds", orderCode, paymentId)
                        .with(refundAdmin)
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("SUCCEEDED"));
    }

    private LearnerAccount createTrackedLearner() {
        LearnerAccount learner = fixtures.createLearner(true, "vi");
        trackedLearnerIds.add(learner.id());
        return learner;
    }

    private JsonNode createAndPayBundleOrder(LearnerAccount learner) throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "code", BUNDLE_CODE,
                "expectedAccountId", learner.id(),
                "expectedVersionNo", 1,
                "expectedAmountDue", 99_000L));
        JsonNode created = mapper.readTree(mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(learner))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        String txnRef = parseQueryParams(created.path("paymentUrl").asText()).get("vnp_TxnRef");
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", Long.toString(99_000L * 100L));
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", "Thanh toan DS bundle");
        params.put("vnp_PayDate", "20261121100600");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TmnCode", MockVnpayGateway.DEFAULT_MOCK_TMN_CODE);
        params.put("vnp_TransactionNo", "VNP" + CommerceTestFixtures.shortToken());
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_SecureHash", VnpaySigner.signPayParams(params, MockVnpayGateway.DEFAULT_MOCK_SECRET));

        var req = get("/api/v1/public/payments/vnpay/ipn")
                .with(r -> {
                    r.setRemoteAddr("127.0.0.1");
                    return r;
                });
        params.forEach(req::param);
        mockMvc.perform(req)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"));
        return created;
    }

    private UUID entitlementIdFor(UUID userId, UUID examId) {
        return jdbc.queryForObject("""
                SELECT id FROM commerce.entitlement
                WHERE user_id = ? AND exam_id = ? AND source = 'PURCHASE' AND source_ref IS NULL
                """, UUID.class, userId, examId);
    }

    private UUID examIdByCode(String code) {
        return publishedExamPort.listPublishedExamIds().stream()
                .map(id -> publishedExamPort.findServing(id).orElse(null))
                .filter(s -> s != null && s.version() != null && code.equals(s.version().code()))
                .map(PublishedExamPort.ServingExam::examId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing DS exam: " + code));
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
