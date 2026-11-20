package vn.unicom.thi360.commerce.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.commerce.promotion.application.PromotionRedemptionService;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;
import vn.unicom.thi360.commerce.promotion.domain.Promotion;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScopeItem;
import vn.unicom.thi360.commerce.promotion.domain.PromotionStatus;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionScopeItemRepository;
import vn.unicom.thi360.commerce.storefront.application.StorefrontProjectionService;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * AC-19, AC-39, AC-40: Storefront public cards/details reflect active automatic promotions, revert when exhausted,
 * sort across pages by post-promotion price, and update {@code ETag}.
 */
class StorefrontPromotionIT extends AbstractStorefrontIT {

    private static final String BASE = "/api/v1/public/storefront";

    @Autowired
    private StorefrontProjectionService projection;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private PromotionScopeItemRepository scopeItemRepository;

    @Autowired
    private PromotionRedemptionService redemptionService;

    private final Set<UUID> createdPromotionIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanupPromotions() {
        for (UUID id : createdPromotionIds) {
            jdbc.update("DELETE FROM commerce.promotion_redemption WHERE promotion_id = ?", id);
            jdbc.update("DELETE FROM commerce.promotion WHERE id = ?", id);
        }
        createdPromotionIds.clear();
    }

    @Test
    void cardShowsAutoPromotion() throws Exception {
        String tag = "pr" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        TestExam exam = exam().named("Exam Promo " + tag).price(200_000L);
        sell(exam);

        Instant now = Instant.now();
        Instant endsAt = now.plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Promotion auto = createExamAutoPromo("Giam 25% " + tag, DiscountType.PERCENT, 25, 40_000L,
                now.minus(1, ChronoUnit.HOURS), endsAt, null, exam.examId);

        String ip = newClientIp();
        JsonNode anonPage = json(publicGet(BASE + "/products?q=" + exam.code, ip)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode anonCard = anonPage.path("items").get(0);
        assertThat(anonCard.path("listPrice").asLong()).isEqualTo(200_000L);
        assertThat(anonCard.path("price").asLong()).isEqualTo(160_000L);
        assertThat(anonCard.path("promotion").path("name").asText()).isEqualTo(auto.getName());
        assertThat(Instant.parse(anonCard.path("promotion").path("endsAt").asText())).isEqualTo(endsAt);

        JsonNode anonDetail = json(publicGet(BASE + "/products/" + exam.code, ip)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(anonDetail.path("listPrice").asLong()).isEqualTo(200_000L);
        assertThat(anonDetail.path("price").asLong()).isEqualTo(160_000L);
        assertThat(anonDetail.path("promotion").path("name").asText()).isEqualTo(auto.getName());

        // E9: student sees identical display price and promotion badge as anonymous guest
        JsonNode studentCard = json(mockMvc.perform(publicRequest(BASE + "/products?q=" + exam.code, ip)
                        .with(asStudent()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .path("items").get(0);
        assertThat(studentCard.path("price").asLong()).isEqualTo(160_000L);
        assertThat(studentCard.path("promotion")).isEqualTo(anonCard.path("promotion"));
    }

    @Test
    void exhaustedAutoPromotionStopsApplying() throws Exception {
        String tag = "ex" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        TestExam exam = exam().named("Exam Limit " + tag).price(200_000L);
        sell(exam);

        Instant now = Instant.now();
        Promotion auto = createExamAutoPromo("Limited Auto " + tag, DiscountType.AMOUNT, 50_000L, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS), 1, exam.examId);

        String ip = newClientIp();
        JsonNode before = json(publicGet(BASE + "/products?q=" + exam.code, ip)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("items").get(0);
        assertThat(before.path("price").asLong()).isEqualTo(150_000L);
        assertThat(before.path("promotion").path("name").asText()).isEqualTo(auto.getName());

        redemptionService.reserve(auto.getId(), UUID.randomUUID(), UUID.randomUUID(), 50_000L);

        JsonNode after = json(publicGet(BASE + "/products?q=" + exam.code, ip)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("items").get(0);
        assertThat(after.path("listPrice").asLong()).isEqualTo(200_000L);
        assertThat(after.path("price").asLong()).isEqualTo(200_000L);
        assertThat(after.path("promotion").isNull()).isTrue();

        JsonNode afterDetail = json(publicGet(BASE + "/products/" + exam.code, ip)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(afterDetail.path("price").asLong()).isEqualTo(200_000L);
        assertThat(afterDetail.path("promotion").isNull()).isTrue();
    }

    @Test
    void priceSortAndEtagFollowPromotion() throws Exception {
        String tag = "st" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        TestExam p1Expensive = exam().named("P1 " + tag).price(300_000L)
                .published(Instant.parse("2026-01-01T00:00:00Z"));
        TestExam p2 = exam().named("P2 " + tag).price(100_000L)
                .published(Instant.parse("2026-01-02T00:00:00Z"));
        TestExam p3 = exam().named("P3 " + tag).price(150_000L)
                .published(Instant.parse("2026-01-03T00:00:00Z"));
        TestExam p4 = exam().named("P4 " + tag).price(200_000L)
                .published(Instant.parse("2026-01-04T00:00:00Z"));
        sell(p1Expensive);
        sell(p2);
        sell(p3);
        sell(p4);

        String ip = newClientIp();
        MvcResult beforeAscResult = publicGet(BASE + "/products?sort=PRICE_ASC&size=2&page=0&q=" + tag, ip)
                .andExpect(status().isOk()).andReturn();
        String etagBeforePromo = beforeAscResult.getResponse().getHeader(HttpHeaders.ETAG);
        String detailEtagBefore = publicGet(BASE + "/products/" + p1Expensive.code, ip)
                .andExpect(status().isOk()).andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(codes(json(beforeAscResult.getResponse().getContentAsString())))
                .containsExactly(p2.code, p3.code);

        // Apply huge discount to P1 (300k - 250k = 50k), making it cheapest across ALL pages
        Instant now = Instant.now();
        Promotion auto = createExamAutoPromo("Mega " + tag, DiscountType.AMOUNT, 250_000L, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS), 1, p1Expensive.examId);

        MvcResult ascPage0 = publicGet(BASE + "/products?sort=PRICE_ASC&size=2&page=0&q=" + tag, ip)
                .andExpect(status().isOk()).andReturn();
        String etagWithPromo = ascPage0.getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(etagWithPromo).isNotEqualTo(etagBeforePromo);
        assertThat(codes(json(ascPage0.getResponse().getContentAsString())))
                .containsExactly(p1Expensive.code, p2.code);

        JsonNode ascPage1 = json(publicGet(BASE + "/products?sort=PRICE_ASC&size=2&page=1&q=" + tag, ip)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(codes(ascPage1)).containsExactly(p3.code, p4.code);

        // PRICE_DESC puts P1 last on page 1
        JsonNode descPage0 = json(publicGet(BASE + "/products?sort=PRICE_DESC&size=2&page=0&q=" + tag, ip)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(codes(descPage0)).containsExactly(p4.code, p3.code);
        JsonNode descPage1 = json(publicGet(BASE + "/products?sort=PRICE_DESC&size=2&page=1&q=" + tag, ip)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(codes(descPage1)).containsExactly(p2.code, p1Expensive.code);

        // Detail ETag changed when promo activated; conditional GET returns 304 when unchanged
        String detailEtagWithPromo = publicGet(BASE + "/products/" + p1Expensive.code, ip)
                .andExpect(status().isOk()).andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(detailEtagWithPromo).isNotEqualTo(detailEtagBefore);
        mockMvc.perform(publicRequest(BASE + "/products/" + p1Expensive.code, ip)
                        .header(HttpHeaders.IF_NONE_MATCH, detailEtagWithPromo))
                .andExpect(status().isNotModified());

        // Exhaust promo -> order and ETag revert
        redemptionService.reserve(auto.getId(), UUID.randomUUID(), UUID.randomUUID(), 250_000L);
        MvcResult afterExhaustResult = publicGet(BASE + "/products?sort=PRICE_ASC&size=2&page=0&q=" + tag, ip)
                .andExpect(status().isOk()).andReturn();
        assertThat(afterExhaustResult.getResponse().getHeader(HttpHeaders.ETAG)).isNotEqualTo(etagWithPromo);
        assertThat(codes(json(afterExhaustResult.getResponse().getContentAsString())))
                .containsExactly(p2.code, p3.code);
        String detailEtagAfterExhaust = publicGet(BASE + "/products/" + p1Expensive.code, ip)
                .andExpect(status().isOk()).andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(detailEtagAfterExhaust).isNotEqualTo(detailEtagWithPromo);
    }

    private void sell(TestExam exam) {
        serve(exam);
        projection.project(exam.examId);
    }

    private Promotion createExamAutoPromo(String name, DiscountType type, long value, Long maxDiscount,
            Instant startsAt, Instant endsAt, Integer totalLimit, UUID examId) {
        UUID promoId = Ids.newId();
        createdPromotionIds.add(promoId);
        Promotion saved = promotionRepository.saveAndFlush(Promotion.auto(
                promoId, name, null, type, value, maxDiscount, startsAt, endsAt, totalLimit,
                PromotionScope.EXAM, PromotionStatus.ACTIVE, Instant.now(), null, null));
        scopeItemRepository.saveAndFlush(PromotionScopeItem.forExam(Ids.newId(), promoId, examId));
        return saved;
    }

    private static List<String> codes(JsonNode page) {
        List<String> codes = new ArrayList<>();
        page.path("items").forEach(item -> codes.add(item.path("code").asText()));
        return codes;
    }
}
