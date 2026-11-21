package vn.unicom.thi360.commerce.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.bundle.devdata.BundleDevDataBuilder;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementRowLocker;
import vn.unicom.thi360.commerce.fulfillment.application.FulfillmentRetryWorker;
import vn.unicom.thi360.commerce.fulfillment.application.FulfillmentService;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpaySigner;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.storefront.application.StorefrontProjectionService;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.LearnerAccount;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.support.PublishedExamSetSupport;

/**
 * Slice 3/4 ITs cho cấp quyền bộ đề 3 môn SP2E {@code DS-BO-SP2E-3MON}: giao dịch nguyên tử, idempotency,
 * thử lại khi lỗi giữa chừng, và cộng dồn FIFO với bài thi lẻ trùng môn (AC-17, AC-18, AC-19).
 */
@TestPropertySource(properties = "thi360.commerce.scheduling.enabled=true")
class BundleFulfillmentIT extends AbstractIntegrationTest {

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
    @Autowired private FulfillmentService fulfillmentService;
    @Autowired private FulfillmentRetryWorker fulfillmentRetryWorker;
    @Autowired private StorefrontProjectionService storefrontProjectionService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;

    @MockitoSpyBean
    private EntitlementRowLocker entitlementRowLocker;

    private CommerceTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        PublishedExamSetSupport.ensureBuilt(context);
        bundleDevDataBuilder.build();
        reset(entitlementRowLocker);
        reset(AopTestUtils.<EntitlementRowLocker>getUltimateTargetObject(entitlementRowLocker));
        fixtures = new CommerceTestFixtures(jdbc, mapper, catalogLookup, promotionRepository, testSecuritySupport);
    }

    @AfterEach
    void tearDown() {
        reset(entitlementRowLocker);
        reset(AopTestUtils.<EntitlementRowLocker>getUltimateTargetObject(entitlementRowLocker));
        fixtures.cleanup();
    }

    @Test
    void ipnFulfillsAllThreeSp2eSubjectsAtomicallyAndIdempotently() throws Exception {
        LearnerAccount learner = fixtures.createLearner();
        JsonNode created = createOrder(learner, BUNDLE_CODE, 99_000L);
        UUID orderId = UUID.fromString(created.path("order").path("id").asText());
        Map<String, String> ipnParams = payViaIpn(created.path("paymentUrl").asText(), 99_000L);

        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, orderId))
                .isEqualTo("FULFILLED");

        UUID mathId = examIdByCode(MATH_CODE);
        UUID vanId = examIdByCode(VAN_CODE);
        UUID anhId = examIdByCode(ANH_CODE);

        List<Map<String, Object>> entitlements = jdbc.queryForList("""
                SELECT exam_id, attempts_total, attempts_used, attempts_revoked
                FROM commerce.entitlement
                WHERE user_id = ?
                """, learner.id());
        assertThat(entitlements).hasSize(3);
        assertThat(entitlements)
                .extracting(row -> (UUID) row.get("exam_id"))
                .containsExactlyInAnyOrder(mathId, vanId, anhId);
        assertThat(entitlements)
                .allSatisfy(row -> assertThat(((Number) row.get("attempts_total")).intValue()).isEqualTo(3));

        List<Map<String, Object>> grants = jdbc.queryForList("""
                SELECT exam_id, attempts, result_view
                FROM commerce.entitlement_grant
                WHERE order_id = ?
                """, orderId);
        assertThat(grants).hasSize(3);
        assertThat(grants).allSatisfy(row -> {
            assertThat(((Number) row.get("attempts")).intValue()).isEqualTo(3);
            assertThat(row.get("result_view")).isEqualTo("SCORE_AND_ANSWERS");
        });

        // AC-17: Gửi lại đúng VNPAY IPN với cùng vnp_TxnRef & vnp_TransactionNo -> RspCode=02, không nhân đôi payment/grant/attempts.
        sendIpn(ipnParams)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("02"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.payment WHERE order_id = ?", Integer.class, orderId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.entitlement_grant WHERE order_id = ?",
                Integer.class, orderId)).isEqualTo(3);
        List<Map<String, Object>> entitlementsAfterIpnReplay = jdbc.queryForList("""
                SELECT exam_id, attempts_total
                FROM commerce.entitlement
                WHERE user_id = ?
                """, learner.id());
        assertThat(entitlementsAfterIpnReplay).hasSize(3);
        assertThat(entitlementsAfterIpnReplay)
                .extracting(row -> (UUID) row.get("exam_id"))
                .containsExactlyInAnyOrder(mathId, vanId, anhId);
        assertThat(entitlementsAfterIpnReplay)
                .allSatisfy(row -> assertThat(((Number) row.get("attempts_total")).intValue()).isEqualTo(3));

        // E16: gọi lại FulfillmentService không nhân đôi grant hoặc lượt thi.
        fulfillmentService.fulfill(orderId);
        Integer grantCountAfterReplay = jdbc.queryForObject(
                "SELECT count(*) FROM commerce.entitlement_grant WHERE order_id = ?",
                Integer.class,
                orderId);
        assertThat(grantCountAfterReplay).isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT attempts_total FROM commerce.entitlement WHERE user_id = ?",
                Integer.class, learner.id())).containsExactly(3, 3, 3);
    }

    @Test
    void partialGrantFailureRollsBackAllAndRetryWorkerCompletesAllThree() throws Exception {
        AtomicInteger calls = new AtomicInteger(0);
        EntitlementRowLocker spy = AopTestUtils.getUltimateTargetObject(entitlementRowLocker);
        doAnswer(inv -> {
            if (calls.incrementAndGet() == 2) {
                throw new RuntimeException("Simulated transient failure on 2nd bundle item");
            }
            return inv.callRealMethod();
        }).when(spy).lockOrCreatePurchase(any(), any());

        LearnerAccount learner = fixtures.createLearner();
        JsonNode created = createOrder(learner, BUNDLE_CODE, 99_000L);
        UUID orderId = UUID.fromString(created.path("order").path("id").asText());
        payViaIpn(created.path("paymentUrl").asText(), 99_000L);

        // Giao dịch cấp quyền thất bại ở mục thứ 2 sau khi mục thứ 1 đã flush -> phải rollback toàn bộ mục thứ 1.
        assertThat(calls.get()).isEqualTo(2);
        Map<String, Object> orderRow = jdbc.queryForMap(
                "SELECT state, fulfilled_at, fulfillment_tries, fulfillment_error FROM commerce.purchase_order WHERE id = ?",
                orderId);
        assertThat(orderRow.get("state")).isEqualTo("PAID");
        assertThat(orderRow.get("fulfilled_at")).isNull();
        assertThat(((Number) orderRow.get("fulfillment_tries")).intValue()).isEqualTo(1);
        assertThat((String) orderRow.get("fulfillment_error"))
                .contains("Simulated transient failure on 2nd bundle item");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM commerce.entitlement WHERE user_id = ?", Integer.class, learner.id())).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM commerce.entitlement_grant WHERE order_id = ?", Integer.class, orderId)).isZero();

        reset(spy);
        fulfillmentRetryWorker.retryPendingFulfillments();

        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, orderId))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM commerce.entitlement WHERE user_id = ?", Integer.class, learner.id())).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM commerce.entitlement_grant WHERE order_id = ?", Integer.class, orderId)).isEqualTo(3);
    }

    @Test
    void buyingBundleAndOverlappingSingleExamStacksFifoGrants() throws Exception {
        UUID mathId = examIdByCode(MATH_CODE);
        UUID vanId = examIdByCode(VAN_CODE);
        UUID anhId = examIdByCode(ANH_CODE);
        // Đảm bảo bài thi đơn DS-SP2E-MATH-2026 đã được project lên storefront (outbox không tự chạy trong test).
        storefrontProjectionService.project(mathId);
        try {
            LearnerAccount learner = fixtures.createLearner();

            JsonNode singleOrder = createOrder(learner, MATH_CODE, 39_000L);
            UUID singleOrderId = UUID.fromString(singleOrder.path("order").path("id").asText());
            payViaIpn(singleOrder.path("paymentUrl").asText(), 39_000L);

            JsonNode bundleOrder = createOrder(learner, BUNDLE_CODE, 99_000L);
            UUID bundleOrderId = UUID.fromString(bundleOrder.path("order").path("id").asText());
            payViaIpn(bundleOrder.path("paymentUrl").asText(), 99_000L);

            assertThat(totalAttemptsFor(learner.id(), mathId)).isEqualTo(6);
            assertThat(totalAttemptsFor(learner.id(), vanId)).isEqualTo(3);
            assertThat(totalAttemptsFor(learner.id(), anhId)).isEqualTo(3);

            List<UUID> mathGrantOrders = jdbc.queryForList("""
                    SELECT g.order_id
                    FROM commerce.entitlement_grant g
                    JOIN commerce.entitlement e ON e.id = g.entitlement_id
                    WHERE e.user_id = ? AND e.exam_id = ?
                    ORDER BY g.seq ASC
                    """, UUID.class, learner.id(), mathId);
            assertThat(mathGrantOrders).containsExactly(singleOrderId, bundleOrderId);
        } finally {
            jdbc.update("DELETE FROM commerce.storefront_product WHERE exam_id = ?", mathId);
        }
    }

    private int totalAttemptsFor(UUID userId, UUID examId) {
        Integer total = jdbc.queryForObject("""
                SELECT attempts_total
                FROM commerce.entitlement
                WHERE user_id = ? AND exam_id = ? AND source = 'PURCHASE' AND source_ref IS NULL
                """, Integer.class, userId, examId);
        return total == null ? 0 : total;
    }

    private UUID examIdByCode(String code) {
        return publishedExamPort.listPublishedExamIds().stream()
                .map(id -> publishedExamPort.findServing(id).orElse(null))
                .filter(s -> s != null && s.version() != null && code.equals(s.version().code()))
                .map(PublishedExamPort.ServingExam::examId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing DS exam: " + code));
    }

    private JsonNode createOrder(LearnerAccount learner, String productCode, long expectedAmountDue) throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "code", productCode,
                "expectedAccountId", learner.id(),
                "expectedVersionNo", 1,
                "expectedAmountDue", expectedAmountDue));
        String content = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(learner))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(content);
    }

    private Map<String, String> payViaIpn(String paymentUrl, long amountVnd) throws Exception {
        String txnRef = parseQueryParams(paymentUrl).get("vnp_TxnRef");
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", Long.toString(amountVnd * 100L));
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", "Thanh toan DS");
        params.put("vnp_PayDate", "20261121100500");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TmnCode", MockVnpayGateway.DEFAULT_MOCK_TMN_CODE);
        params.put("vnp_TransactionNo", "VNP" + CommerceTestFixtures.shortToken());
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_SecureHash", VnpaySigner.signPayParams(params, MockVnpayGateway.DEFAULT_MOCK_SECRET));

        sendIpn(params)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"));
        return params;
    }

    private org.springframework.test.web.servlet.ResultActions sendIpn(Map<String, String> params) throws Exception {
        var req = get("/api/v1/public/payments/vnpay/ipn")
                .with(r -> {
                    r.setRemoteAddr("127.0.0.1");
                    return r;
                });
        params.forEach(req::param);
        return mockMvc.perform(req);
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
