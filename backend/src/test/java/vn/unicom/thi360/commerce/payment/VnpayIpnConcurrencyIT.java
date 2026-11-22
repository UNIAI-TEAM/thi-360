package vn.unicom.thi360.commerce.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpaySigner;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;

/**
 * AC-20 đồng thời: IPN song song trên cùng {@code vnp_TxnRef} hoặc hai {@code vnp_TxnRef} khác nhau
 * của cùng một đơn — chỉ một khoản được tính ({@code duplicate=false}) và chỉ cấp đúng một lần quyền.
 */
class VnpayIpnConcurrencyIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;

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
    void handlesTenSequentialDuplicateIpnsIdempotently() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var order = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var payment = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.CREATED);

        List<String> rspCodes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            rspCodes.add(callSignedIpn(payment.txnRef(), 200_000L, "91000001"));
        }

        assertThat(rspCodes.getFirst()).isEqualTo("00");
        assertThat(rspCodes.subList(1, 10)).containsOnly("02");
        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.entitlement_grant WHERE order_id = ?",
                Integer.class, order.id())).isEqualTo(1);
    }

    @Test
    void handlesConcurrentDuplicateIpnForSameTxnRefIdempotently() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var order = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var payment = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.CREATED);

        List<Callable<String>> jobs = new ArrayList<>(20);
        for (int i = 0; i < 20; i++) {
            jobs.add(() -> callSignedIpn(payment.txnRef(), 200_000L, "91000011"));
        }
        List<String> rspCodes = runConcurrently(jobs);

        assertThat(Collections.frequency(rspCodes, "00")).isEqualTo(1);
        assertThat(Collections.frequency(rspCodes, "02")).isEqualTo(19);
        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject(
                "SELECT attempts_total FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                Integer.class, buyer.id(), product.examId())).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.entitlement_grant WHERE order_id = ?",
                Integer.class, order.id())).isEqualTo(1);
    }

    @Test
    void countsOnlyOnePaymentAndMarksSecondAsExcessWhenTwoAttemptsSucceedConcurrently() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var order = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var p1 = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.CREATED);
        var p2 = fixtures.insertStubPayment(order, 2, 200_000L, PaymentState.CREATED);

        List<String> rspCodes = runConcurrently(List.of(
                () -> callSignedIpn(p1.txnRef(), 200_000L, "91000021"),
                () -> callSignedIpn(p2.txnRef(), 200_000L, "91000022")));

        assertThat(rspCodes).containsExactlyInAnyOrder("00", "00");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.payment WHERE order_id = ? AND state = 'SUCCEEDED' AND is_duplicate = false",
                Integer.class, order.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.payment WHERE order_id = ? AND state = 'SUCCEEDED' AND is_duplicate = true AND excess_reason = 'AFTER_COUNTED'",
                Integer.class, order.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT attempts_total FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                Integer.class, buyer.id(), product.examId())).isEqualTo(2);
    }

    private List<String> runConcurrently(List<Callable<String>> jobs) throws Exception {
        CountDownLatch ready = new CountDownLatch(jobs.size());
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(jobs.size())) {
            List<Future<String>> futures = new ArrayList<>();
            for (Callable<String> job : jobs) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(15, TimeUnit.SECONDS);
                    return job.call();
                }));
            }
            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<String> results = new ArrayList<>();
            for (Future<String> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        }
    }

    private String callSignedIpn(String txnRef, long amountVnd, String txnNo) throws Exception {
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
        String body = mockMvc.perform(req).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("RspCode").asText();
    }
}
