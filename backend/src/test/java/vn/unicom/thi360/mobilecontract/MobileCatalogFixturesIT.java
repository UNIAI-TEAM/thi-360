package vn.unicom.thi360.mobilecontract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;
import vn.unicom.thi360.commerce.promotion.domain.Promotion;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScopeItem;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionScopeItemRepository;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.commerce.storefront.api.AnswerTypeGroup;
import vn.unicom.thi360.commerce.storefront.api.BundleDetailItem;
import vn.unicom.thi360.commerce.storefront.api.DecimalRange;
import vn.unicom.thi360.commerce.storefront.api.DetailBenefits;
import vn.unicom.thi360.commerce.storefront.api.DetailChoiceGroup;
import vn.unicom.thi360.commerce.storefront.api.DetailChoiceOption;
import vn.unicom.thi360.commerce.storefront.api.DetailPart;
import vn.unicom.thi360.commerce.storefront.api.DetailScoring;
import vn.unicom.thi360.commerce.storefront.api.ExamKindRef;
import vn.unicom.thi360.commerce.storefront.api.IntRange;
import vn.unicom.thi360.commerce.storefront.api.LocalizedName;
import vn.unicom.thi360.commerce.storefront.api.ProductCard;
import vn.unicom.thi360.commerce.storefront.api.ProductDetail;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.security.RateLimitFilter;

public class MobileCatalogFixturesIT extends AbstractIntegrationTest {

    public static final class Fixtures {
        public static final Set<String> SET = Set.of(
                "api/storefront-filters-200.json",
                "api/storefront-filters-429-rate-limited.json",
                "api/storefront-products-list-200.json",
                "api/storefront-products-list-200-empty.json",
                "api/storefront-products-list-429-rate-limited.json",
                "api/storefront-products-detail-200-exam.json",
                "api/storefront-products-detail-200-bundle.json",
                "api/storefront-products-detail-404-not-found.json",
                "api/storefront-products-detail-410-product-discontinued.json",
                "api/exam-access-query-200.json",
                "api/exam-access-query-400-validation-failed.json");

        private Fixtures() {}
    }

    public static final Set<String> FIXTURES = Fixtures.SET;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StringRedisTemplate redis;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private PromotionScopeItemRepository promotionScopeItemRepository;
    @Autowired private EntitlementGrantService grantService;
    @Autowired private EntitlementPort entitlementPort;

    private CommerceTestFixtures fixtures;
    private MobileFixtureCertifier certifier;
    private final List<UUID> extraStorefrontIds = new ArrayList<>();
    private final List<UUID> attemptIds = new ArrayList<>();
    private final List<String> rateLimitIps = new ArrayList<>();

    @BeforeEach
    void setUp() {
        fixtures = new CommerceTestFixtures(jdbc, objectMapper, catalogLookup, promotionRepository, testSecuritySupport);
        certifier = new MobileFixtureCertifier(objectMapper);
        extraStorefrontIds.clear();
        attemptIds.clear();
        rateLimitIps.clear();
    }

    @AfterEach
    void tearDown() {
        for (UUID attemptId : attemptIds) {
            jdbc.update("DELETE FROM attempt.attempt_event WHERE attempt_id = ?", attemptId);
            jdbc.update("DELETE FROM attempt.attempt_item WHERE attempt_id = ?", attemptId);
            jdbc.update("DELETE FROM attempt.attempt_part WHERE attempt_id = ?", attemptId);
            jdbc.update("DELETE FROM attempt.attempt WHERE id = ?", attemptId);
        }
        attemptIds.clear();
        for (UUID rowId : extraStorefrontIds) {
            jdbc.update("DELETE FROM commerce.storefront_product WHERE id = ?", rowId);
        }
        extraStorefrontIds.clear();
        for (String ip : rateLimitIps) {
            redis.delete(RateLimitFilter.PUBLIC_KEY_PREFIX + ip);
        }
        rateLimitIps.clear();
        fixtures.cleanup();
    }

