package vn.unicom.thi360.commerce.order;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpaySigner;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;

class CrossChannelPaymentIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private VnpayGateway vnpayGateway;

    @Value("${thi360.web-url:http://localhost:3000}")
    private String webUrl;

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
    void eachPaymentKeepsItsReturnPath() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);

        JsonNode created = createOrder(fixtures.asMobileLearner(buyer), buyer.id(), product.code(), 1, 200_000L);
        String orderCode = created.path("order").path("code").asText();
        String payUrl1 = created.path("paymentUrl").asText();
        String txnRef1 = created.path("order").path("payments").get(0).path("txnRef").asText();

        JsonNode paidWeb = payOrder(fixtures.asLearner(buyer), orderCode);
        String payUrl2 = paidWeb.path("paymentUrl").asText();
        String txnRef2 = paidWeb.path("txnRef").asText();

        mockMvc.perform(getDecodedUrl(payUrl1)).andExpect(status().isOk());
        mockMvc.perform(getDecodedUrl(payUrl2)).andExpect(status().isOk());

        String location1 = mockMvc.perform(getDecodedUrl(toMockActionUrl(payUrl1, "success")))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader("Location");
        assertThat(location1).startsWith(expectedAppReturnUrl() + "?");
        URI uri1 = URI.create(location1);
        assertThat(uri1.getPath()).isEqualTo("/checkout/result/app");
        Map<String, String> params1 = parseQueryParams(location1);
        assertThat(params1.get("vnp_TxnRef")).isEqualTo(txnRef1);
        assertThat(vnpayGateway.verify(params1)).isTrue();

        String location2 = mockMvc.perform(getDecodedUrl(toMockActionUrl(payUrl2, "success")))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader("Location");
        assertThat(location2).startsWith(expectedWebReturnUrl() + "?");
        URI uri2 = URI.create(location2);
        assertThat(uri2.getPath()).isEqualTo("/checkout/result");
        Map<String, String> params2 = parseQueryParams(location2);
        assertThat(params2.get("vnp_TxnRef")).isEqualTo(txnRef2);
        assertThat(vnpayGateway.verify(params2)).isTrue();
    }

    @Test
    void appReturnParamsVerify() throws Exception {
        var buyerSuccess = fixtures.createLearner();
        var buyerFailed = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);

        JsonNode okOrder = createOrder(fixtures.asMobileLearner(buyerSuccess), buyerSuccess.id(), product.code(), 1, 200_000L);
        String okOrderCode = okOrder.path("order").path("code").asText();
        String okLocation = mockMvc.perform(getDecodedUrl(toMockActionUrl(okOrder.path("paymentUrl").asText(), "success")))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader("Location");
        assertThat(okLocation).startsWith(expectedAppReturnUrl() + "?");

        MockHttpServletRequestBuilder okReturnReq = get("/api/v1/public/payments/vnpay/return");
        parseQueryParams(okLocation).forEach(okReturnReq::param);
        mockMvc.perform(okReturnReq)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderCode").value(okOrderCode))
                .andExpect(jsonPath("$.paymentState").value("SUCCEEDED"));

        JsonNode failedOrder = createOrder(fixtures.asMobileLearner(buyerFailed), buyerFailed.id(), product.code(), 1, 200_000L);
        String failedOrderCode = failedOrder.path("order").path("code").asText();
        String failedLocation = mockMvc.perform(getDecodedUrl(toMockActionUrl(failedOrder.path("paymentUrl").asText(), "failed")))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader("Location");
        assertThat(failedLocation).startsWith(expectedAppReturnUrl() + "?");

        MockHttpServletRequestBuilder failedReturnReq = get("/api/v1/public/payments/vnpay/return");
        parseQueryParams(failedLocation).forEach(failedReturnReq::param);
        mockMvc.perform(failedReturnReq)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderCode").value(failedOrderCode))
                .andExpect(jsonPath("$.paymentState").value("FAILED"));
    }

    @Test
    void firstPaymentCountsAcrossChannels() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);

        JsonNode created = createOrder(fixtures.asMobileLearner(buyer), buyer.id(), product.code(), 1, 200_000L);
        UUID orderId = UUID.fromString(created.path("order").path("id").asText());
        String orderCode = created.path("order").path("code").asText();
        String txnRef1 = created.path("order").path("payments").get(0).path("txnRef").asText();
        assertThat(parseQueryParams(created.path("paymentUrl").asText()).get("vnp_ReturnUrl"))
                .isEqualTo(expectedAppReturnUrl());

        JsonNode paidWeb = payOrder(fixtures.asLearner(buyer), orderCode);
        String txnRef2 = paidWeb.path("txnRef").asText();
        assertThat(parseQueryParams(paidWeb.path("paymentUrl").asText()).get("vnp_ReturnUrl"))
                .isEqualTo(expectedWebReturnUrl());

        assertThat(callSignedIpn(txnRef1, 200_000L, "95010001")).isEqualTo("00");

        String detailRaw = mockMvc.perform(get("/api/v1/me/orders/" + orderCode)
                        .with(fixtures.asMobileLearner(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FULFILLED"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode detail = mapper.readTree(detailRaw);

        Map<String, JsonNode> paymentsByTxn = new LinkedHashMap<>();
        for (JsonNode p : detail.path("payments")) {
            paymentsByTxn.put(p.path("txnRef").asText(), p);
        }
        assertThat(paymentsByTxn.get(txnRef1).path("state").asText()).isEqualTo("SUCCEEDED");
        assertThat(paymentsByTxn.get(txnRef1).path("duplicate").asBoolean()).isFalse();
        assertThat(paymentsByTxn.get(txnRef2).path("state").asText()).isEqualTo("CREATED");
        assertThat(paymentsByTxn.get(txnRef2).path("duplicate").asBoolean()).isFalse();

        assertThat(historyTransitions(detail)).contains("PENDING_PAYMENT->PAID", "PAID->FULFILLED");
        assertThat(jdbc.queryForObject(
                "SELECT attempts_total FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                Integer.class, buyer.id(), product.examId())).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.entitlement_grant WHERE order_id = ?",
                Integer.class, orderId)).isEqualTo(1);
    }

    @Test
    void secondSuccessIsDuplicate() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 4);

        JsonNode created = createOrder(fixtures.asMobileLearner(buyer), buyer.id(), product.code(), 1, 200_000L);
        UUID orderId = UUID.fromString(created.path("order").path("id").asText());
        String orderCode = created.path("order").path("code").asText();
        String txnRef1 = created.path("order").path("payments").get(0).path("txnRef").asText();
        assertThat(parseQueryParams(created.path("paymentUrl").asText()).get("vnp_ReturnUrl"))
                .isEqualTo(expectedAppReturnUrl());

        JsonNode paidWeb = payOrder(fixtures.asLearner(buyer), orderCode);
        String txnRef2 = paidWeb.path("txnRef").asText();
        assertThat(parseQueryParams(paidWeb.path("paymentUrl").asText()).get("vnp_ReturnUrl"))
                .isEqualTo(expectedWebReturnUrl());

        List<String> rspCodes = runConcurrently(List.of(
                () -> callSignedIpn(txnRef1, 200_000L, "95020001"),
                () -> callSignedIpn(txnRef2, 200_000L, "95020002")));

        assertThat(rspCodes).containsExactlyInAnyOrder("00", "00");
        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, orderId))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.payment WHERE order_id = ? AND state = 'SUCCEEDED' AND is_duplicate = false",
                Integer.class, orderId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.payment WHERE order_id = ? AND state = 'SUCCEEDED' AND is_duplicate = true",
                Integer.class, orderId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT attempts_total FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                Integer.class, buyer.id(), product.examId())).isEqualTo(4);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.entitlement_grant WHERE order_id = ?",
                Integer.class, orderId)).isEqualTo(1);

        String detailRaw = mockMvc.perform(get("/api/v1/me/orders/" + orderCode)
                        .with(fixtures.asMobileLearner(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FULFILLED"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode payments = mapper.readTree(detailRaw).path("payments");
        assertThat(payments).hasSize(2);
        long counted = 0;
        long duplicates = 0;
        for (JsonNode p : payments) {
            assertThat(p.path("state").asText()).isEqualTo("SUCCEEDED");
            if (p.path("duplicate").asBoolean()) {
                duplicates++;
            } else {
                counted++;
            }
        }
        assertThat(counted).isEqualTo(1);
        assertThat(duplicates).isEqualTo(1);
    }

    @Test
    void lateSuccessAfterClose() throws Exception {
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);

        var buyerExpired = fixtures.createLearner();
        JsonNode expCreated = createOrder(
                fixtures.asMobileLearner(buyerExpired), buyerExpired.id(), product.code(), 1, 200_000L);
        UUID expOrderId = UUID.fromString(expCreated.path("order").path("id").asText());
        String expOrderCode = expCreated.path("order").path("code").asText();
        String expTxnRef = expCreated.path("order").path("payments").get(0).path("txnRef").asText();
        assertThat(parseQueryParams(expCreated.path("paymentUrl").asText()).get("vnp_ReturnUrl"))
                .isEqualTo(expectedAppReturnUrl());
        jdbc.update(
                "UPDATE commerce.purchase_order SET state = 'EXPIRED', expires_at = now() - interval '30 minutes' WHERE id = ?",
                expOrderId);

        assertThat(callSignedIpn(expTxnRef, 200_000L, "95030001")).isEqualTo("00");
        assertLateFulfilled(buyerExpired, product.examId(), expOrderId, expOrderCode, "EXPIRED->PAID", 2);

        var buyerCancelled = fixtures.createLearner();
        JsonNode canCreated = createOrder(
                fixtures.asMobileLearner(buyerCancelled), buyerCancelled.id(), product.code(), 1, 200_000L);
        UUID canOrderId = UUID.fromString(canCreated.path("order").path("id").asText());
        String canOrderCode = canCreated.path("order").path("code").asText();
        String canTxnRef = canCreated.path("order").path("payments").get(0).path("txnRef").asText();
        assertThat(parseQueryParams(canCreated.path("paymentUrl").asText()).get("vnp_ReturnUrl"))
                .isEqualTo(expectedAppReturnUrl());
        mockMvc.perform(post("/api/v1/me/orders/" + canOrderCode + "/cancel")
                        .with(fixtures.asMobileLearner(buyerCancelled)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CANCELLED"));

        assertThat(callSignedIpn(canTxnRef, 200_000L, "95030002")).isEqualTo("00");
        assertLateFulfilled(buyerCancelled, product.examId(), canOrderId, canOrderCode, "CANCELLED->PAID", 2);

        var buyerReplaced = fixtures.createLearner();
        JsonNode repCreated = createOrder(
                fixtures.asMobileLearner(buyerReplaced), buyerReplaced.id(), product.code(), 1, 200_000L);
        UUID repOrderId = UUID.fromString(repCreated.path("order").path("id").asText());
        String repOrderCode = repCreated.path("order").path("code").asText();
        String repTxnRef = repCreated.path("order").path("payments").get(0).path("txnRef").asText();
        assertThat(parseQueryParams(repCreated.path("paymentUrl").asText()).get("vnp_ReturnUrl"))
                .isEqualTo(expectedAppReturnUrl());

        fixtures.updateStorefrontPriceAndVersion(product.examId(), 250_000L, 2, "ON_SALE");
        createOrder(fixtures.asLearner(buyerReplaced), buyerReplaced.id(), product.code(), 2, 250_000L);
        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, repOrderId))
                .isEqualTo("REPLACED");

        assertThat(callSignedIpn(repTxnRef, 200_000L, "95030003")).isEqualTo("00");
        assertLateFulfilled(buyerReplaced, product.examId(), repOrderId, repOrderCode, "REPLACED->PAID", 2);
    }

    private void assertLateFulfilled(
            CommerceTestFixtures.LearnerAccount buyer,
            UUID examId,
            UUID orderId,
            String orderCode,
            String expectedReclaimTransition,
            int expectedAttempts)
            throws Exception {
        String detailRaw = mockMvc.perform(get("/api/v1/me/orders/" + orderCode)
                        .with(fixtures.asMobileLearner(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FULFILLED"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode detail = mapper.readTree(detailRaw);
        assertThat(historyTransitions(detail)).contains(expectedReclaimTransition, "PAID->FULFILLED");
        assertThat(jdbc.queryForObject(
                "SELECT attempts_total FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                Integer.class, buyer.id(), examId)).isEqualTo(expectedAttempts);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.entitlement_grant WHERE order_id = ?",
                Integer.class, orderId)).isEqualTo(1);
    }

    private JsonNode createOrder(
            RequestPostProcessor auth, UUID buyerId, String productCode, int expectedVersionNo, long expectedAmountDue)
            throws Exception {
        String raw = mockMvc.perform(post("/api/v1/me/orders")
                        .with(auth)
                        .header("Idempotency-Key", "cc-ord-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", productCode,
                                "expectedAccountId", buyerId,
                                "expectedVersionNo", expectedVersionNo,
                                "expectedAmountDue", expectedAmountDue))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(raw);
    }

    private JsonNode payOrder(RequestPostProcessor auth, String orderCode) throws Exception {
        String raw = mockMvc.perform(post("/api/v1/me/orders/" + orderCode + "/pay")
                        .with(auth)
                        .header("Idempotency-Key", "cc-pay-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(raw);
    }

    private String callSignedIpn(String txnRef, long amountVnd, String txnNo) throws Exception {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", Long.toString(amountVnd * 100L));
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", "Thanh toan don test");
        params.put("vnp_PayDate", "20261127010000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TmnCode", MockVnpayGateway.DEFAULT_MOCK_TMN_CODE);
        params.put("vnp_TransactionNo", txnNo);
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_SecureHash", VnpaySigner.signPayParams(params, MockVnpayGateway.DEFAULT_MOCK_SECRET));

        MockHttpServletRequestBuilder req = get("/api/v1/public/payments/vnpay/ipn");
        params.forEach(req::param);
        String body = mockMvc.perform(req)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(body).path("RspCode").asText();
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

    private static List<String> historyTransitions(JsonNode detail) {
        List<String> transitions = new ArrayList<>();
        for (JsonNode h : detail.path("history")) {
            transitions.add(h.path("fromState").asText() + "->" + h.path("toState").asText());
        }
        return transitions;
    }

    private String expectedWebReturnUrl() {
        return normalizedWebUrl() + "/checkout/result";
    }

    private String expectedAppReturnUrl() {
        return normalizedWebUrl() + "/checkout/result/app";
    }

    private String normalizedWebUrl() {
        String trimmed = webUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private static String toMockActionUrl(String payUrl, String outcome) {
        return payUrl.replace("/mock-vnpay?", "/mock-vnpay/action?outcome=" + outcome + "&");
    }

    private static MockHttpServletRequestBuilder getDecodedUrl(String fullUrl) {
        URI uri = URI.create(fullUrl);
        MockHttpServletRequestBuilder builder = get(uri.getRawPath());
        String query = uri.getRawQuery();
        if (query != null && !query.isBlank()) {
            for (String pair : query.split("&")) {
                int idx = pair.indexOf('=');
                if (idx <= 0) {
                    continue;
                }
                String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                builder.param(k, v);
            }
        }
        return builder;
    }

    private static Map<String, String> parseQueryParams(String url) {
        String query = URI.create(url).getRawQuery();
        Map<String, String> map = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
            String v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
            map.put(k, v);
        }
        return map;
    }
}
