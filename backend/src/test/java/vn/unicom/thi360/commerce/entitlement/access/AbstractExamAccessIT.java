package vn.unicom.thi360.commerce.entitlement.access;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import vn.unicom.thi360.commerce.spi.AttemptStatusProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.entitlement.application.GrantLine;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderCommand;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.commerce.storefront.api.ExamKindRef;
import vn.unicom.thi360.commerce.storefront.api.IntRange;
import vn.unicom.thi360.commerce.storefront.api.LocalizedName;
import vn.unicom.thi360.commerce.storefront.api.ProductCard;
import vn.unicom.thi360.shared.security.TestSecurity.SimulatedPrincipal;

/**
 * Shared set-up of the exam-access and my-exams tests (plan CM-4b "Dữ liệu test"): random accounts, exams, product codes
 * and orders per test; attempts granted through {@link EntitlementGrantService}; storefront rows built by hand; the exam
 * module and the pending-order lookup replaced by fakes. The attempt module is absent from the context unless a test asks
 * for {@link #attempts()}, so the default of {@code commerce/spi} is what the other tests run against.
 */
@Import(AbstractExamAccessIT.FakesConfig.class)
abstract class AbstractExamAccessIT extends AbstractIntegrationTest {

    @TestConfiguration
    static class FakesConfig {
        @Bean
        @Primary
        CountingExamPort fakePublishedExamPort() {
            return new CountingExamPort();
        }

        @Bean
        @Primary
        FakePendingOrderLookup fakePendingOrderLookup() {
            return new FakePendingOrderLookup();
        }

        @Bean
        @Primary
        SwitchableAttemptStatusProvider switchableAttemptStatusProvider(
                @org.springframework.beans.factory.annotation.Autowired(required = false)
                        @org.springframework.beans.factory.annotation.Qualifier(
                                "attemptStatusProviderImpl")
                        AttemptStatusProvider realProvider) {
            return new SwitchableAttemptStatusProvider(realProvider);
        }

        static class SwitchableAttemptStatusProvider implements AttemptStatusProvider {
            private final AttemptStatusProvider fallback;
            private volatile FakeAttemptStatusProvider activeFake;

            SwitchableAttemptStatusProvider(AttemptStatusProvider fallback) {
                this.fallback = fallback != null ? fallback : AttemptStatusProvider.EMPTY;
            }

            void useFake(FakeAttemptStatusProvider fake) {
                this.activeFake = fake;
            }

            void clearFake() {
                this.activeFake = null;
            }

            private AttemptStatusProvider delegate() {
                FakeAttemptStatusProvider fake = this.activeFake;
                return fake != null ? fake : this.fallback;
            }

            @Override
            public java.util.Optional<OpenAttempt> findOpenAttempt(UUID userId, UUID entitlementId) {
                return delegate().findOpenAttempt(userId, entitlementId);
            }

            @Override
            public java.util.Optional<ResultAttempt> findLatestResultAttempt(
                    UUID userId, UUID entitlementId) {
                return delegate().findLatestResultAttempt(userId, entitlementId);
            }
        }
    }

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected EntitlementGrantService grantService;
    @Autowired protected EntitlementPort entitlementPort;
    @Autowired protected CountingExamPort examPort;
    @Autowired protected FakePendingOrderLookup pendingOrders;

    private final List<UUID> accountIds = new ArrayList<>();
    private final List<UUID> examIds = new ArrayList<>();
    private FakeAttemptStatusProvider attemptsFake;
    @org.springframework.beans.factory.annotation.Autowired
    private FakesConfig.SwitchableAttemptStatusProvider attemptsDelegate;

    @AfterEach
    void removeAccessData() {
        pendingOrders.clear();
        if (attemptsFake != null) {
            attemptsDelegate.clearFake();
            attemptsFake = null;
        }
        for (UUID userId : accountIds) {
            jdbc.update("DELETE FROM commerce.entitlement_usage WHERE grant_id IN (SELECT g.id"
                    + " FROM commerce.entitlement_grant g JOIN commerce.entitlement e ON e.id = g.entitlement_id"
                    + " WHERE e.user_id = ?)", userId);
            jdbc.update("DELETE FROM commerce.entitlement_grant WHERE entitlement_id IN (SELECT id"
                    + " FROM commerce.entitlement WHERE user_id = ?)", userId);
            jdbc.update("DELETE FROM commerce.entitlement WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM identity.user_account WHERE id = ?", userId);
        }
        accountIds.clear();
        for (UUID examId : examIds) {
            examPort.withdraw(examId);
            jdbc.update("DELETE FROM commerce.storefront_product WHERE exam_id = ?", examId);
        }
        examIds.clear();
    }

    // -------------------------------------------------------------- accounts

    protected UUID activeAccount() {
        return account(2);
    }