    @Test
    void allCatalogAndExamAccessContractFixturesMatchLiveResponses() throws Exception {
        String tag = "MCAT" + CommerceTestFixtures.shortToken();
        UUID hsaId = catalogLookup.findActiveItem("EXAM", "HSA").orElseThrow().id();
        UUID toanId = catalogLookup.findActiveItem("SUBJECT", "TOAN").orElseThrow().id();
        UUID vanId = catalogLookup.findActiveItem("SUBJECT", "VAN").orElseThrow().id();
        Instant now = Instant.now();

        UUID examId = UUID.randomUUID();
        String examCode = "EXAM-" + tag;
        String examName = "Đề thi HSA tổng hợp " + tag;
        UUID examRowId = insertExamStorefrontRow(
                examId, hsaId, "HSA", toanId, examCode, examName, "ON_SALE", 250_000L, 3, now.minusSeconds(120));

        Promotion autoPromo = fixtures.createAutoPromotion(
                "Ưu đãi khai giảng",
                DiscountType.PERCENT,
                20L,
                100_000L,
                now.minus(1, ChronoUnit.HOURS),
                now.plus(7, ChronoUnit.DAYS),
                100,
                PromotionScope.EXAM);
        promotionScopeItemRepository.saveAndFlush(PromotionScopeItem.forExam(Ids.newId(), autoPromo.getId(), examId));

        UUID bundleId = UUID.randomUUID();
        String bundleCode = "BD-" + tag;
        String bundleName = "Bộ đề luyện thi HSA " + tag;
        insertBundleStorefrontRow(
                bundleId, hsaId, "HSA", List.of(toanId, vanId), bundleCode, bundleName, 320_000L, 4,
                examId, examCode, now.minusSeconds(60));

        UUID discontinuedExamId = UUID.randomUUID();
        String discontinuedCode = "DISC-" + tag;
        insertExamStorefrontRow(
                discontinuedExamId, hsaId, "HSA", toanId, discontinuedCode,
                "Đề thi HSA đã ngừng bán " + tag, "DISCONTINUED", 190_000L, 2, now.minusSeconds(300));

        assertThat(examRowId).isNotNull();

        certifier.certify("api/storefront-filters-200.json", 200,
                mockMvc.perform(get("/api/v1/public/storefront/filters")
                                .header(ClientIpResolver.X_FORWARDED_FOR, freshIp()))
                        .andReturn(),
                Set.of());

        certifier.certify("api/storefront-products-list-200.json", 200,
                mockMvc.perform(get("/api/v1/public/storefront/products")
                                .param("q", tag)
                                .param("sort", "PRICE_ASC")
                                .header(ClientIpResolver.X_FORWARDED_FOR, freshIp()))
                        .andReturn(),
                Set.of("$.items[*].code", "$.items[*].name", "$.items[*].summary", "endsAt"));

        certifier.certify("api/storefront-products-list-200-empty.json", 200,
                mockMvc.perform(get("/api/v1/public/storefront/products")
                                .param("q", "KHONG-TON-TAI-" + tag)
                                .header(ClientIpResolver.X_FORWARDED_FOR, freshIp()))
                        .andReturn(),
                Set.of());

        certifier.certify("api/storefront-products-detail-200-exam.json", 200,
                mockMvc.perform(get("/api/v1/public/storefront/products/{code}", examCode)
                                .header(ClientIpResolver.X_FORWARDED_FOR, freshIp()))
                        .andReturn(),
                Set.of("$.code", "$.name", "summary", "description", "endsAt"));

        certifier.certify("api/storefront-products-detail-200-bundle.json", 200,
                mockMvc.perform(get("/api/v1/public/storefront/products/{code}", bundleCode)
                                .header(ClientIpResolver.X_FORWARDED_FOR, freshIp()))
                        .andReturn(),
                Set.of("$.code", "$.name", "summary", "description", "examId", "$.items[*].code"));

        certifier.certify("api/storefront-products-detail-404-not-found.json", 404,
                mockMvc.perform(get("/api/v1/public/storefront/products/{code}", "NO-SUCH-" + tag)
                                .header(ClientIpResolver.X_FORWARDED_FOR, freshIp()))
                        .andReturn(),
                Set.of("traceId"));

        certifier.certify("api/storefront-products-detail-410-product-discontinued.json", 410,
                mockMvc.perform(get("/api/v1/public/storefront/products/{code}", discontinuedCode)
                                .header(ClientIpResolver.X_FORWARDED_FOR, freshIp()))
                        .andReturn(),
                Set.of("traceId", "$.product.code", "$.product.name", "summary", "description"));

        String filtersRlIp = freshIp();
        for (int i = 0; i < 120; i++) {
            assertThat(mockMvc.perform(get("/api/v1/public/storefront/filters")
                                    .header(ClientIpResolver.X_FORWARDED_FOR, filtersRlIp))
                            .andReturn().getResponse().getStatus())
                    .isEqualTo(200);
        }
        MvcResult filtersLimited = mockMvc.perform(get("/api/v1/public/storefront/filters")
                        .header(ClientIpResolver.X_FORWARDED_FOR, filtersRlIp))
                .andReturn();
        certifier.certify("api/storefront-filters-429-rate-limited.json", 429, filtersLimited, Set.of("traceId"));

        String listRlIp = freshIp();
        for (int i = 0; i < 120; i++) {
            assertThat(mockMvc.perform(get("/api/v1/public/storefront/products")
                                    .param("q", "RL-" + tag)
                                    .header(ClientIpResolver.X_FORWARDED_FOR, listRlIp))
                            .andReturn().getResponse().getStatus())
                    .isEqualTo(200);
        }
        MvcResult listLimited = mockMvc.perform(get("/api/v1/public/storefront/products")
                        .param("q", "RL-" + tag)
                        .header(ClientIpResolver.X_FORWARDED_FOR, listRlIp))
                .andReturn();
        certifier.certify("api/storefront-products-list-429-rate-limited.json", 429, listLimited, Set.of("traceId"));

        var learner = fixtures.createLearner();
        var prodCanStart = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var orderCanStart = fixtures.insertStubOrder(
                learner, prodCanStart, OrderState.FULFILLED, PaymentMethod.VNPAY, 200_000L, null, now);
        fixtures.grantOrder(grantService, orderCanStart.id(), orderCanStart.code(), learner.id(), prodCanStart.examId(), 3);

        var prodInProgress = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var orderInProgress = fixtures.insertStubOrder(
                learner, prodInProgress, OrderState.FULFILLED, PaymentMethod.VNPAY, 200_000L, null, now);
        var lineInProgress = fixtures.grantOrder(
                grantService, orderInProgress.id(), orderInProgress.code(), learner.id(), prodInProgress.examId(), 2);
        UUID activeAttemptId = Ids.newId();
        entitlementPort.reserve(learner.id(), lineInProgress.entitlementId(), activeAttemptId);
        insertOpenAttemptRow(activeAttemptId, learner.id(), prodInProgress.examId(),
                lineInProgress.entitlementId(), lineInProgress.grantId(), prodInProgress.name(), hsaId, now);

        var prodExhausted = fixtures.insertOnSaleProduct("HSA", 200_000L, 1);
        var orderExhausted = fixtures.insertStubOrder(
                learner, prodExhausted, OrderState.FULFILLED, PaymentMethod.VNPAY, 200_000L, null, now);
        var lineExhausted = fixtures.grantOrder(
                grantService, orderExhausted.id(), orderExhausted.code(), learner.id(), prodExhausted.examId(), 1);
        UUID usedAttemptId = Ids.newId();
        entitlementPort.reserve(learner.id(), lineExhausted.entitlementId(), usedAttemptId);
        entitlementPort.confirmUse(usedAttemptId);

        var prodChecking = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        fixtures.insertStubOrder(
                learner, prodChecking, OrderState.PAID, PaymentMethod.VNPAY,
                200_000L, now.plusSeconds(900), now);

        String accessReqJson = objectMapper.writeValueAsString(
                java.util.Map.of("codes", List.of(
                        prodCanStart.code(),
                        prodInProgress.code(),
                        prodExhausted.code(),
                        prodChecking.code())));
        certifier.certify("api/exam-access-query-200.json", 200,
                mockMvc.perform(post("/api/v1/me/exam-access/query")
                                .with(fixtures.asLearner(learner))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(accessReqJson))
                        .andReturn(),
                Set.of("$.items[*].code", "entitlementId", "activeAttemptId", "pendingOrderCode"));

        certifier.certify("api/exam-access-query-400-validation-failed.json", 400,
                mockMvc.perform(post("/api/v1/me/exam-access/query")
                                .with(fixtures.asLearner(learner))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                        .andReturn(),
                Set.of("traceId"));

        certifier.assertCertifiedExactly(FIXTURES);
    }

    private UUID insertExamStorefrontRow(
            UUID examId,
            UUID examKindItemId,
            String kindCode,
            UUID subjectItemId,
            String code,
            String name,
            String saleState,
            long listPriceVnd,
            int attemptsGranted,
            Instant publishedAt) throws Exception {
        DetailBenefits benefits = new DetailBenefits(true, true, false, "Kèm giải thích chi tiết");
        ProductCard card = new ProductCard(
                code, name, "Tóm tắt " + code, null,
                new ExamKindRef(kindCode, "Đánh giá năng lực ĐHQGHN", "HSA"),
                "FULL", null, "STANDARD",
                new IntRange(50, 50), new IntRange(75, 75),
                attemptsGranted, listPriceVnd, listPriceVnd, null, 1, "EXAM");
        ProductDetail detail = new ProductDetail(
                code, name, "Tóm tắt " + code, null,
                new ExamKindRef(kindCode, "Đánh giá năng lực ĐHQGHN", "HSA"),
                "FULL", null, "STANDARD",
                new IntRange(50, 50), new IntRange(75, 75),
                new DecimalRange(BigDecimal.valueOf(100), BigDecimal.valueOf(100)),
                attemptsGranted, listPriceVnd, listPriceVnd, null, 1,
                "Mô tả chi tiết " + code, "Ôn luyện chuẩn cấu trúc", "Học sinh lớp 12",
                List.of(new DetailPart(1, "Tư duy định lượng", new IntRange(50, 50), new IntRange(75, 75),
                        new DecimalRange(BigDecimal.valueOf(100), BigDecimal.valueOf(100)),
                        true, List.of(new AnswerTypeGroup("SINGLE_CHOICE", 50, null)))),
                List.of(new DetailChoiceGroup("Phần tự chọn", List.of(
                        new DetailChoiceOption("BRANCH", "ANH", "Tiếng Anh", null, null, null, List.of(), List.of(), List.of())))),
                new DetailScoring("PER_PART", BigDecimal.valueOf(100), null, "RAW"),
                List.of(),
                benefits,
                "FULL_SOLUTION",
                "EXAM",
                List.of());

        UUID rowId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO commerce.storefront_product
                    (id, exam_id, product_type, code, code_normalized, version_no, sale_state, name, search_text,
                     exam_item_id, kind_code, scope, subject_item_ids, part_codes, part_names,
                     question_count, question_count_max, duration_minutes, duration_minutes_max,
                     max_points, max_points_max, build_mode, list_price_vnd, attempts_granted,
                     card, detail, projection_hash, published_at, updated_at)
                VALUES (?, ?, 'EXAM', ?, ?, 1, ?, ?, ?, ?, ?, 'FULL', ARRAY[?]::uuid[], ARRAY['P1']::text[], ARRAY['Phần 1']::text[],
                        50, 50, 75, 75, 100.00, 100.00, 'STANDARD', ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)
                """,
                rowId, examId, code, code.toLowerCase(), saleState, name, (name + " " + code).toLowerCase(),
                examKindItemId, kindCode, subjectItemId, listPriceVnd, attemptsGranted,
                objectMapper.writeValueAsString(card), objectMapper.writeValueAsString(detail),
                "hash-" + CommerceTestFixtures.shortToken(), Timestamp.from(publishedAt), Timestamp.from(publishedAt));
        extraStorefrontIds.add(rowId);
        return rowId;
    }

    private void insertBundleStorefrontRow(
            UUID bundleId,
            UUID examKindItemId,
            String kindCode,
            List<UUID> subjectItemIds,
            String code,
            String name,
            long listPriceVnd,
            int attemptsGranted,
            UUID memberExamId,
            String memberExamCode,
            Instant publishedAt) throws Exception {
        DetailBenefits benefits = new DetailBenefits(true, true, false, "Kèm giải thích chi tiết");
        ProductCard card = new ProductCard(
                code, name, "Tóm tắt bộ đề " + code, null,
                new ExamKindRef(kindCode, "Đánh giá năng lực ĐHQGHN", "HSA"),
                "BUNDLE", null, "STANDARD",
                new IntRange(90, 90), new IntRange(135, 135),
                attemptsGranted, listPriceVnd, listPriceVnd, null, 1, "BUNDLE");
        BundleDetailItem item1 = new BundleDetailItem(
                memberExamId, memberExamCode, "Phần Tư duy định lượng", "PART", "P1",
                new LocalizedName("Toán học", "Mathematics"), "STANDARD",
                new IntRange(50, 50), new IntRange(75, 75),
                new DecimalRange(BigDecimal.valueOf(50), BigDecimal.valueOf(50)),
                attemptsGranted, benefits, "FULL_SOLUTION", 1);
        BundleDetailItem item2 = new BundleDetailItem(
                UUID.randomUUID(), "SUB-VAN-01", "Môn Ngữ văn", "SUBJECT", null,
                new LocalizedName("Ngữ văn", "Literature"), "STANDARD",
                new IntRange(40, 40), new IntRange(60, 60),
                new DecimalRange(BigDecimal.valueOf(50), BigDecimal.valueOf(50)),
                attemptsGranted, benefits, "FULL_SOLUTION", 1);
        ProductDetail detail = new ProductDetail(
                code, name, "Tóm tắt bộ đề " + code, null,
                new ExamKindRef(kindCode, "Đánh giá năng lực ĐHQGHN", "HSA"),
                "BUNDLE", null, "STANDARD",
                new IntRange(90, 90), new IntRange(135, 135),
                new DecimalRange(BigDecimal.valueOf(100), BigDecimal.valueOf(100)),
                attemptsGranted, listPriceVnd, listPriceVnd, null, 1,
                "Mô tả bộ đề " + code, "Ôn luyện trọn bộ", "Thí sinh thi HSA",
                List.of(),
                List.of(),
                new DetailScoring("PER_PART", BigDecimal.valueOf(100), null, "RAW"),
                List.of(),
                benefits,
                "FULL_SOLUTION",
                "BUNDLE",
                List.of(item1, item2));

        UUID rowId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO commerce.storefront_product
                    (id, exam_id, bundle_id, product_type, code, code_normalized, version_no, sale_state, name,
                     search_text, exam_item_id, kind_code, scope, subject_item_ids, part_codes, part_names,
                     question_count, question_count_max, duration_minutes, duration_minutes_max,
                     max_points, max_points_max, build_mode, list_price_vnd, attempts_granted,
                     card, detail, projection_hash, published_at, updated_at)
                VALUES (?, NULL, ?, 'BUNDLE', ?, ?, 1, 'ON_SALE', ?, ?, ?, ?, 'BUNDLE', ARRAY[?, ?]::uuid[],
                        '{}'::text[], '{}'::text[], 90, 90, 135, 135, 100.00, 100.00, 'STANDARD', ?, ?,
                        ?::jsonb, ?::jsonb, ?, ?, ?)
                """,
                rowId, bundleId, code, code.toLowerCase(), name, (name + " " + code).toLowerCase(),
                examKindItemId, kindCode, subjectItemIds.get(0), subjectItemIds.get(1),
                listPriceVnd, attemptsGranted,
                objectMapper.writeValueAsString(card), objectMapper.writeValueAsString(detail),
                "hash-" + CommerceTestFixtures.shortToken(), Timestamp.from(publishedAt), Timestamp.from(publishedAt));
        extraStorefrontIds.add(rowId);
    }

