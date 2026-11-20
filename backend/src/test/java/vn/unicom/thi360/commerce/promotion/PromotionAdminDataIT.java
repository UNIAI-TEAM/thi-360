package vn.unicom.thi360.commerce.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.pricing.CommercePricingProperties;
import vn.unicom.thi360.commerce.pricing.PriceCalculator;
import vn.unicom.thi360.commerce.promotion.api.CapWarning;
import vn.unicom.thi360.commerce.promotion.api.PromotionDetail;
import vn.unicom.thi360.commerce.promotion.api.PromotionSummary;
import vn.unicom.thi360.commerce.promotion.api.UpdatePromotionRequest;
import vn.unicom.thi360.commerce.promotion.application.PromotionAdminService;
import vn.unicom.thi360.commerce.promotion.application.PromotionCapWarningService;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;
import vn.unicom.thi360.commerce.promotion.domain.Promotion;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudienceType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionChangeAction;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScopeItem;
import vn.unicom.thi360.commerce.promotion.domain.PromotionStatus;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionAudienceRepository;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionChangeLogRepository;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRedemptionRepository;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionScopeItemRepository;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.identity.spi.ClassMembershipPort;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.api.PageResponse;
import vn.unicom.thi360.shared.idempotency.IdempotentCommand;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.security.PermissionSet;
import vn.unicom.thi360.shared.security.Realm;
import vn.unicom.thi360.shared.web.AppMessages;

/**
 * Integration tests for promotion admin derived data & audit history (BS §10, AC-41, AC-42, AC-43).
 */
@TestPropertySource(properties = "thi360.commerce.vnpay-min-amount=5000")
class PromotionAdminDataIT extends PromotionIntegrationTest {

    private static final String BASE = "/api/v1/admin/promotions";

    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private StorefrontProductRepository storefrontProductRepository;
    @Autowired private PromotionScopeItemRepository scopeItemRepository;
    @Autowired private PromotionAudienceRepository audienceRepository;
    @Autowired private PromotionRedemptionRepository redemptionRepository;
    @Autowired private PromotionChangeLogRepository changeLogRepository;
    @Autowired private PriceCalculator priceCalculator;
    @Autowired private PublishedExamPort publishedExamPort;
    @Autowired private ClassMembershipPort classMembershipPort;
    @Autowired private UserDirectory userDirectory;
    @Autowired private IdempotentCommand idempotentCommand;
    @Autowired private AuditRecorder auditRecorder;
    @Autowired private CurrentUserProvider currentUserProvider;
    @Autowired private AppMessages messages;
    @Autowired private Clock clock;
    @Autowired private TransactionTemplate transactionTemplate;

    private final List<UUID> examIds = new ArrayList<>();
    private final List<UUID> actorIds = new ArrayList<>();

    @AfterEach
    void cleanupData() {
        for (UUID examId : examIds) {
            jdbc.update("DELETE FROM commerce.storefront_product WHERE exam_id = ?", examId);
        }
        examIds.clear();
        for (UUID actorId : actorIds) {
            jdbc.update("DELETE FROM shared.idempotency_key WHERE scope LIKE ?", actorId + ":%");
            jdbc.update("DELETE FROM shared.outbox_event WHERE topic = 'audit' AND payload ->> 'actorId' = ?",
                    actorId.toString());
        }
        actorIds.clear();
    }

    @Test
    void capWarningsListOnlyFlooredExams() throws Exception {
        String tag = shortToken();
        UUID flooredExam = insertOnSaleExam("Floored Exam " + tag, 10_000L);
        UUID safeExam = insertOnSaleExam("Safe Exam " + tag, 50_000L);

        UUID promoId = trackPromotion(Ids.newId());
        Instant now = Instant.now();
        promotionRepository.saveAndFlush(Promotion.auto(
                promoId, "Cap Warning Auto " + tag, null, DiscountType.AMOUNT, 7_000L, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS), null,
                PromotionScope.EXAM, PromotionStatus.ACTIVE, now, null, null));
        scopeItemRepository.saveAndFlush(PromotionScopeItem.forExam(Ids.newId(), promoId, flooredExam));
        scopeItemRepository.saveAndFlush(PromotionScopeItem.forExam(Ids.newId(), promoId, safeExam));

