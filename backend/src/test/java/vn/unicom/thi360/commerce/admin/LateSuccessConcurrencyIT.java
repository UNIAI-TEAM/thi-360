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
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import vn.unicom.thi360.commerce.fulfillment.application.FulfillmentService;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.payment.application.PaymentOutcomeApplier;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpaySigner;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.outbox.OutboxRelay;
import vn.unicom.thi360.shared.security.SecurityProperties;

/**
 * Kiểm thử đồng thời xử lý tiền về muộn Ca C (AC-39, AC-40, BS §14):
 * tranh chấp {@code ACCEPT} vs {@code REFUND}, và tranh chấp với IPN / {@link PaymentOutcomeApplier}.
 */
class LateSuccessConcurrencyIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private PaymentOutcomeApplier outcomeApplier;
    @Autowired private FulfillmentService fulfillmentService;
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
    void acceptRacingRefundHasOneWinner() throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var exam = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var order = fixtures.insertStubOrder(
                buyer, exam, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var p1 = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.FAILED);
        jdbc.update("UPDATE commerce.payment SET late_success_review = 'PENDING', late_success_at = now() WHERE id = ?",
                p1.id());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CallResult acceptRes;
        CallResult refundRes;
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<CallResult> f1 = pool.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return callLateSuccess(order.code(), p1.id(), "ACCEPT", "EV-WIN-1");
            });
            Future<CallResult> f2 = pool.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return callLateSuccess(order.code(), p1.id(), "REFUND", "EV-WIN-2");
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            acceptRes = f1.get(30, TimeUnit.SECONDS);
            refundRes = f2.get(30, TimeUnit.SECONDS);
        }

        List<CallResult> all = List.of(acceptRes, refundRes);
        CallResult winner = all.stream().filter(r -> r.status() == 200).findFirst().orElseThrow();
        CallResult loser = all.stream().filter(r -> r.status() != 200).findFirst().orElseThrow();
        assertThat(all.stream().filter(r -> r.status() == 200).count()).isEqualTo(1L);
        assertThat(loser.status()).isEqualTo(409);
        assertThat(mapper.readTree(loser.body()).path("code").asText())
                .isEqualTo("LATE_SUCCESS_ALREADY_RESOLVED");

        while (outboxRelay.relayBatch() > 0) {
            // Đẩy audit.
        }

        if ("ACCEPT".equals(winner.decision())) {
            assertThat(jdbc.queryForObject(
                    "SELECT late_success_review FROM commerce.payment WHERE id = ?", String.class, p1.id()))
                    .isEqualTo("ACCEPTED");
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM commerce.entitlement_grant WHERE order_id = ?",
                    Integer.class, order.id())).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM audit.audit_event WHERE module = 'commerce' AND action = 'PAYMENT_LATE_SUCCESS_ACCEPTED' AND target_id = ?",
                    Integer.class, order.id().toString())).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM audit.audit_event WHERE module = 'commerce' AND action = 'PAYMENT_LATE_SUCCESS_REJECTED' AND target_id = ?",
                    Integer.class, order.id().toString())).isZero();
        } else {
            assertThat(jdbc.queryForObject(
                    "SELECT late_success_review FROM commerce.payment WHERE id = ?", String.class, p1.id()))
                    .isEqualTo("REJECTED");
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM commerce.entitlement_grant WHERE order_id = ?",
                    Integer.class, order.id())).isZero();
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM audit.audit_event WHERE module = 'commerce' AND action = 'PAYMENT_LATE_SUCCESS_REJECTED' AND target_id = ?",
                    Integer.class, order.id().toString())).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM audit.audit_event WHERE module = 'commerce' AND action = 'PAYMENT_LATE_SUCCESS_ACCEPTED' AND target_id = ?",
                    Integer.class, order.id().toString())).isZero();
        }
    }

    @Test
    void acceptFirstThenOtherIpnIsExcess() throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var exam = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var order = fixtures.insertStubOrder(
                buyer, exam, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var p1 = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.FAILED);
        var p2 = fixtures.insertStubPayment(order, 2, 200_000L, PaymentState.CREATED);
        jdbc.update("UPDATE commerce.payment SET late_success_review = 'PENDING', late_success_at = now() WHERE id = ?",
                p1.id());

        // 1. Admin ACCEPT p1 trước.
        CallResult accept = callLateSuccess(order.code(), p1.id(), "ACCEPT", "EV-AC40-1");
        assertThat(accept.status()).isEqualTo(200);
        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("FULFILLED");

        // 2. IPN báo 00/00 cho p2 -> p2 thành SUCCEEDED + is_duplicate=true + AFTER_COUNTED, IPN trả 00; gửi lại trả 02.
        assertThat(callIpnRspCode(p2.txnRef(), 200_000L, "90081002")).isEqualTo("00");
        assertThat(callIpnRspCode(p2.txnRef(), 200_000L, "90081002")).isEqualTo("02");

        Map<String, Object> p2Row = jdbc.queryForMap(
                "SELECT state, is_duplicate, excess_reason FROM commerce.payment WHERE id = ?", p2.id());
        assertThat(p2Row.get("state")).isEqualTo("SUCCEEDED");
        assertThat(p2Row.get("is_duplicate")).isEqualTo(true);
        assertThat(p2Row.get("excess_reason")).isEqualTo("AFTER_COUNTED");

        // Hoàn khoản thu thừa p2 giữ nguyên bài cho học viên.
        mockMvc.perform(post("/api/v1/admin/orders/" + order.code() + "/payments/" + p2.id() + "/refunds")
                        .with(asUser(UUID.randomUUID(), "ops-refund", Permissions.ORDER_REFUND))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "ac40-ref-dup-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "amountVnd", 200_000L,
                                "kind", "API",
                                "reason", "Hoan khoan thu thua p2",
                                "verifiedSystemError", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("SUCCEEDED"));

        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject(
                "SELECT attempts_revoked FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                Integer.class, buyer.id(), exam.examId())).isZero();
    }

    @Test
    void ipnFirstThenAcceptIs409() throws Exception {
        var buyer = fixtures.createLearner(true, "vi");
        var exam = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var order = fixtures.insertStubOrder(
                buyer, exam, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var p1 = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.FAILED);
        var p2 = fixtures.insertStubPayment(order, 2, 200_000L, PaymentState.CREATED);
        jdbc.update("UPDATE commerce.payment SET late_success_review = 'PENDING', late_success_at = now() WHERE id = ?",
                p1.id());

        // 1. IPN cho p2 thắng trước -> p2 được tính, p1 tự thành AUTO_EXCESS, đơn FULFILLED.
        assertThat(callIpnRspCode(p2.txnRef(), 200_000L, "90082002")).isEqualTo("00");
        assertThat(jdbc.queryForObject(
                "SELECT late_success_review FROM commerce.payment WHERE id = ?", String.class, p1.id()))
                .isEqualTo("AUTO_EXCESS");
        assertThat(jdbc.queryForObject(
                "SELECT is_duplicate FROM commerce.payment WHERE id = ?", Boolean.class, p1.id()))
                .isTrue();

        // 2. Admin ACCEPT p1 sau đó -> 409 LATE_SUCCESS_ALREADY_RESOLVED, giữ đúng 1 quyền.
        CallResult acceptAfter = callLateSuccess(order.code(), p1.id(), "ACCEPT", "EV-AC40-2");
        assertThat(acceptAfter.status()).isEqualTo(409);
        assertThat(mapper.readTree(acceptAfter.body()).path("code").asText())
                .isEqualTo("LATE_SUCCESS_ALREADY_RESOLVED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.entitlement_grant WHERE order_id = ?",
                Integer.class, order.id())).isEqualTo(1);
    }

    @Test
    void queryPathMatchesIpnPath() throws Exception {
        // Thử cả 2 thứ tự qua PaymentOutcomeApplier (đường dùng chung cho IPN và querydr):
        // (1) ACCEPT p1 cạnh tranh trực tiếp với PaymentOutcomeApplier trên p2 cùng đơn -> tối đa 1 khoản được tính, 1 quyền.
        var buyer1 = fixtures.createLearner(true, "vi");
        var exam = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var oRace = fixtures.insertStubOrder(
                buyer1, exam, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var p1Race = fixtures.insertStubPayment(oRace, 1, 200_000L, PaymentState.FAILED);
        var p2Race = fixtures.insertStubPayment(oRace, 2, 200_000L, PaymentState.CREATED);
        jdbc.update("UPDATE commerce.payment SET late_success_review = 'PENDING', late_success_at = now() WHERE id = ?",
                p1Race.id());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CallResult acceptRace;
        PaymentOutcomeApplier.ApplyResult applierRace;
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<CallResult> fAdmin = pool.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return callLateSuccess(oRace.code(), p1Race.id(), "ACCEPT", "EV-QRY-RACE");
            });
            Future<PaymentOutcomeApplier.ApplyResult> fQuery = pool.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                var res = outcomeApplier.applyIpnOutcome(
                        p2Race.id(),
                        oRace.id(),
                        Map.of(
                                "vnp_ResponseCode", "00",
                                "vnp_TransactionStatus", "00",
                                "vnp_TransactionNo", "90083002",
                                "vnp_BankCode", "NCB",
                                "vnp_CardType", "ATM",
                                "vnp_PayDate", "20261121100500"),
                        Map.of("source", "QUERYDR_TEST"));
                if (res.fulfillOrderId() != null) {
                    fulfillmentService.fulfill(res.fulfillOrderId());
                }
                return res;
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            acceptRace = fAdmin.get(30, TimeUnit.SECONDS);
            applierRace = fQuery.get(30, TimeUnit.SECONDS);
        }

        // Không có lỗi tranh khóa: PaymentOutcomeApplier luôn trả 00, admin chỉ 200 hoặc 409.
        assertThat(applierRace.rspCode()).isEqualTo("00");
        assertThat(acceptRace.status()).isIn(200, 409);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.payment WHERE order_id = ? AND state = 'SUCCEEDED' AND is_duplicate = false",
                Integer.class, oRace.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.payment WHERE order_id = ? AND state = 'SUCCEEDED' AND is_duplicate = true AND excess_reason = 'AFTER_COUNTED'",
                Integer.class, oRace.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.entitlement_grant WHERE order_id = ?",
                Integer.class, oRace.id())).isEqualTo(1);

        // (2) Thứ tự tuần tự ngược lại: PaymentOutcomeApplier áp p2 trước -> ACCEPT p1 trả 409.
        var buyer2 = fixtures.createLearner(true, "vi");
        var oSeq = fixtures.insertStubOrder(
                buyer2, exam, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var p1Seq = fixtures.insertStubPayment(oSeq, 1, 200_000L, PaymentState.FAILED);
        var p2Seq = fixtures.insertStubPayment(oSeq, 2, 200_000L, PaymentState.CREATED);
        jdbc.update("UPDATE commerce.payment SET late_success_review = 'PENDING', late_success_at = now() WHERE id = ?",
                p1Seq.id());

        var seqRes = outcomeApplier.applyIpnOutcome(
                p2Seq.id(),
                oSeq.id(),
                Map.of(
                        "vnp_ResponseCode", "00",
                        "vnp_TransactionStatus", "00",
                        "vnp_TransactionNo", "90083009",
                        "vnp_BankCode", "NCB",
                        "vnp_CardType", "ATM",
                        "vnp_PayDate", "20261121100500"),
                Map.of("source", "QUERYDR_SEQ"));
        assertThat(seqRes.rspCode()).isEqualTo("00");
        assertThat(fulfillmentService.fulfill(seqRes.fulfillOrderId())).isTrue();

        CallResult seqAccept = callLateSuccess(oSeq.code(), p1Seq.id(), "ACCEPT", "EV-QRY-SEQ");
        assertThat(seqAccept.status()).isEqualTo(409);
        assertThat(mapper.readTree(seqAccept.body()).path("code").asText())
                .isEqualTo("LATE_SUCCESS_ALREADY_RESOLVED");
    }

    private CallResult callLateSuccess(String orderCode, UUID paymentId, String decision, String evidenceRef)
            throws Exception {
        var res = mockMvc.perform(post("/api/v1/admin/orders/" + orderCode + "/payments/" + paymentId + "/late-success")
                        .with(asUser(UUID.randomUUID(), "ops-" + decision.toLowerCase(), Permissions.ORDER_RECOVER))
                        .header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst())
                        .header("Idempotency-Key", "ls-conc-" + decision + "-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "decision", decision,
                                "reason", "Xu ly dong thoi " + decision,
                                "evidenceRef", evidenceRef))))
                .andReturn()
                .getResponse();
        return new CallResult(decision, res.getStatus(), res.getContentAsString());
    }

    private String callIpnRspCode(String txnRef, long amountVnd, String txnNo) throws Exception {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", Long.toString(amountVnd * 100L));
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", "Thanh toan don test");
        params.put("vnp_PayDate", "20261121100100");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TmnCode", MockVnpayGateway.DEFAULT_MOCK_TMN_CODE);
        params.put("vnp_TransactionNo", txnNo);
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_SecureHash", VnpaySigner.signPayParams(params, MockVnpayGateway.DEFAULT_MOCK_SECRET));
        MockHttpServletRequestBuilder req = get("/api/v1/public/payments/vnpay/ipn");
        params.forEach(req::param);
        String body = mockMvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("RspCode").asText();
    }

    private record CallResult(String decision, int status, String body) {
    }
}