    private void insertOpenAttemptRow(
            UUID attemptId, UUID userId, UUID examId, UUID entitlementId, UUID grantId,
            String examName, UUID kindItemId, Instant startedAt) {
        jdbc.update("""
                INSERT INTO attempt.attempt
                    (id, user_id, exam_id, exam_version_no, entitlement_id, grant_id, attempt_no,
                     exam_name, exam_kind_item_id, exam_kind_code, scope, timing, navigation_mode,
                     choices, seed, generation_token, state, regrading,
                     started_at, result_view, practice_scoring, created_at, updated_at, version)
                VALUES (?, ?, ?, 1, ?, ?, 1, ?, ?, 'HSA', 'FULL', 'PER_PART', 'SEQUENTIAL',
                        '{}'::jsonb, 42, ?, 'IN_PROGRESS', false, ?, 'FULL_SOLUTION', 'RAW', ?, ?, 0)
                """,
                attemptId, userId, examId, entitlementId, grantId, examName, kindItemId, Ids.newId(),
                Timestamp.from(startedAt), Timestamp.from(startedAt), Timestamp.from(startedAt));
        attemptIds.add(attemptId);
    }

    private String freshIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String ip = "198.18." + r.nextInt(10, 240) + "." + r.nextInt(1, 254);
        rateLimitIps.add(ip);
        return ip;
    }
}
