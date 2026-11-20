package vn.unicom.thi360.commerce.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.payment.domain.ExcessReason;
import vn.unicom.thi360.commerce.payment.domain.LateSuccessReview;
import vn.unicom.thi360.commerce.payment.domain.Payment;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpaySigner;
import vn.unicom.thi360.commerce.payment.infrastructure.PaymentRepository;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;

/**
 * Kiểm thử tích hợp cho quy tắc đếm khoản thu mỗi đơn (AC-41..AC-47, TM §8.4, BS §14):
 * chỉ mục duy nhất {@code uq_payment_counted_per_order}, cạnh tranh thành công song song,
 * Ca B sau {@code AFTER_COUNTED} / {@code REFUNDED}, Ca C chờ đối soát và chuyển {@code AUTO_EXCESS},
 * Ca D thông báo lặp {@code 02}, và ca chuẩn BS 14.
 */
class PaymentCountingRulesIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private EntitlementGrantService entitlementGrantService;

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
    void oneCountedPaymentPerOrder() throws Exception {
        CommerceTestFixtures.LearnerAccount buyer = fixtures.createLearner();
        CommerceTestFixtures.StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);

        OrderHandle created = createOrderViaApi(buyer, product);
        callIpn(created.firstTxnRef(), product.listPriceVnd(), "00", "00", "900101", "NCB", "ATM")
                .andExpect(jsonPath("$.RspCode").value("00"));

        CommerceTestFixtures.StubPayment attempt2 = fixtures.insertStubPayment(
                new CommerceTestFixtures.StubOrder(created.orderId(), created.orderCode()),
                2,
                product.listPriceVnd(),
                PaymentState.CREATED);
        Payment secondCounted = paymentRepository.findByTxnRef(attempt2.txnRef()).orElseThrow();
        secondCounted.markCountedSuccess(
                "900102", "NCB", "ATM", "20261121100500", "00", "00", Instant.now());

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(secondCounted))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void parallelSuccessCountsOnce() throws Exception {
        CommerceTestFixtures.LearnerAccount buyer = fixtures.createLearner();
        CommerceTestFixtures.StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 220_000L, 2);

        OrderHandle created = createOrderViaApi(buyer, product);
        CommerceTestFixtures.StubPayment attempt2 = fixtures.insertStubPayment(
                new CommerceTestFixtures.StubOrder(created.orderId(), created.orderCode()),
                2,
                product.listPriceVnd(),
                PaymentState.CREATED);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> futures = new ArrayList<>();
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return callIpnAndReadRspCode(
                        created.firstTxnRef(), product.listPriceVnd(), "00", "00", "900201", "NCB", "ATM");
            }));
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return callIpnAndReadRspCode(
                        attempt2.txnRef(), product.listPriceVnd(), "00", "00", "900202", "VCB", "QRCODE");
            }));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            for (Future<String> future : futures) {
                assertThat(future.get(15, TimeUnit.SECONDS)).isEqualTo("00");
            }
        } finally {
            pool.shutdownNow();
        }

        List<Payment> payments = paymentRepository.findByOrderIdOrderByCreatedAtDesc(created.orderId());
        assertThat(payments).hasSize(2);
        assertThat(payments.stream().filter(p -> p.getState() == PaymentState.SUCCEEDED && !p.isDuplicate()).count())
                .isEqualTo(1L);
        assertThat(payments.stream().filter(p ->
                        p.getState() == PaymentState.SUCCEEDED
                                && p.isDuplicate()
                                && p.getExcessReason() == ExcessReason.AFTER_COUNTED)
                .count()).isEqualTo(1L);
        assertThat(orderState(created.orderId())).isEqualTo("FULFILLED");
        assertThat(grantCount(created.orderId())).isEqualTo(1);
    }

    @Test
    void failedAttemptAfterCountedIsExcess() throws Exception {
        CommerceTestFixtures.LearnerAccount buyer = fixtures.createLearner();
        CommerceTestFixtures.StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);

        OrderHandle created = createOrderViaApi(buyer, product);
        callIpn(created.firstTxnRef(), product.listPriceVnd(), "24", "02", "0", "NCB", "ATM")
                .andExpect(jsonPath("$.RspCode").value("00"));

        String secondTxnRef = retryPayViaApi(buyer, created.orderCode());
        callIpn(secondTxnRef, product.listPriceVnd(), "00", "00", "900302", "NCB", "ATM")
                .andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(orderState(created.orderId())).isEqualTo("FULFILLED");
        assertThat(grantCount(created.orderId())).isEqualTo(1);

        // Ca B thuần trên lần đã FAILED: không đi qua PENDING mà thành thu thừa AFTER_COUNTED, giữ late_success_review = null.
        callIpn(created.firstTxnRef(), product.listPriceVnd(), "00", "00", "900301", "VCB", "QRCODE")
                .andExpect(jsonPath("$.RspCode").value("00"));

        Payment p1 = paymentRepository.findByTxnRef(created.firstTxnRef()).orElseThrow();
        assertThat(p1.getState()).isEqualTo(PaymentState.SUCCEEDED);
        assertThat(p1.isDuplicate()).isTrue();
        assertThat(p1.getExcessReason()).isEqualTo(ExcessReason.AFTER_COUNTED);
        assertThat(p1.getLateSuccessReview()).isNull();
        assertThat(p1.getVnpTransactionNo()).isEqualTo("900301");
        assertThat(p1.getBankCode()).isEqualTo("VCB");
        assertThat(p1.getCardType()).isEqualTo("QRCODE");
        assertThat(orderState(created.orderId())).isEqualTo("FULFILLED");
        assertThat(grantCount(created.orderId())).isEqualTo(1);
    }

    @Test
    void openAttemptAfterCountedIsExcess() throws Exception {
        CommerceTestFixtures.LearnerAccount buyer = fixtures.createLearner();
        CommerceTestFixtures.StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 180_000L, 2);

        OrderHandle created = createOrderViaApi(buyer, product);
        CommerceTestFixtures.StubPayment attempt2 = fixtures.insertStubPayment(
                new CommerceTestFixtures.StubOrder(created.orderId(), created.orderCode()),
                2,
                product.listPriceVnd(),
                PaymentState.CREATED);

        callIpn(attempt2.txnRef(), product.listPriceVnd(), "00", "00", "900402", "NCB", "ATM")
                .andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(orderState(created.orderId())).isEqualTo("FULFILLED");
        assertThat(grantCount(created.orderId())).isEqualTo(1);

        callIpn(created.firstTxnRef(), product.listPriceVnd(), "00", "00", "900401", "BIDV", "ATM")
                .andExpect(jsonPath("$.RspCode").value("00"));

        Payment p1 = paymentRepository.findByTxnRef(created.firstTxnRef()).orElseThrow();
        assertThat(p1.getState()).isEqualTo(PaymentState.SUCCEEDED);
        assertThat(p1.isDuplicate()).isTrue();
        assertThat(p1.getExcessReason()).isEqualTo(ExcessReason.AFTER_COUNTED);
        assertThat(p1.getLateSuccessReview()).isNull();
        assertThat(orderState(created.orderId())).isEqualTo("FULFILLED");
        assertThat(grantCount(created.orderId())).isEqualTo(1);
    }

    @Test
    void successAfterRefundNeverReopens() throws Exception {
        CommerceTestFixtures.LearnerAccount buyer = fixtures.createLearner();
        CommerceTestFixtures.StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 250_000L, 2);

        OrderHandle created = createOrderViaApi(buyer, product);
        CommerceTestFixtures.StubPayment lateAttempt = fixtures.insertStubPayment(
                new CommerceTestFixtures.StubOrder(created.orderId(), created.orderCode()),
                2,
                product.listPriceVnd(),
                PaymentState.CREATED);

        callIpn(created.firstTxnRef(), product.listPriceVnd(), "00", "00", "900501", "NCB", "ATM")
                .andExpect(jsonPath("$.RspCode").value("00"));
        UUID grantId = jdbc.queryForObject(
                "SELECT id FROM commerce.entitlement_grant WHERE order_id = ?",
                UUID.class,
                created.orderId());
        entitlementGrantService.revokeUnused(grantId);
        jdbc.update("UPDATE commerce.purchase_order SET state = ? WHERE id = ?",
                OrderState.REFUNDED.name(), created.orderId());

        int grantsBefore = grantCount(created.orderId());
        assertThat(remainingAttempts(buyer.id(), product.examId())).isZero();

        callIpn(lateAttempt.txnRef(), product.listPriceVnd(), "00", "00", "900502", "TCB", "ATM")
                .andExpect(jsonPath("$.RspCode").value("00"));

        Payment late = paymentRepository.findByTxnRef(lateAttempt.txnRef()).orElseThrow();
        assertThat(late.getState()).isEqualTo(PaymentState.SUCCEEDED);
        assertThat(late.isDuplicate()).isTrue();
        assertThat(late.getExcessReason()).isEqualTo(ExcessReason.AFTER_COUNTED);
        assertThat(orderState(created.orderId())).isEqualTo("REFUNDED");
        assertThat(grantCount(created.orderId())).isEqualTo(grantsBefore);
        assertThat(remainingAttempts(buyer.id(), product.examId())).isZero();
    }

    @Test
    void failedAttemptWaitsForReconcile() throws Exception {
        CommerceTestFixtures.LearnerAccount buyer = fixtures.createLearner();
        CommerceTestFixtures.StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);

        OrderHandle created = createOrderViaApi(buyer, product);
        callIpn(created.firstTxnRef(), product.listPriceVnd(), "24", "02", "0", "NCB", "ATM")
                .andExpect(jsonPath("$.RspCode").value("00"));

        callIpn(created.firstTxnRef(), product.listPriceVnd(), "00", "00", "900601", "BIDV", "ATM")
                .andExpect(jsonPath("$.RspCode").value("00"));

        Payment p1 = paymentRepository.findByTxnRef(created.firstTxnRef()).orElseThrow();
        assertThat(p1.getState()).isEqualTo(PaymentState.FAILED);
        assertThat(p1.isDuplicate()).isFalse();
        assertThat(p1.getLateSuccessReview()).isEqualTo(LateSuccessReview.PENDING);
        assertThat(p1.getLateSuccessAt()).isNotNull();
        assertThat(p1.getVnpTransactionNo()).isEqualTo("900601");
        assertThat(p1.getBankCode()).isEqualTo("BIDV");
        assertThat(orderState(created.orderId())).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject(
                "SELECT needs_review FROM commerce.purchase_order WHERE id = ?",
                Boolean.class,
                created.orderId())).isFalse();
        assertThat(grantCount(created.orderId())).isZero();
    }

    @Test
    void pendingReviewBecomesExcessWhenAnotherCounts() throws Exception {
        CommerceTestFixtures.LearnerAccount buyer = fixtures.createLearner();
        CommerceTestFixtures.StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);

        OrderHandle created = createOrderViaApi(buyer, product);
        callIpn(created.firstTxnRef(), product.listPriceVnd(), "24", "02", "0", "NCB", "ATM")
                .andExpect(jsonPath("$.RspCode").value("00"));
        callIpn(created.firstTxnRef(), product.listPriceVnd(), "00", "00", "900701", "VCB", "ATM")
                .andExpect(jsonPath("$.RspCode").value("00"));

        String secondTxnRef = retryPayViaApi(buyer, created.orderCode());
        callIpn(secondTxnRef, product.listPriceVnd(), "00", "00", "900702", "NCB", "ATM")
                .andExpect(jsonPath("$.RspCode").value("00"));

        Payment p1 = paymentRepository.findByTxnRef(created.firstTxnRef()).orElseThrow();
        Payment p2 = paymentRepository.findByTxnRef(secondTxnRef).orElseThrow();
        assertThat(p2.getState()).isEqualTo(PaymentState.SUCCEEDED);
        assertThat(p2.isDuplicate()).isFalse();
        assertThat(p2.getExcessReason()).isNull();

        assertThat(p1.getState()).isEqualTo(PaymentState.SUCCEEDED);
        assertThat(p1.isDuplicate()).isTrue();
        assertThat(p1.getExcessReason()).isEqualTo(ExcessReason.AFTER_COUNTED);
        assertThat(p1.getLateSuccessReview()).isEqualTo(LateSuccessReview.AUTO_EXCESS);

        assertThat(orderState(created.orderId())).isEqualTo("FULFILLED");
        assertThat(grantCount(created.orderId())).isEqualTo(1);
    }

    @Test
    void repeatedNoticeAddsNothing() throws Exception {
        CommerceTestFixtures.LearnerAccount buyer = fixtures.createLearner();
        CommerceTestFixtures.StoredTestProduct pBProduct = fixtures.insertOnSaleProduct("HSA", 190_000L, 1);
        CommerceTestFixtures.StoredTestProduct pCProduct = fixtures.insertOnSaleProduct("HSA", 210_000L, 1);

        // Ca D sau Ca B: payment đã sang SUCCEEDED + is_duplicate=true + AFTER_COUNTED.
        OrderHandle orderB = createOrderViaApi(buyer, pBProduct);
        callIpn(orderB.firstTxnRef(), pBProduct.listPriceVnd(), "24", "02", "0", "NCB", "ATM")
                .andExpect(jsonPath("$.RspCode").value("00"));
        String bTxn2 = retryPayViaApi(buyer, orderB.orderCode());
        callIpn(bTxn2, pBProduct.listPriceVnd(), "00", "00", "900802", "NCB", "ATM")
                .andExpect(jsonPath("$.RspCode").value("00"));
        callIpn(orderB.firstTxnRef(), pBProduct.listPriceVnd(), "00", "00", "900801", "VCB", "ATM")
                .andExpect(jsonPath("$.RspCode").value("00"));

        Payment pB1 = paymentRepository.findByTxnRef(orderB.firstTxnRef()).orElseThrow();
        int bEventsBefore = paymentEventCount(pB1.getId());
        int bHistoryBefore = orderHistoryCount(orderB.orderId());
        long bExcessBefore = refundableExcessSum(orderB.orderId());

        callIpn(orderB.firstTxnRef(), pBProduct.listPriceVnd(), "00", "00", "900801", "VCB", "ATM")
                .andExpect(jsonPath("$.RspCode").value("02"));
        assertThat(paymentEventCount(pB1.getId())).isEqualTo(bEventsBefore + 1);
        assertThat(orderHistoryCount(orderB.orderId())).isEqualTo(bHistoryBefore);
        assertThat(refundableExcessSum(orderB.orderId())).isEqualTo(bExcessBefore);

        // Ca D sau Ca C: payment đang FAILED + late_success_review=PENDING.
        CommerceTestFixtures.LearnerAccount buyerC = fixtures.createLearner();
        OrderHandle orderC = createOrderViaApi(buyerC, pCProduct);
        callIpn(orderC.firstTxnRef(), pCProduct.listPriceVnd(), "24", "02", "0", "NCB", "ATM")
                .andExpect(jsonPath("$.RspCode").value("00"));
        callIpn(orderC.firstTxnRef(), pCProduct.listPriceVnd(), "00", "00", "900803", "BIDV", "ATM")
                .andExpect(jsonPath("$.RspCode").value("00"));

        Payment pC1 = paymentRepository.findByTxnRef(orderC.firstTxnRef()).orElseThrow();
        int cEventsBefore = paymentEventCount(pC1.getId());
        int cHistoryBefore = orderHistoryCount(orderC.orderId());

        callIpn(orderC.firstTxnRef(), pCProduct.listPriceVnd(), "00", "00", "900803", "BIDV", "ATM")
                .andExpect(jsonPath("$.RspCode").value("02"));
        Payment pC1After = paymentRepository.findByTxnRef(orderC.firstTxnRef()).orElseThrow();
        assertThat(paymentEventCount(pC1.getId())).isEqualTo(cEventsBefore + 1);
        assertThat(orderHistoryCount(orderC.orderId())).isEqualTo(cHistoryBefore);
        assertThat(pC1After.getState()).isEqualTo(PaymentState.FAILED);
        assertThat(pC1After.getLateSuccessReview()).isEqualTo(LateSuccessReview.PENDING);
        assertThat(grantCount(orderC.orderId())).isZero();
    }

    @Test
    void failedThenRetriedThenLateSuccessScenario() throws Exception {
        CommerceTestFixtures.LearnerAccount buyer = fixtures.createLearner();
        CommerceTestFixtures.StoredTestProduct product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);

        OrderHandle created = createOrderViaApi(buyer, product);
        callIpn(created.firstTxnRef(), product.listPriceVnd(), "24", "02", "0", "NCB", "ATM")
                .andExpect(jsonPath("$.RspCode").value("00"));

        String secondTxnRef = retryPayViaApi(buyer, created.orderCode());
        callIpn(secondTxnRef, product.listPriceVnd(), "00", "00", "900902", "NCB", "ATM")
                .andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(orderState(created.orderId())).isEqualTo("FULFILLED");
        assertThat(grantCount(created.orderId())).isEqualTo(1);
        assertThat(remainingAttempts(buyer.id(), product.examId())).isEqualTo(2);

        // VNPAY báo muộn rằng lần 1 đã thu tiền thành công -> ghi nhận thu thừa đúng bằng số tiền lần 1, học viên vẫn giữ bài.
        callIpn(created.firstTxnRef(), product.listPriceVnd(), "00", "00", "900901", "VCB", "ATM")
                .andExpect(jsonPath("$.RspCode").value("00"));

        Payment p1 = paymentRepository.findByTxnRef(created.firstTxnRef()).orElseThrow();
        assertThat(p1.getState()).isEqualTo(PaymentState.SUCCEEDED);
        assertThat(p1.isDuplicate()).isTrue();
        assertThat(p1.getExcessReason()).isEqualTo(ExcessReason.AFTER_COUNTED);
        assertThat(p1.getLateSuccessReview()).isNull();
        assertThat(refundableExcessSum(created.orderId())).isEqualTo(p1.getAmountVnd());
        assertThat(orderState(created.orderId())).isEqualTo("FULFILLED");
        assertThat(grantCount(created.orderId())).isEqualTo(1);
        assertThat(remainingAttempts(buyer.id(), product.examId())).isEqualTo(2);

        int historyBeforeRepeat = orderHistoryCount(created.orderId());
        int eventsBeforeRepeat = paymentEventCount(p1.getId());

        // VNPAY gửi lại thông báo của lần 1 -> 02, không ghi nhận thêm khoản thu thừa và không tạo thêm lịch sử hoàn.
        callIpn(created.firstTxnRef(), product.listPriceVnd(), "00", "00", "900901", "VCB", "ATM")
                .andExpect(jsonPath("$.RspCode").value("02"));
        assertThat(paymentEventCount(p1.getId())).isEqualTo(eventsBeforeRepeat + 1);
        assertThat(orderHistoryCount(created.orderId())).isEqualTo(historyBeforeRepeat);
        assertThat(refundableExcessSum(created.orderId())).isEqualTo(p1.getAmountVnd());
        assertThat(grantCount(created.orderId())).isEqualTo(1);
        assertThat(remainingAttempts(buyer.id(), product.examId())).isEqualTo(2);
    }

    private OrderHandle createOrderViaApi(
            CommerceTestFixtures.LearnerAccount buyer, CommerceTestFixtures.StoredTestProduct product) throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "code", product.code(),
                "expectedAccountId", buyer.id(),
                "expectedVersionNo", product.versionNo(),
                "expectedAmountDue", product.listPriceVnd()));
        String raw = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "k-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode root = mapper.readTree(raw);
        UUID orderId = UUID.fromString(root.path("order").path("id").asText());
        String orderCode = root.path("order").path("code").asText();
        String firstTxnRef = root.path("order").path("payments").get(0).path("txnRef").asText();
        fixtures.trackOrderId(orderId);
        return new OrderHandle(orderId, orderCode, firstTxnRef);
    }

    private String retryPayViaApi(CommerceTestFixtures.LearnerAccount buyer, String orderCode) throws Exception {
        String raw = mockMvc.perform(post("/api/v1/me/orders/" + orderCode + "/pay")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", "pay-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return mapper.readTree(raw).path("txnRef").asText();
    }

    private org.springframework.test.web.servlet.ResultActions callIpn(
            String txnRef,
            long amountVnd,
            String responseCode,
            String transactionStatus,
            String txnNo,
            String bankCode,
            String cardType) throws Exception {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", Long.toString(amountVnd * 100L));
        params.put("vnp_BankCode", bankCode);
        params.put("vnp_CardType", cardType);
        params.put("vnp_OrderInfo", "Thanh toan don test");
        params.put("vnp_PayDate", "20261121100100");
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TmnCode", MockVnpayGateway.DEFAULT_MOCK_TMN_CODE);
        params.put("vnp_TransactionNo", txnNo);
        params.put("vnp_TransactionStatus", transactionStatus);
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_SecureHash", VnpaySigner.signPayParams(params, MockVnpayGateway.DEFAULT_MOCK_SECRET));
        MockHttpServletRequestBuilder req = get("/api/v1/public/payments/vnpay/ipn");
        params.forEach(req::param);
        return mockMvc.perform(req).andExpect(status().isOk());
    }

    private String callIpnAndReadRspCode(
            String txnRef,
            long amountVnd,
            String responseCode,
            String transactionStatus,
            String txnNo,
            String bankCode,
            String cardType) throws Exception {
        String body = callIpn(txnRef, amountVnd, responseCode, transactionStatus, txnNo, bankCode, cardType)
                .andReturn()
                .getResponse()
                .getContentAsString();
        return mapper.readTree(body).path("RspCode").asText();
    }

    private String orderState(UUID orderId) {
        return jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, orderId);
    }

    private int grantCount(UUID orderId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.entitlement_grant WHERE order_id = ?",
                Integer.class,
                orderId);
        return count == null ? 0 : count;
    }

    private int remainingAttempts(UUID userId, UUID examId) {
        Integer rem = jdbc.queryForObject(
                "SELECT COALESCE(SUM(attempts_total - attempts_used - attempts_reserved - attempts_revoked), 0) "
                        + "FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                Integer.class,
                userId,
                examId);
        return rem == null ? 0 : rem;
    }

    private int paymentEventCount(UUID paymentId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.payment_event WHERE payment_id = ?",
                Integer.class,
                paymentId);
        return count == null ? 0 : count;
    }

    private int orderHistoryCount(UUID orderId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.order_history WHERE order_id = ?",
                Integer.class,
                orderId);
        return count == null ? 0 : count;
    }

    private long refundableExcessSum(UUID orderId) {
        Long sum = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount_vnd), 0) FROM commerce.payment "
                        + "WHERE order_id = ? AND state = 'SUCCEEDED' AND is_duplicate = true",
                Long.class,
                orderId);
        return sum == null ? 0L : sum;
    }

    private record OrderHandle(UUID orderId, String orderCode, String firstTxnRef) {
    }
}