    /** An account row with this {@code UserStatus} code (2 = ACTIVE, 3 = INACTIVE). */
    protected UUID account(int statusCode) {
        UUID userId = UUID.randomUUID();
        Long seq = jdbc.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        String username = "acc." + userId.toString().substring(0, 8);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO identity.user_account
                    (id, code, username, username_normalized, full_name, email, email_normalized,
                     source, status, must_change_password, created_at, created_by, updated_at, updated_by, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, false, ?, ?, ?, ?, 0)
                """, userId, String.format("U%06d", seq), username, username, "Access Test",
                userId + "@test.local", userId + "@test.local", statusCode, now, userId, now, userId);
        accountIds.add(userId);
        return userId;
    }

    /** The session of a learner ({@code access = EXAM}) with this account id. */
    protected RequestPostProcessor learner(UUID userId) {
        return testSecuritySupport.apply(new SimulatedPrincipal(userId, "learner." + userId, "STUDENT", false, List.of()));
    }

    /** The session of an account with {@code access = MANAGE} (no role: the test support's direct-permission path). */
    protected RequestPostProcessor manager(UUID userId) {
        return asUser(userId, "manager." + userId);
    }

    // ------------------------------------------------------------- products

    /** A product code and the exam it sells, for one test. */
    protected record Product(UUID examId, String code) {
    }

    protected Product onSale() {
        return product("ON_SALE");
    }

    protected Product discontinued() {
        return product("DISCONTINUED");
    }

    protected Product product(String saleState) {
        UUID examId = UUID.randomUUID();
        String code = "AC" + examId.toString().replace("-", "").substring(0, 10).toUpperCase();
        return insertProduct(examId, code, saleState, card(code, "Bai thi " + code));
    }

    protected ProductCard card(String code, String name) {
        return new ProductCard(code, name, null, null, new ExamKindRef("HSA", "Kỳ thi HSA", "HSA exam"), "FULL", null,
                "STANDARD", new IntRange(50, 50), new IntRange(75, 75), 3, 199_000L, 199_000L, null, 1);
    }

    /** Writes a storefront row of an exam product (the projection is not run: the test builds its rows). */
    protected Product insertProduct(UUID examId, String code, String saleState, ProductCard card) {
        try {
            jdbc.update("""
                    INSERT INTO commerce.storefront_product
                        (id, exam_id, code, code_normalized, version_no, sale_state, name, search_text, kind_code, scope,
                         question_count, question_count_max, duration_minutes, duration_minutes_max, build_mode,
                         list_price_vnd, attempts_granted, card, detail, projection_hash, published_at, updated_at)
                    VALUES (?, ?, ?, ?, 1, ?, ?, ?, 'HSA', 'FULL', 50, 50, 75, 75, ?, 199000, 3, ?::jsonb, '{}'::jsonb,
                            'test', now(), now())
                    """, UUID.randomUUID(), examId, code, code.toLowerCase(), saleState, card.name(),
                    card.name().toLowerCase(), card.buildMode(), mapper.writeValueAsString(card));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        examIds.add(examId);
        return new Product(examId, code);
    }

    /** A bundle row (CM-6): its code is not an exam product and must read as {@code NONE}. */
    protected String insertBundle() {
        String code = "BD" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        jdbc.update("""
                INSERT INTO commerce.storefront_product
                    (id, exam_id, code, code_normalized, version_no, sale_state, name, search_text, kind_code, scope,
                     product_type, bundle_id, build_mode, list_price_vnd, attempts_granted, card, detail, projection_hash,
                     published_at, updated_at)
                VALUES (?, NULL, ?, ?, 1, 'ON_SALE', 'Bo de', 'bo de', 'HSA', 'FULL', 'BUNDLE', ?, 'STANDARD', 1000, 1,
                        '{}'::jsonb, '{}'::jsonb, 'test', now(), now())
                """, UUID.randomUUID(), code, code.toLowerCase(), UUID.randomUUID());
        bundleCodes.add(code);
        return code;
    }

    private final List<String> bundleCodes = new ArrayList<>();

    @AfterEach
    void removeBundles() {
        bundleCodes.forEach(code -> jdbc.update("DELETE FROM commerce.storefront_product WHERE code = ?", code));
        bundleCodes.clear();
    }

    // ---------------------------------------------------------- entitlements

    protected GrantOrderResult.Line grant(UUID userId, UUID examId, int attempts) {
        return grant(userId, examId, attempts, List.of(), 1);
    }

    protected GrantOrderResult.Line grant(UUID userId, UUID examId, int attempts, List<String> optionCodes,
            int examVersionNo) {
        UUID orderId = UUID.randomUUID();
        GrantLine line = new GrantLine(examId, examVersionNo, attempts, optionCodes, Map.of("ai", true), "FULL");
        return grantService.grantForOrder(new GrantOrderCommand(
                orderId, "ORD-" + orderId.toString().substring(0, 8), userId, List.of(line))).lines().get(0);
    }

    /** Holds one attempt: it stays reserved, as while an attempt is open. Returns the attempt id. */
    protected UUID reserveAttempt(UUID userId, UUID entitlementId) {
        UUID attemptId = UUID.randomUUID();
        entitlementPort.reserve(userId, entitlementId, attemptId);
        return attemptId;
    }

    /** Uses one attempt up: reserved, then confirmed. Returns the attempt id. */
    protected UUID useAttempt(UUID userId, UUID entitlementId) {
        UUID attemptId = reserveAttempt(userId, entitlementId);
        entitlementPort.confirmUse(attemptId);
        return attemptId;
    }

    /** The attempt module fake override, active from now until the end of the test. */
    protected FakeAttemptStatusProvider attempts() {
        if (attemptsFake == null) {
            attemptsFake = new FakeAttemptStatusProvider();
            attemptsDelegate.useFake(attemptsFake);
        }
        return attemptsFake;
    }

    // ------------------------------------------------------------------ http

    protected JsonNode queryAccess(RequestPostProcessor who, String... codes) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        ArrayNode array = body.putArray("codes");
        for (String code : codes) {
            array.add(code);
        }
        return mapper.readTree(mockMvc.perform(post("/api/v1/me/exam-access/query").with(who)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    protected JsonNode myExams(RequestPostProcessor who) throws Exception {
        return mapper.readTree(mockMvc.perform(get("/api/v1/me/exams").with(who))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    protected static JsonNode only(JsonNode response) {
        JsonNode items = response.get("items");
        if (items.size() != 1) {
            throw new AssertionError("expected one item, got " + items);
        }
        return items.get(0);
    }

    /** The localized name type of the storefront API, for cards that show a part or subject label. */
    protected static LocalizedName name(String vi, String en) {
        return new LocalizedName(vi, en);
    }
}
