package vn.unicom.thi360.commerce.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.fulfillment.application.FulfillmentRetryWorker;
import vn.unicom.thi360.commerce.fulfillment.application.FulfillmentService;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.QueryDrResult;
import vn.unicom.thi360.commerce.payment.gateway.VnpaySigner;
import vn.unicom.thi360.commerce.payment.vnpay.QueryDrService;
import vn.unicom.thi360.commerce.payment.vnpay.VnpayTestClockConfig;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.security.SecurityProperties;
import vn.unicom.thi360.shared.testapi.MutableClock;

@Import(VnpayTestClockConfig.class)
@TestPropertySource(properties = {
    "thi360.commerce.scheduling.enabled=true",
    "thi360.commerce.fulfillment-retry.interval=PT1H",
    "thi360.commerce.querydr.interval=PT1H",
    "thi360.commerce.order-expiry.interval=PT1H"
})
class DeletedAccountPaymentIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private FulfillmentService fulfillmentService;
    @Autowired private FulfillmentRetryWorker retryWorker;
    @Autowired private QueryDrService queryDrService;
    @Autowired private OutboxRelay outboxRelay;
    @Autowired private SecurityProperties securityProperties;
    @Autowired private MutableClock mutableClock;

    @MockitoSpyBean
    private VnpayGateway vnpayGateway;

    private CommerceTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        reset(vnpayGateway);
        mutableClock.setAt(Instant.now());
        fixtures = new CommerceTestFixtures(jdbc, mapper, catalogLookup, promotionRepository, testSecuritySupport);
    }

    @AfterEach
    void tearDown() {
        reset(vnpayGateway);
        mutableClock.reset();
        jdbc.update("DELETE FROM commerce.refund");
        fixtures.cleanup();
    }

    @ParameterizedTest
    @EnumSource(value = OrderState.class, names = {"PENDING_PAYMENT", "CANCELLED", "EXPIRED", "REPLACED"})
    void ipnAfterDeletion(OrderState initialState) throws Exception {
        var buyer = fixtures.createLearner(true, "vi", 3, true);
        var exam = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var order = fixtures.insertStubOrder(
                buyer, exam, initialState, PaymentMethod.VNPAY,
                200_000L, mutableClock.instant().plusSeconds(900), null);
        if (initialState == OrderState.REPLACED) {
            var replacement = fixtures.insertStubOrder(
                    buyer, exam, OrderState.CANCELLED, PaymentMethod.VNPAY,
                    200_000L, mutableClock.instant().plusSeconds(900), null);
            jdbc.update("UPDATE commerce.purchase_order SET replaced_by_order_id = ? WHERE id = ?",
                    replacement.id(), order.id());
        }
        var payment = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.CREATED);

        callIpn(payment.txnRef(), 200_000L, "00", "00", "99100001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"));

        Map<String, Object> payRow = jdbc.queryForMap(
                "SELECT state, is_duplicate FROM commerce.payment WHERE id = ?", payment.id());
        assertThat(payRow.get("state")).isEqualTo("SUCCEEDED");
        assertThat(payRow.get("is_duplicate")).isEqualTo(false);

        Map<String, Object> orderRow = jdbc.queryForMap(
                "SELECT state, needs_review, review_reason, fulfillment_tries, fulfillment_error "
                        + "FROM commerce.purchase_order WHERE id = ?",
                order.id());
        assertThat(orderRow.get("state")).isEqualTo("PAID");
        assertThat(orderRow.get("needs_review")).isEqualTo(true);
        assertThat(orderRow.get("review_reason")).isEqualTo("ACCOUNT_DELETED");
        assertThat(((Number) orderRow.get("fulfillment_tries")).intValue()).isZero();
        assertThat(orderRow.get("fulfillment_error")).isNull();

        assertThat(countSkippedHistory(order.id())).isEqualTo(1);
        assertThat(countEntitlementGrants(order.id())).isZero();

        mockMvc.perform(get("/api/v1/admin/orders")
                        .with(asUser(UUID.randomUUID(), "ops-viewer", Permissions.ORDER_VIEW))
                        .param("attention", "NEEDS_REVIEW")
                        .param("q", order.code()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].code", Matchers.hasItem(order.code())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"QUERYDR", "LATE_SUCCESS", "RETRY_WORKER", "ORDER_RECOVER"})
    void otherFulfillPaths(String path) throws Exception {
        var buyer = fixtures.createLearner(true, "vi", 3, true);
        var exam = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);

        switch (path) {
            case "QUERYDR" -> {
                var qdrOrder = fixtures.insertStubOrder(
                        buyer, exam, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                        200_000L, mutableClock.instant().plusSeconds(900), null);
                var qdrPay = fixtures.insertStubPayment(qdrOrder, 1, 200_000L, PaymentState.CREATED);
                doReturn(queryResult(qdrPay.txnRef(), 200_000L, "00", "00")).when(vnpayGateway).queryDr(any());
                queryDrService.queryPaymentNow(qdrPay.id());
                assertPaidAccountDeletedNoGrants(qdrOrder.id());
                assertThat(countSkippedHistory(qdrOrder.id())).isEqualTo(1);
            }
            case "LATE_SUCCESS" -> {
                var lateOrder = fixtures.insertStubOrder(
                        buyer, exam, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                        200_000L, mutableClock.instant().plusSeconds(900), null);
                var latePay = fixtures.insertStubPayment(lateOrder, 1, 200_000L, PaymentState.FAILED);
                jdbc.update(
                        "UPDATE commerce.payment SET late_success_review = 'PENDING', late_success_at = now() WHERE id = ?",
                        latePay.id());
                mockMvc.perform(post("/api/v1/admin/orders/" + lateOrder.code() + "/payments/" + latePay.id() + "/late-success")
                                .with(asUser(UUID.randomUUID(), "ops-recover", Permissions.ORDER_RECOVER))
                                .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                                .header("Idempotency-Key", "del-ls-" + UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(Map.of(
                                        "decision", "ACCEPT",
                                        "reason", "Doi soat tien ve muon cho tai khoan da xoa",
                                        "evidenceRef", "EV-DEL-1"))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.state").value("PAID"))
                        .andExpect(jsonPath("$.needsReview").value(true))
                        .andExpect(jsonPath("$.reviewReason").value("ACCOUNT_DELETED"));
                assertPaidAccountDeletedNoGrants(lateOrder.id());
                assertThat(countSkippedHistory(lateOrder.id())).isEqualTo(1);
            }
            case "RETRY_WORKER" -> {
                var retryOrder = fixtures.insertStubOrder(
                        buyer, exam, OrderState.PAID, PaymentMethod.VNPAY,
                        200_000L, null, mutableClock.instant().minus(Duration.ofMinutes(5)));
                retryWorker.retryPendingFulfillments();
                assertPaidAccountDeletedNoGrants(retryOrder.id());
                assertThat(countSkippedHistory(retryOrder.id())).isEqualTo(1);
            }
            case "ORDER_RECOVER" -> {
                var unflaggedOrder = fixtures.insertStubOrder(
                        buyer, exam, OrderState.PAID, PaymentMethod.VNPAY,
                        200_000L, null, mutableClock.instant());
                fixtures.insertStubPayment(unflaggedOrder, 1, 200_000L, PaymentState.SUCCEEDED);

                mockMvc.perform(post("/api/v1/admin/orders/" + unflaggedOrder.code() + "/fulfill")
                                .with(asUser(UUID.randomUUID(), "ops-recover", Permissions.ORDER_RECOVER))
                                .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                                .header("Idempotency-Key", "del-rec-unflagged-" + UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(Map.of("reason", "Thu cap lai don chua gan co"))))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("ORDER_RECOVERY_NOT_ALLOWED"));

                while (outboxRelay.relayBatch() > 0) {
                }
                assertPaidAccountDeletedNoGrants(unflaggedOrder.id());
                assertThat(countSkippedHistory(unflaggedOrder.id())).isEqualTo(1);
                assertThat(countSkippedAudit(unflaggedOrder.id())).isEqualTo(1);

                mockMvc.perform(post("/api/v1/admin/orders/" + unflaggedOrder.code() + "/fulfill")
                                .with(asUser(UUID.randomUUID(), "ops-recover", Permissions.ORDER_RECOVER))
                                .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                                .header("Idempotency-Key", "del-rec-flagged-" + UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(Map.of("reason", "Thu cap lai don da gan co"))))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("ORDER_RECOVERY_NOT_ALLOWED"));

                while (outboxRelay.relayBatch() > 0) {
                }
                assertPaidAccountDeletedNoGrants(unflaggedOrder.id());
                assertThat(countSkippedHistory(unflaggedOrder.id())).isEqualTo(1);
                assertThat(countSkippedAudit(unflaggedOrder.id())).isEqualTo(1);
            }
            default -> throw new IllegalArgumentException("Unknown path: " + path);
        }
    }

    @Test
    void repeatedIpn() throws Exception {
        var buyer = fixtures.createLearner(true, "vi", 3, true);
        var exam = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);

        var order1 = fixtures.insertStubOrder(
                buyer, exam, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, mutableClock.instant().plusSeconds(900), null);
        var pay1 = fixtures.insertStubPayment(order1, 1, 200_000L, PaymentState.CREATED);

        callIpn(pay1.txnRef(), 200_000L, "00", "00", "99200001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"));
        callIpn(pay1.txnRef(), 200_000L, "00", "00", "99200001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("02"));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM commerce.payment WHERE order_id = ? AND state = 'SUCCEEDED' AND is_duplicate = false",
                Integer.class, order1.id())).isEqualTo(1);
        assertPaidAccountDeletedNoGrants(order1.id());
        assertThat(countSkippedHistory(order1.id())).isEqualTo(1);

        var order2 = fixtures.insertStubOrder(
                buyer, exam, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, mutableClock.instant().plusSeconds(900), null);
        var pay2 = fixtures.insertStubPayment(order2, 1, 200_000L, PaymentState.CREATED);

        callIpn(pay2.txnRef(), 200_000L, "00", "00", "99200002")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"));
        doReturn(queryResult(pay2.txnRef(), 200_000L, "00", "00")).when(vnpayGateway).queryDr(any());
        queryDrService.queryPaymentNow(pay2.id());

        assertPaidAccountDeletedNoGrants(order2.id());
        assertThat(countSkippedHistory(order2.id())).isEqualTo(1);
    }

    @Test
    void retryWorkerSkipsOnce() {
        var buyer = fixtures.createLearner(true, "vi", 3, true);
        var exam = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);

        var order1 = fixtures.insertStubOrder(
                buyer, exam, OrderState.PAID, PaymentMethod.VNPAY,
                200_000L, null, mutableClock.instant().minus(Duration.ofMinutes(2)));
        assertThat(fulfillmentService.fulfill(order1.id())).isFalse();
        assertThat(fulfillmentService.fulfill(order1.id())).isFalse();
        assertThat(fulfillmentService.fulfill(order1.id())).isFalse();

        while (outboxRelay.relayBatch() > 0) {
        }
        assertPaidAccountDeletedNoGrants(order1.id());
        assertThat(countSkippedHistory(order1.id())).isEqualTo(1);
        assertThat(countSkippedAudit(order1.id())).isEqualTo(1);

        var order2 = fixtures.insertStubOrder(
                buyer, exam, OrderState.PAID, PaymentMethod.VNPAY,
                200_000L, null, mutableClock.instant().minus(Duration.ofMinutes(2)));
        for (int round = 0; round < 3; round++) {
            retryWorker.retryPendingFulfillments();
            mutableClock.setAt(mutableClock.instant().plus(Duration.ofMinutes(1)));
        }

        while (outboxRelay.relayBatch() > 0) {
        }
        assertPaidAccountDeletedNoGrants(order2.id());
        assertThat(countSkippedHistory(order2.id())).isEqualTo(1);
        assertThat(countSkippedAudit(order2.id())).isEqualTo(1);
    }

    @Test
    void keepsExistingReviewReason() {
        var buyer = fixtures.createLearner(true, "vi", 3, true);
        var exam = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var order = fixtures.insertStubOrder(
                buyer, exam, OrderState.PAID, PaymentMethod.VNPAY,
                200_000L, null, mutableClock.instant());
        jdbc.update(
                "UPDATE commerce.purchase_order SET needs_review = true, review_reason = 'DOUBLE_CHARGE_REPLACED_ORDER' WHERE id = ?",
                order.id());

        assertThat(fulfillmentService.fulfill(order.id())).isFalse();
        assertThat(fulfillmentService.fulfill(order.id())).isFalse();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT state, needs_review, review_reason, fulfillment_tries, fulfillment_error "
                        + "FROM commerce.purchase_order WHERE id = ?",
                order.id());
        assertThat(row.get("state")).isEqualTo("PAID");
        assertThat(row.get("needs_review")).isEqualTo(true);
        assertThat(row.get("review_reason")).isEqualTo("DOUBLE_CHARGE_REPLACED_ORDER");
        assertThat(((Number) row.get("fulfillment_tries")).intValue()).isZero();
        assertThat(row.get("fulfillment_error")).isNull();
        assertThat(countSkippedHistory(order.id())).isEqualTo(1);
        assertThat(countEntitlementGrants(order.id())).isZero();
    }

    private void assertPaidAccountDeletedNoGrants(UUID orderId) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT state, needs_review, review_reason, fulfillment_tries, fulfillment_error "
                        + "FROM commerce.purchase_order WHERE id = ?",
                orderId);
        assertThat(row.get("state")).isEqualTo("PAID");
        assertThat(row.get("needs_review")).isEqualTo(true);
        assertThat(row.get("review_reason")).isEqualTo("ACCOUNT_DELETED");
        assertThat(((Number) row.get("fulfillment_tries")).intValue()).isZero();
        assertThat(row.get("fulfillment_error")).isNull();
        assertThat(countEntitlementGrants(orderId)).isZero();
    }

    private int countSkippedHistory(UUID orderId) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM commerce.order_history WHERE order_id = ? AND reason = 'FULFILLMENT_SKIPPED_ACCOUNT_DELETED'",
                Integer.class, orderId);
        return n == null ? 0 : n;
    }

    private int countSkippedAudit(UUID orderId) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM audit.audit_event WHERE module = 'commerce' AND action = 'ORDER_FULFILLMENT_SKIPPED_ACCOUNT_DELETED' AND target_id = ?",
                Integer.class, orderId.toString());
        return n == null ? 0 : n;
    }

    private int countEntitlementGrants(UUID orderId) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM commerce.entitlement_grant WHERE order_id = ?",
                Integer.class, orderId);
        return n == null ? 0 : n;
    }

    private org.springframework.test.web.servlet.ResultActions callIpn(
            String txnRef, long amountVnd, String responseCode, String txStatus, String txnNo) throws Exception {
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
        return mockMvc.perform(req);
    }

    private static QueryDrResult queryResult(String txnRef, long amountVnd, String responseCode, String txnStatus) {
        return new QueryDrResult(
                true,
                "resp-" + UUID.randomUUID().toString().substring(0, 8),
                responseCode,
                "Msg",
                txnRef,
                amountVnd,
                "NCB",
                "20261121100200",
                "99300001",
                "01",
                txnStatus,
                "Info",
                null,
                null,
                Map.of("vnp_ResponseCode", responseCode, "vnp_TransactionStatus", txnStatus, "vnp_TxnRef", txnRef));
    }
}