        // Both GET /promotions and GET /promotions/{id} list ONLY the floored exam
        JsonNode listRes = mapper.readTree(mockMvc.perform(get(BASE)
                        .param("q", tag)
                        .with(asUser(Permissions.PROMOTION_VIEW)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode summaryWarnings = listRes.path("items").get(0).path("capWarnings");
        assertThat(summaryWarnings).hasSize(1);
        assertThat(summaryWarnings.get(0).path("examId").asText()).isEqualTo(flooredExam.toString());
        assertThat(summaryWarnings.get(0).path("examName").asText()).isEqualTo("Floored Exam " + tag);
        assertThat(summaryWarnings.get(0).path("listPrice").asLong()).isEqualTo(10_000L);
        assertThat(summaryWarnings.get(0).path("discountedPrice").asLong()).isEqualTo(3_000L);
        assertThat(summaryWarnings.get(0).path("floorPrice").asLong()).isEqualTo(5_000L);
        assertThat(summaryWarnings.get(0).path("floorPrice").asLong())
                .isLessThanOrEqualTo(summaryWarnings.get(0).path("listPrice").asLong());

        JsonNode detailRes = mapper.readTree(mockMvc.perform(get(BASE + "/" + promoId)
                        .with(asUser(Permissions.PROMOTION_VIEW)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode detailWarnings = detailRes.path("capWarnings");
        assertThat(detailWarnings).hasSize(1);
        assertThat(detailWarnings.get(0)).isEqualTo(summaryWarnings.get(0));

        // VOUCHER on the same 10,000 VND exam produces empty capWarnings (only AUTO promos generate cap warnings)
        UUID voucherId = trackPromotion(Ids.newId());
        promotionRepository.saveAndFlush(Promotion.voucher(
                voucherId, "Voucher No Cap Warning " + tag, null, "CAPV" + tag,
                DiscountType.AMOUNT, 7_000L, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS), null, null, false,
                PromotionScope.EXAM, PromotionAudienceType.ALL, PromotionStatus.ACTIVE, now, null, null));
        scopeItemRepository.saveAndFlush(PromotionScopeItem.forExam(Ids.newId(), voucherId, flooredExam));

        mockMvc.perform(get(BASE + "/" + voucherId).with(asUser(Permissions.PROMOTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capWarnings").isEmpty());

        // When vnpayMinAmount exceeds listPrice, floorPrice is clamped to eval.promoPrice() (<= listPrice)
        PromotionCapWarningService highFloorWarnings = new PromotionCapWarningService(
                storefrontProductRepository, scopeItemRepository, new CommercePricingProperties(15_000L, null));
        Promotion auto = promotionRepository.findByIdAndDeletedAtIsNull(promoId).orElseThrow();
        List<CapWarning> clampedWarnings = highFloorWarnings.computeForOne(
                auto, scopeItemRepository.findByPromotionId(promoId));
        assertThat(clampedWarnings).extracting(CapWarning::examId).contains(flooredExam);
        CapWarning flooredWarning = clampedWarnings.stream()
                .filter(w -> w.examId().equals(flooredExam))
                .findFirst().orElseThrow();
        assertThat(flooredWarning.floorPrice()).isEqualTo(10_000L);
        assertThat(flooredWarning.floorPrice()).isLessThanOrEqualTo(flooredWarning.listPrice());
    }

    @Test
    void noFloorNoWarning() {
        String tag = shortToken();
        UUID examA = insertOnSaleExam("NoFloor Exam " + tag, 10_000L);
        UUID promoId = trackPromotion(Ids.newId());
        Instant now = Instant.now();
        promotionRepository.saveAndFlush(Promotion.auto(
                promoId, "NoFloor Auto " + tag, null, DiscountType.AMOUNT, 7_000L, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS), null,
                PromotionScope.EXAM, PromotionStatus.ACTIVE, now, null, null));
        scopeItemRepository.saveAndFlush(PromotionScopeItem.forExam(Ids.newId(), promoId, examA));

        CommercePricingProperties disabledFloorProps = new CommercePricingProperties(0L, null);
        PromotionCapWarningService noFloorWarnings = new PromotionCapWarningService(
                storefrontProductRepository, scopeItemRepository, disabledFloorProps);
        PromotionAdminService noFloorService = new PromotionAdminService(
                promotionRepository, scopeItemRepository, audienceRepository, redemptionRepository,
                changeLogRepository, storefrontProductRepository, noFloorWarnings, priceCalculator,
                catalogLookup, publishedExamPort, classMembershipPort, userDirectory,
                idempotentCommand, auditRecorder, currentUserProvider, messages, clock);

        PageResponse<PromotionSummary> summaries = noFloorService.listPromotions(null, null, null, tag, 0, 20);
        assertThat(summaries.items()).hasSize(1);
        List<CapWarning> listWarnings = summaries.items().getFirst().capWarnings();
        assertThat(listWarnings).isEmpty();

        PromotionDetail detail = noFloorService.getPromotion(promoId, 0, 20, null, null);
        assertThat(detail.capWarnings()).isEmpty();
    }

    @Test
    void totalDiscountCountsConfirmedOnly() throws Exception {
        Promotion voucher = saveVoucher(10, 5);

        UUID orderConfirmed1 = UUID.randomUUID();
        UUID orderConfirmed2 = UUID.randomUUID();
        UUID orderReserved = UUID.randomUUID();
        UUID orderReleased = UUID.randomUUID();

        redemptionService.reserve(voucher.getId(), UUID.randomUUID(), orderConfirmed1, "DH-C1", 15_000L);
        redemptionService.confirm(voucher.getId(), orderConfirmed1);

        redemptionService.reserve(voucher.getId(), UUID.randomUUID(), orderConfirmed2, "DH-C2", 25_000L);
        redemptionService.confirm(voucher.getId(), orderConfirmed2);

        redemptionService.reserve(voucher.getId(), UUID.randomUUID(), orderReserved, "DH-RES", 10_000L);

        redemptionService.reserve(voucher.getId(), UUID.randomUUID(), orderReleased, "DH-REL", 30_000L);
        redemptionService.release(voucher.getId(), orderReleased);

        mockMvc.perform(get(BASE + "/" + voucher.getId())
                        .with(asUser(Permissions.PROMOTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDiscountPaid").value(40_000L))
                .andExpect(jsonPath("$.confirmedCount").value(2L))
                .andExpect(jsonPath("$.reservedCount").value(1L));
    }

    @Test
    void historyRecordsEachChange() throws Exception {
        UUID actor = UUID.randomUUID();
        actorIds.add(actor);
        var auth = asUser(actor, "hist.admin", Permissions.PROMOTION_VIEW, Permissions.PROMOTION_MODIFY);
        Instant now = Instant.now();

        MvcResult createRes = mockMvc.perform(post(BASE)
                        .with(auth)
                        .header("Idempotency-Key", "k-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "kind", "VOUCHER",
                                "name", "History V1",
                                "code", "HST" + shortToken(),
                                "discountType", "AMOUNT",
                                "discountValue", 20_000L,
                                "startsAt", now.minus(1, ChronoUnit.HOURS).toString(),
                                "endsAt", now.plus(2, ChronoUnit.DAYS).toString(),
                                "scope", "ALL"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = mapper.readTree(createRes.getResponse().getContentAsString());
        UUID promoId = UUID.fromString(created.path("id").asText());
        trackPromotion(promoId);
        long rev1 = created.path("revision").asLong();

        JsonNode patched = mapper.readTree(mockMvc.perform(patch(BASE + "/" + promoId)
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "expectedRevision", rev1,
                                "name", "History V2",
                                "discountValue", 30_000L))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        long rev2 = patched.path("revision").asLong();

        JsonNode stopped = mapper.readTree(mockMvc.perform(post(BASE + "/" + promoId + "/stop")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("expectedRevision", rev2))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        long rev3 = stopped.path("revision").asLong();

        // Repeated /stop is a no-op: revision unchanged and no duplicate STOPPED -> STOPPED history row (R1-22)
        mockMvc.perform(post(BASE + "/" + promoId + "/stop")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("expectedRevision", rev3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STOPPED"))
                .andExpect(jsonPath("$.revision").value(rev3));

        JsonNode resumed = mapper.readTree(mockMvc.perform(post(BASE + "/" + promoId + "/resume")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("expectedRevision", rev3))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        long rev4 = resumed.path("revision").asLong();

        // Repeated /resume and no-op PATCH also leave revision and history untouched (R1-22)
        mockMvc.perform(post(BASE + "/" + promoId + "/resume")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("expectedRevision", rev4))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.revision").value(rev4));

        mockMvc.perform(patch(BASE + "/" + promoId)
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("expectedRevision", rev4, "name", "History V2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(rev4));

        JsonNode detail = mapper.readTree(mockMvc.perform(get(BASE + "/" + promoId).with(auth))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode history = detail.path("history");
        assertThat(history).hasSize(4);

        // Newest-first order
        assertThat(history.get(0).path("action").asText()).isEqualTo(PromotionChangeAction.RESUMED.name());
        assertThat(history.get(1).path("action").asText()).isEqualTo(PromotionChangeAction.STOPPED.name());
        assertThat(history.get(2).path("action").asText()).isEqualTo(PromotionChangeAction.UPDATED.name());
        assertThat(history.get(3).path("action").asText()).isEqualTo(PromotionChangeAction.CREATED.name());

        // Verify old & new values on UPDATED and STOPPED
        assertThat(history.get(1).path("changes").get(0).path("field").asText()).isEqualTo("status");
        assertThat(history.get(1).path("changes").get(0).path("oldValue").asText()).isEqualTo("ACTIVE");
        assertThat(history.get(1).path("changes").get(0).path("newValue").asText()).isEqualTo("STOPPED");

        assertThat(history.get(2).path("changes").toString()).contains("History V1", "History V2");
        assertThat(history.get(3).path("changes").get(0).path("oldValue").isNull()).isTrue();
    }

    @Test
    void rejectedChangeLeavesNoHistory() throws Exception {
        UUID actor = UUID.randomUUID();
        actorIds.add(actor);
        var auth = asUser(actor, "rej.admin", Permissions.PROMOTION_VIEW, Permissions.PROMOTION_MODIFY);
        Instant now = Instant.now();

        JsonNode created = mapper.readTree(mockMvc.perform(post(BASE)
                        .with(auth)
                        .header("Idempotency-Key", "k-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "kind", "VOUCHER",
                                "name", "Reject History",
                                "code", "REJ" + shortToken(),
                                "discountType", "AMOUNT",
                                "discountValue", 20_000L,
                                "startsAt", now.minus(1, ChronoUnit.HOURS).toString(),
                                "endsAt", now.plus(2, ChronoUnit.DAYS).toString(),
                                "scope", "ALL"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        UUID promoId = UUID.fromString(created.path("id").asText());
        trackPromotion(promoId);
        long wrongRevision = created.path("revision").asLong() + 42;

        mockMvc.perform(patch(BASE + "/" + promoId)
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("expectedRevision", wrongRevision, "name", "No"))))
                .andExpect(status().isPreconditionFailed());
        mockMvc.perform(post(BASE + "/" + promoId + "/stop")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("expectedRevision", wrongRevision))))
                .andExpect(status().isPreconditionFailed());
        mockMvc.perform(post(BASE + "/" + promoId + "/resume")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("expectedRevision", wrongRevision))))
                .andExpect(status().isPreconditionFailed());

        long logCount = jdbc.queryForObject(
                "SELECT count(*) FROM commerce.promotion_change_log WHERE promotion_id = ?", Long.class, promoId);
        assertThat(logCount).isEqualTo(1L);
    }

    @Test
    void inUseUpdateRejectsEndsAtEqualToCurrentInstant() {
        Instant fixedNow = Instant.parse("2026-11-21T12:00:00Z");
        Clock fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);
        CurrentUser fixedActor = new CurrentUser(
                UUID.randomUUID(), "fixed.admin", Realm.APP, Access.MANAGE, PermissionSet.all());
        CurrentUserProvider fixedUserProvider = new CurrentUserProvider() {
            @Override
            public CurrentUser requireCurrentUser() {
                return fixedActor;
            }
        };
        PromotionAdminService fixedClockService = new PromotionAdminService(
                promotionRepository, scopeItemRepository, audienceRepository, redemptionRepository,
                changeLogRepository, storefrontProductRepository,
                new PromotionCapWarningService(storefrontProductRepository, scopeItemRepository,
                        new CommercePricingProperties(5_000L, null)),
                priceCalculator, catalogLookup, publishedExamPort, classMembershipPort, userDirectory,
                idempotentCommand, auditRecorder, fixedUserProvider, messages, fixedClock);

        UUID promoId = trackPromotion(Ids.newId());
        promotionRepository.saveAndFlush(Promotion.voucher(
                promoId, "Exact Now Boundary", null, "EXN" + shortToken(),
                DiscountType.AMOUNT, 10_000L, null,
                fixedNow.minus(1, ChronoUnit.HOURS), fixedNow.plus(1, ChronoUnit.DAYS),
                10, null, false, PromotionScope.ALL, PromotionAudienceType.ALL, PromotionStatus.ACTIVE,
                fixedNow.minus(1, ChronoUnit.HOURS), null, null));
        jdbc.update("UPDATE commerce.promotion SET used_count = 1 WHERE id = ?", promoId);

        UpdatePromotionRequest reqAtExactNow = new UpdatePromotionRequest(
                1L, null, null, null, null, null, null, null, null, null, null,
                fixedNow, null, null, null, null, null, null, null, null, null, null, null, null);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                transactionTemplate.executeWithoutResult(status -> fixedClockService.updatePromotion(promoId, reqAtExactNow))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getCode())
                .isEqualTo("PROMOTION_ENDS_AT_PAST");
    }

    private UUID insertOnSaleExam(String name, long listPriceVnd) {
        UUID examId = UUID.randomUUID();
        UUID kindId = catalogLookup.findActiveItem("EXAM", "HSA").orElseThrow().id();
        String code = "PD" + shortToken();
        jdbc.update("""
                INSERT INTO commerce.storefront_product
                    (id, exam_id, code, code_normalized, version_no, sale_state, name, search_text, exam_item_id,
                     kind_code, scope, question_count, question_count_max, duration_minutes, duration_minutes_max,
                     build_mode, list_price_vnd, attempts_granted, card, detail, projection_hash, published_at, updated_at)
                VALUES (?, ?, ?, ?, 1, 'ON_SALE', ?, ?, ?, 'HSA', 'FULL', 50, 50, 75, 75, 'STANDARD', ?, 3,
                        '{}'::jsonb, '{}'::jsonb, 'test', now(), now())
                """, UUID.randomUUID(), examId, code, code.toLowerCase(), name, name.toLowerCase(), kindId, listPriceVnd);
        examIds.add(examId);
        return examId;
    }

    private static String shortToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
