package vn.unicom.thi360.commerce.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTParser;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudienceType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures.StubOrder;
import vn.unicom.thi360.identity.domain.UserAccount;
import vn.unicom.thi360.identity.domain.UserSource;
import vn.unicom.thi360.identity.domain.UserStatus;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.CreateUserCommand;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;
import vn.unicom.thi360.shared.security.SessionAuthzResolver;
import vn.unicom.thi360.shared.security.SessionStore;

class OrderReturnUrlChannelIT extends AbstractKeycloakIntegrationTest {

    private static final String TEST_PASSWORD = "Valid@1234";
    private static final String TEST_IP = "203.0.113.191";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private VnpayGateway vnpayGateway;
    @Autowired private KeycloakIdentityClient keycloakClient;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private SessionStore sessionStore;
    @Autowired private StringRedisTemplate redisTemplate;

    @Value("${thi360.web-url:http://localhost:3000}")
    private String webUrl;

    private CommerceTestFixtures fixtures;
    private final List<UUID> provisionedUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        fixtures = new CommerceTestFixtures(jdbc, mapper, catalogLookup, promotionRepository, testSecuritySupport);
        redisTemplate.delete(RateLimitFilter.KEY_PREFIX + TEST_IP);
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete(RateLimitFilter.KEY_PREFIX + TEST_IP);
        for (UUID userId : provisionedUserIds) {
            sessionStore.deleteAllSessions(userId);
            redisTemplate.delete(List.of(
                    "user-sess-seq:" + userId,
                    "user-rev-gen:" + userId,
                    SessionAuthzResolver.AUTHZ_KEY_PREFIX + userId));
            CommerceTestFixtures.deleteStubOrdersForUser(jdbc, userId);
            jdbc.update("DELETE FROM shared.email_message WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM identity.user_account WHERE id = ?", userId);
        }
        provisionedUserIds.clear();
        fixtures.cleanup();
    }

    @Test
    void mobileCreateUsesAppPath() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);

        JsonNode created = createOrderJson(fixtures.asMobileLearner(buyer), buyer.id(), product.code(), 1, 200_000L);
        assertThat(created.path("next").asText()).isEqualTo("REDIRECT");
        String paymentUrl = created.path("paymentUrl").asText();
        Map<String, String> params = parseQueryParams(paymentUrl);
        assertThat(params.get("vnp_ReturnUrl")).isEqualTo(expectedAppReturnUrl());
        assertThat(vnpayGateway.verify(params)).isTrue();
    }

    @Test
    void webCreateKeepsWebPath() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);

        JsonNode created = createOrderJson(fixtures.asLearner(buyer), buyer.id(), product.code(), 1, 200_000L);
        assertThat(created.path("next").asText()).isEqualTo("REDIRECT");
        String paymentUrl = created.path("paymentUrl").asText();
        Map<String, String> params = parseQueryParams(paymentUrl);
        assertThat(params.get("vnp_ReturnUrl")).isEqualTo(expectedWebReturnUrl());
        assertThat(vnpayGateway.verify(params)).isTrue();
    }

    @Test
    void reusedPendingFollowsCallerChannel() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);

        JsonNode webCreated = createOrderJson(fixtures.asLearner(buyer), buyer.id(), product.code(), 1, 200_000L);
        String orderCode = webCreated.path("order").path("code").asText();
        String orderId = webCreated.path("order").path("id").asText();
        assertThat(parseQueryParams(webCreated.path("paymentUrl").asText()).get("vnp_ReturnUrl"))
                .isEqualTo(expectedWebReturnUrl());

        JsonNode mobileReused = createOrderJson(fixtures.asMobileLearner(buyer), buyer.id(), product.code(), 1, 200_000L);
        assertThat(mobileReused.path("order").path("id").asText()).isEqualTo(orderId);
        assertThat(mobileReused.path("order").path("code").asText()).isEqualTo(orderCode);
        assertThat(mobileReused.path("order").path("payments")).hasSize(2);
        Map<String, String> reusedParams = parseQueryParams(mobileReused.path("paymentUrl").asText());
        assertThat(reusedParams.get("vnp_ReturnUrl")).isEqualTo(expectedAppReturnUrl());
        assertThat(reusedParams.get("vnp_TxnRef")).isEqualTo(orderCode + "02");
        assertThat(vnpayGateway.verify(reusedParams)).isTrue();

        JsonNode webReusedAgain = createOrderJson(fixtures.asLearner(buyer), buyer.id(), product.code(), 1, 200_000L);
        assertThat(webReusedAgain.path("order").path("id").asText()).isEqualTo(orderId);
        Map<String, String> webReusedParams = parseQueryParams(webReusedAgain.path("paymentUrl").asText());
        assertThat(webReusedParams.get("vnp_ReturnUrl")).isEqualTo(expectedWebReturnUrl());
        assertThat(webReusedParams.get("vnp_TxnRef")).isEqualTo(orderCode + "03");
        assertThat(vnpayGateway.verify(webReusedParams)).isTrue();
    }

    @Test
    void priceChangeReplaces() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);

        JsonNode webCreated = createOrderJson(fixtures.asLearner(buyer), buyer.id(), product.code(), 1, 200_000L);
        UUID firstOrderId = UUID.fromString(webCreated.path("order").path("id").asText());
        String firstOrderCode = webCreated.path("order").path("code").asText();

        fixtures.updateStorefrontPriceAndVersion(product.examId(), 250_000L, 2, "ON_SALE");

        JsonNode mobileReplaced = createOrderJson(fixtures.asMobileLearner(buyer), buyer.id(), product.code(), 2, 250_000L);
        UUID secondOrderId = UUID.fromString(mobileReplaced.path("order").path("id").asText());
        String secondOrderCode = mobileReplaced.path("order").path("code").asText();

        assertThat(secondOrderId).isNotEqualTo(firstOrderId);
        assertThat(secondOrderCode).isNotEqualTo(firstOrderCode);
        assertThat(parseQueryParams(mobileReplaced.path("paymentUrl").asText()).get("vnp_ReturnUrl"))
                .isEqualTo(expectedAppReturnUrl());
        assertThat(jdbc.queryForObject(
                "SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, firstOrderId))
                .isEqualTo("REPLACED");
        assertThat(jdbc.queryForObject(
                "SELECT replaced_by_order_id FROM commerce.purchase_order WHERE id = ?", UUID.class, firstOrderId))
                .isEqualTo(secondOrderId);
    }

    @Test
    void payFollowsCallerChannelNotOrderChannel() throws Exception {
        var buyerA = fixtures.createLearner();
        var buyerB = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);

        JsonNode webOrder = createOrderJson(fixtures.asLearner(buyerA), buyerA.id(), product.code(), 1, 200_000L);
        String webOrderCode = webOrder.path("order").path("code").asText();
        assertThat(parseQueryParams(webOrder.path("paymentUrl").asText()).get("vnp_ReturnUrl"))
                .isEqualTo(expectedWebReturnUrl());

        JsonNode paidOnMobile = payOrderJson(fixtures.asMobileLearner(buyerA), webOrderCode, "pay-m-" + UUID.randomUUID());
        Map<String, String> paidMobileParams = parseQueryParams(paidOnMobile.path("paymentUrl").asText());
        assertThat(paidMobileParams.get("vnp_ReturnUrl")).isEqualTo(expectedAppReturnUrl());
        assertThat(paidOnMobile.path("txnRef").asText()).isEqualTo(webOrderCode + "02");
        assertThat(vnpayGateway.verify(paidMobileParams)).isTrue();

        JsonNode mobileOrder = createOrderJson(fixtures.asMobileLearner(buyerB), buyerB.id(), product.code(), 1, 200_000L);
        String mobileOrderCode = mobileOrder.path("order").path("code").asText();
        assertThat(parseQueryParams(mobileOrder.path("paymentUrl").asText()).get("vnp_ReturnUrl"))
                .isEqualTo(expectedAppReturnUrl());

        JsonNode paidOnWeb = payOrderJson(fixtures.asLearner(buyerB), mobileOrderCode, "pay-w-" + UUID.randomUUID());
        Map<String, String> paidWebParams = parseQueryParams(paidOnWeb.path("paymentUrl").asText());
        assertThat(paidWebParams.get("vnp_ReturnUrl")).isEqualTo(expectedWebReturnUrl());
        assertThat(paidOnWeb.path("txnRef").asText()).isEqualTo(mobileOrderCode + "02");
        assertThat(vnpayGateway.verify(paidWebParams)).isTrue();
    }

    @Test
    void idempotentReplayKeepsFirstUrl() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);

        String createKey = "idem-create-" + UUID.randomUUID();
        String createBody = mapper.writeValueAsString(Map.of(
                "code", product.code(),
                "expectedAccountId", buyer.id(),
                "expectedVersionNo", 1,
                "expectedAmountDue", 200_000L));

        String mobileCreateRaw = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asMobileLearner(buyer))
                        .header("Idempotency-Key", createKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode firstCreate = mapper.readTree(mobileCreateRaw);
        UUID orderId = UUID.fromString(firstCreate.path("order").path("id").asText());
        String orderCode = firstCreate.path("order").path("code").asText();
        String firstCreateUrl = firstCreate.path("paymentUrl").asText();
        String firstCreateTxnRef = firstCreate.path("order").path("payments").get(0).path("txnRef").asText();
        assertThat(parseQueryParams(firstCreateUrl).get("vnp_ReturnUrl")).isEqualTo(expectedAppReturnUrl());

        String webReplayCreateRaw = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asLearner(buyer))
                        .header("Idempotency-Key", createKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode replayCreate = mapper.readTree(webReplayCreateRaw);
        assertThat(replayCreate.path("order").path("id").asText()).isEqualTo(orderId.toString());
        assertThat(replayCreate.path("paymentUrl").asText()).isEqualTo(firstCreateUrl);
        assertThat(replayCreate.path("order").path("payments").get(0).path("txnRef").asText())
                .isEqualTo(firstCreateTxnRef);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.payment WHERE order_id = ?", Integer.class, orderId))
                .isEqualTo(1);

        String payKey = "idem-pay-" + UUID.randomUUID();
        JsonNode webPay = payOrderJson(fixtures.asLearner(buyer), orderCode, payKey);
        String webPayUrl = webPay.path("paymentUrl").asText();
        String webPayTxnRef = webPay.path("txnRef").asText();
        assertThat(parseQueryParams(webPayUrl).get("vnp_ReturnUrl")).isEqualTo(expectedWebReturnUrl());

        JsonNode mobileReplayPay = payOrderJson(fixtures.asMobileLearner(buyer), orderCode, payKey);
        assertThat(mobileReplayPay.path("paymentUrl").asText()).isEqualTo(webPayUrl);
        assertThat(mobileReplayPay.path("txnRef").asText()).isEqualTo(webPayTxnRef);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.payment WHERE order_id = ?", Integer.class, orderId))
                .isEqualTo(2);
    }

    @Test
    void realMobileLoginSession() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "ord.mob." + suffix;
        UserAccount account = provisionKeycloakLearner(username, username + "@thi360.vn");
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);

        String mobileAccessToken = mobileLoginAccessToken(username);

        String createRaw = mockMvc.perform(post("/api/v1/me/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mobileAccessToken)
                        .header("Idempotency-Key", "real-mob-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "expectedAccountId", account.getId(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 200_000L))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode created = mapper.readTree(createRaw);
        String orderCode = created.path("order").path("code").asText();
        assertThat(parseQueryParams(created.path("paymentUrl").asText()).get("vnp_ReturnUrl"))
                .isEqualTo(expectedAppReturnUrl());

        String mobilePayRaw = mockMvc.perform(post("/api/v1/me/orders/" + orderCode + "/pay")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + mobileAccessToken)
                        .header("Idempotency-Key", "real-mob-pay-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(parseQueryParams(mapper.readTree(mobilePayRaw).path("paymentUrl").asText()).get("vnp_ReturnUrl"))
                .isEqualTo(expectedAppReturnUrl());

        MvcResult webLoginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, TEST_IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("identifier", username, "password", TEST_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie webAccess = webLoginRes.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN_COOKIE);
        Cookie webXsrf = webLoginRes.getResponse().getCookie(AuthCookieService.XSRF_COOKIE);
        assertThat(webAccess).isNotNull();
        assertThat(webXsrf).isNotNull();

        String webPayRaw = mockMvc.perform(post("/api/v1/me/orders/" + orderCode + "/pay")
                        .cookie(webAccess, webXsrf)
                        .header(AuthCookieService.XSRF_HEADER, webXsrf.getValue())
                        .header("Idempotency-Key", "real-web-pay-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(parseQueryParams(mapper.readTree(webPayRaw).path("paymentUrl").asText()).get("vnp_ReturnUrl"))
                .isEqualTo(expectedWebReturnUrl());
    }

    @Test
    void legacySessionWithoutChannelIsWeb() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "ord.leg." + suffix;
        UserAccount account = provisionKeycloakLearner(username, username + "@thi360.vn");
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);

        String accessToken = mobileLoginAccessToken(username);
        String sid = JWTParser.parse(accessToken).getJWTClaimsSet().getStringClaim("sid");
        assertThat(sid).isNotBlank();

        redisTemplate.opsForHash().delete("sess:" + sid, "channel");
        assertThat(redisTemplate.opsForHash().hasKey("sess:" + sid, "channel")).isFalse();

        String createRaw = mockMvc.perform(post("/api/v1/me/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header("Idempotency-Key", "legacy-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "expectedAccountId", account.getId(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 200_000L))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(parseQueryParams(mapper.readTree(createRaw).path("paymentUrl").asText()).get("vnp_ReturnUrl"))
                .isEqualTo(expectedWebReturnUrl());
    }

    @Test
    void freeOrderHasNoPaymentUrl() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 100_000L, 4);
        var fullVoucher = fixtures.createVoucher(
                "MFREE", DiscountType.PERCENT, 100L, null,
                5, 1, false, PromotionScope.ALL, PromotionAudienceType.ALL);

        String raw = mockMvc.perform(post("/api/v1/me/orders")
                        .with(fixtures.asMobileLearner(buyer))
                        .header("Idempotency-Key", "mfree-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "code", product.code(),
                                "voucherCode", fullVoucher.getCode(),
                                "expectedAccountId", buyer.id(),
                                "expectedVersionNo", 1,
                                "expectedAmountDue", 0L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.next").value("FULFILLED"))
                .andExpect(jsonPath("$.paymentUrl").doesNotExist())
                .andExpect(jsonPath("$.order.paymentMethod").value("FREE"))
                .andExpect(jsonPath("$.order.state").value("FULFILLED"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        UUID orderId = UUID.fromString(mapper.readTree(raw).path("order").path("id").asText());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.payment WHERE order_id = ?", Integer.class, orderId))
                .isZero();
    }

    @Test
    void otherUsersOrderStill404() throws Exception {
        var alice = fixtures.createLearner();
        var bob = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);

        JsonNode aliceOrder = createOrderJson(fixtures.asLearner(alice), alice.id(), product.code(), 1, 200_000L);
        UUID aliceOrderId = UUID.fromString(aliceOrder.path("order").path("id").asText());
        String aliceOrderCode = aliceOrder.path("order").path("code").asText();

        mockMvc.perform(post("/api/v1/me/orders/" + aliceOrderCode + "/pay")
                        .with(fixtures.asMobileLearner(bob))
                        .header("Idempotency-Key", "authz-" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM commerce.payment WHERE order_id = ?", Integer.class, aliceOrderId))
                .isEqualTo(1);
    }

    @Test
    void paymentAttemptLimit99Then100OnMobile() throws Exception {
        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);

        JsonNode created = createOrderJson(fixtures.asMobileLearner(buyer), buyer.id(), product.code(), 1, 200_000L);
        UUID orderId = UUID.fromString(created.path("order").path("id").asText());
        String orderCode = created.path("order").path("code").asText();

        fixtures.insertStubPayment(new StubOrder(orderId, orderCode), 98, 200_000L, PaymentState.CREATED);

        JsonNode attempt99 = payOrderJson(fixtures.asMobileLearner(buyer), orderCode, "att99-" + UUID.randomUUID());
        assertThat(attempt99.path("txnRef").asText()).isEqualTo(orderCode + "99");
        assertThat(parseQueryParams(attempt99.path("paymentUrl").asText()).get("vnp_ReturnUrl"))
                .isEqualTo(expectedAppReturnUrl());

        mockMvc.perform(post("/api/v1/me/orders/" + orderCode + "/pay")
                        .with(fixtures.asMobileLearner(buyer))
                        .header("Idempotency-Key", "att100-" + UUID.randomUUID()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PAYMENT_ATTEMPTS_EXCEEDED"));
    }

    private JsonNode createOrderJson(
            org.springframework.test.web.servlet.request.RequestPostProcessor auth,
            UUID buyerId,
            String productCode,
            int expectedVersionNo,
            long expectedAmountDue)
            throws Exception {
        String raw = mockMvc.perform(post("/api/v1/me/orders")
                        .with(auth)
                        .header("Idempotency-Key", "ord-" + UUID.randomUUID())
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

    private JsonNode payOrderJson(
            org.springframework.test.web.servlet.request.RequestPostProcessor auth,
            String orderCode,
            String idempotencyKey)
            throws Exception {
        String raw = mockMvc.perform(post("/api/v1/me/orders/" + orderCode + "/pay")
                        .with(auth)
                        .header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(raw);
    }

    private String mobileLoginAccessToken(String username) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/auth/mobile/login")
                        .header(ClientIpResolver.X_FORWARDED_FOR, TEST_IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("identifier", username, "password", TEST_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("accessToken").asText();
    }

    private UserAccount provisionKeycloakLearner(String username, String email) {
        UUID userId = keycloakClient.createUser(new CreateUserCommand(
                username, email, "Hoc Vien", "Mobile", TEST_PASSWORD, true, false, true));
        Long seq = jdbc.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        Instant now = Instant.now();
        UserAccount account = new UserAccount(
                userId,
                String.format("U%06d", seq),
                username,
                "Hoc Vien Mobile",
                email,
                null,
                UserSource.ADMIN_CREATED,
                UserStatus.ACTIVE,
                false,
                now,
                userId);
        account.markEmailVerified(now, userId);
        UserAccount saved = userAccountRepository.save(account);
        provisionedUserIds.add(userId);
        return saved;
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
