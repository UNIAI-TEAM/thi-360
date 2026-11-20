package vn.unicom.thi360.commerce.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.commerce.pricing.PricingTestClockConfig.MutableClock;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;
import vn.unicom.thi360.commerce.promotion.domain.Promotion;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudienceType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.domain.PromotionStatus;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.security.TestSecurity.SimulatedPrincipal;

/**
 * Integration tests for per-user voucher quote rate limiting (TM 7.2, AC-37, AC-38).
 */
@Import(PricingTestClockConfig.class)
class PriceQuoteRateLimitIT extends AbstractIntegrationTest {

    private static final String QUOTE_PATH = "/api/v1/me/price-quote";
    private static final Instant T0 = Instant.parse("2026-11-23T08:00:00Z");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private MutableClock clock;

    private final List<UUID> userIds = new ArrayList<>();
    private final List<UUID> examIds = new ArrayList<>();
    private final List<UUID> promotionIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        clock.reset();
        for (UUID userId : userIds) {
            redisTemplate.delete(VoucherQuoteRateLimiter.KEY_PREFIX + userId);
        }
        var leftoverKeys = redisTemplate.keys(VoucherQuoteRateLimiter.KEY_PREFIX + "*");
        if (leftoverKeys != null && !leftoverKeys.isEmpty()) {
            for (UUID userId : userIds) {
                leftoverKeys.stream()
                        .filter(k -> k.endsWith(userId.toString()))
                        .forEach(redisTemplate::delete);
            }
        }
        for (UUID promoId : promotionIds) {
            jdbc.update("DELETE FROM commerce.promotion WHERE id = ?", promoId);
        }
        promotionIds.clear();
        for (UUID examId : examIds) {
            jdbc.update("DELETE FROM commerce.storefront_product WHERE exam_id = ?", examId);
        }
        examIds.clear();
        for (UUID userId : userIds) {
            jdbc.update("DELETE FROM identity.user_account WHERE id = ?", userId);
        }
        userIds.clear();
    }

    @Test
    void perMinuteLimit() throws Exception {
        clock.fixAt(T0);
        UUID userId = createLearner();
        String productCode = insertOnSaleProduct();
        String validVoucher = saveVoucher(T0);

        // Requests without voucherCode are never counted (15 before limit)
        for (int i = 0; i < 15; i++) {
            mockMvc.perform(post(QUOTE_PATH)
                            .with(learner(userId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(productCode, null)))
                    .andExpect(status().isOk());
        }
        assertThat(redisTemplate.hasKey(VoucherQuoteRateLimiter.KEY_PREFIX + userId)).isFalse();

        // 10 requests with voucherCode (including invalid code checks) succeed; 11th returns 429
        for (int i = 0; i < 10; i++) {
            String code = (i % 2 == 0) ? validVoucher : "WRONG" + i;
            int expectedStatus = (i % 2 == 0) ? 200 : 422;
            mockMvc.perform(post(QUOTE_PATH)
                            .with(learner(userId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(productCode, code)))
                    .andExpect(status().is(expectedStatus))
                    .andExpect(header().string("X-RateLimit-Remaining", Integer.toString(9 - i)));
        }

        mockMvc.perform(post(QUOTE_PATH)
                        .with(learner(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(productCode, validVoucher)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        // Requests without voucherCode still succeed even after hitting the voucher rate limit
        mockMvc.perform(post(QUOTE_PATH)
                        .with(learner(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(productCode, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalPrice").value(200_000L));
    }

    @Test
    void perDayLimit() throws Exception {
        clock.fixAt(T0);
        UUID userA = createLearner();
        UUID userB = createLearner();
        String productCode = insertOnSaleProduct();
        String validVoucher = saveVoucher(T0);

        // 50 requests across 5 minute-windows (10 per minute, advancing clock by 61s after each batch)
        for (int batch = 0; batch < 5; batch++) {
            for (int i = 0; i < 10; i++) {
                mockMvc.perform(post(QUOTE_PATH)
                                .with(learner(userA))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(productCode, validVoucher)))
                        .andExpect(status().isOk());
            }
            clock.advance(Duration.ofSeconds(61));
        }

        // 51st request in the same 24h hits the daily limit even though the 1-minute window is empty
        mockMvc.perform(post(QUOTE_PATH)
                        .with(learner(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(productCode, validVoucher)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        // Different learner account is unaffected
        mockMvc.perform(post(QUOTE_PATH)
                        .with(learner(userB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(productCode, validVoucher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalPrice").value(180_000L));
    }

    private String body(String productCode, String voucherCode) throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", productCode);
        if (voucherCode != null) {
            map.put("voucherCode", voucherCode);
        }
        return mapper.writeValueAsString(map);
    }

    private RequestPostProcessor learner(UUID userId) {
        return testSecuritySupport.apply(new SimulatedPrincipal(userId, "rl." + userId, "STUDENT", false, List.of()));
    }

    private UUID createLearner() {
        UUID userId = UUID.randomUUID();
        Long seq = jdbc.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        String username = "rl." + userId.toString().substring(0, 8);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO identity.user_account
                    (id, code, username, username_normalized, full_name, email, email_normalized,
                     source, status, must_change_password, created_at, created_by, updated_at, updated_by, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, 2, false, ?, ?, ?, ?, 0)
                """, userId, String.format("U%06d", seq), username, username, "RateLimit Learner",
                userId + "@test.local", userId + "@test.local", now, userId, now, userId);
        userIds.add(userId);
        return userId;
    }

    private String insertOnSaleProduct() {
        UUID examId = UUID.randomUUID();
        String code = "RL" + examId.toString().replace("-", "").substring(0, 8).toUpperCase();
        jdbc.update("""
                INSERT INTO commerce.storefront_product
                    (id, exam_id, code, code_normalized, version_no, sale_state, name, search_text,
                     kind_code, scope, question_count, question_count_max, duration_minutes, duration_minutes_max,
                     build_mode, list_price_vnd, attempts_granted, card, detail, projection_hash, published_at, updated_at)
                VALUES (?, ?, ?, ?, 1, 'ON_SALE', ?, ?, 'HSA', 'FULL', 50, 50, 75, 75, 'STANDARD', 200000, 3,
                        '{}'::jsonb, '{}'::jsonb, 'test', now(), now())
                """, UUID.randomUUID(), examId, code, code.toLowerCase(), "Exam " + code, ("exam " + code).toLowerCase());
        examIds.add(examId);
        return code;
    }

    private String saveVoucher(Instant referenceNow) {
        UUID id = Ids.newId();
        promotionIds.add(id);
        String code = "RLV" + id.toString().replace("-", "").substring(0, 6).toUpperCase();
        promotionRepository.saveAndFlush(Promotion.voucher(
                id, "RL Voucher", null, code, DiscountType.AMOUNT, 20_000L, null,
                referenceNow.minus(1, ChronoUnit.HOURS), referenceNow.plus(2, ChronoUnit.DAYS),
                null, null, false, PromotionScope.ALL, PromotionAudienceType.ALL,
                PromotionStatus.ACTIVE, referenceNow, null, null));
        return code;
    }
}
