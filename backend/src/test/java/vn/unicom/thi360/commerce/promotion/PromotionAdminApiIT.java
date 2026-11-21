package vn.unicom.thi360.commerce.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;
import vn.unicom.thi360.commerce.promotion.domain.Promotion;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudienceType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.domain.PromotionStatus;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.outbox.OutboxRelay;

/**
 * Integration tests for {@code /api/v1/admin/promotions/**} covering AC-20 through AC-32.
 */
class PromotionAdminApiIT extends PromotionIntegrationTest {

    private static final String BASE = "/api/v1/admin/promotions";
    private static final String ALLOWED_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private OutboxRelay outboxRelay;

    private final List<UUID> examIds = new ArrayList<>();
    private final List<UUID> classIds = new ArrayList<>();
    private final List<UUID> userIds = new ArrayList<>();
    private final Set<UUID> actorIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanupExtra() {
        for (UUID examId : examIds) {
            jdbc.update("DELETE FROM commerce.storefront_product WHERE exam_id = ?", examId);
        }
        examIds.clear();
        for (UUID classId : classIds) {
            jdbc.update("DELETE FROM classroom.class_member WHERE class_id = ?", classId);
            jdbc.update("DELETE FROM classroom.class_room WHERE id = ?", classId);
        }
        classIds.clear();
        for (UUID userId : userIds) {
            jdbc.update("DELETE FROM classroom.class_member WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM identity.user_account WHERE id = ?", userId);
        }
        userIds.clear();
        for (UUID actorId : actorIds) {
            jdbc.update("DELETE FROM shared.idempotency_key WHERE scope LIKE ?", actorId + ":%");
            jdbc.update("DELETE FROM shared.outbox_event WHERE topic = 'audit' AND payload ->> 'actorId' = ?",
                    actorId.toString());
        }
        actorIds.clear();
    }

    @Test
    void usedPromotionLocksFields() throws Exception {
        Promotion voucher = saveVoucher(10, 2);
        UUID buyerId = UUID.randomUUID();
        redemptionService.reserve(voucher.getId(), buyerId, stubOrder(buyerId), 20_000L);
        long rev = reloadRevision(voucher.getId());

        // Allowed fields succeed: name, description, endsAt, totalLimit, status
        Instant newEnds = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Map<String, Object> allowedPatch = new LinkedHashMap<>();
        allowedPatch.put("expectedRevision", rev);
        allowedPatch.put("name", "Updated Used Name");
        allowedPatch.put("description", "Updated description");
        allowedPatch.put("endsAt", newEnds.toString());
        allowedPatch.put("totalLimit", 20);
        allowedPatch.put("status", "STOPPED");

        JsonNode updated = mapper.readTree(patchJson(voucher.getId(), allowedPatch, adminAll())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(updated.path("name").asText()).isEqualTo("Updated Used Name");
        assertThat(updated.path("totalLimit").asInt()).isEqualTo(20);
        assertThat(updated.path("status").asText()).isEqualTo("STOPPED");
        long nextRev = updated.path("revision").asLong();

        // Removing totalLimit (clearTotalLimit = true) is also allowed on a used promotion
        patchJson(voucher.getId(), Map.of("expectedRevision", nextRev, "clearTotalLimit", true), adminAll())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLimit").isEmpty());
        nextRev = reloadRevision(voucher.getId());

        // Locked fields must fail with 409 PROMOTION_IN_USE
        List<Map<String, Object>> lockedChanges = List.of(
                Map.of("expectedRevision", nextRev, "discountValue", 30_000L),
                Map.of("expectedRevision", nextRev, "discountType", "PERCENT"),
                Map.of("expectedRevision", nextRev, "startsAt", Instant.now().minus(2, ChronoUnit.HOURS).toString()),
                Map.of("expectedRevision", nextRev, "code", "NEWCODE" + shortToken()),
                Map.of("expectedRevision", nextRev, "scope", "EXAM"),
                Map.of("expectedRevision", nextRev, "audience", "NEW_ACCOUNT"),
                Map.of("expectedRevision", nextRev, "combinable", true),
                Map.of("expectedRevision", nextRev, "perUserLimit", 5));
        for (Map<String, Object> lockedPatch : lockedChanges) {
            patchJson(voucher.getId(), lockedPatch, adminAll())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PROMOTION_IN_USE"));
        }

        // Null items inside ID arrays on in-use PATCH must fail with 400 VALIDATION_FAILED, never 500 NPE (R1-7..R1-9)
        for (String arrayField : List.of("examIds", "examKindIds", "audienceClassIds", "audienceUserIds")) {
            List<UUID> withNull = new ArrayList<>();
            withNull.add(null);
            Map<String, Object> nullItemPatch = new LinkedHashMap<>();
            nullItemPatch.put("expectedRevision", nextRev);
            nullItemPatch.put(arrayField, withNull);
            patchJson(voucher.getId(), nullItemPatch, adminAll())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value(arrayField));
        }
    }

    @Test
    void usedPromotionLimitsEditableValues() throws Exception {
        Promotion voucher = saveVoucher(5, 1);
        UUID b1 = UUID.randomUUID();
        UUID b2 = UUID.randomUUID();
        redemptionService.reserve(voucher.getId(), b1, stubOrder(b1), 20_000L);
        redemptionService.reserve(voucher.getId(), b2, stubOrder(b2), 20_000L);
        long rev = reloadRevision(voucher.getId());

        patchJson(voucher.getId(), Map.of(
                        "expectedRevision", rev,
                        "endsAt", Instant.now().minusSeconds(60).toString()), adminAll())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PROMOTION_ENDS_AT_PAST"));

        // Changing endsAt to now (or before now) is rejected because endsAt must be strictly future (R1-22)
        patchJson(voucher.getId(), Map.of(
                        "expectedRevision", rev,
                        "endsAt", Instant.now().toString()), adminAll())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PROMOTION_ENDS_AT_PAST"));

        patchJson(voucher.getId(), Map.of(
                        "expectedRevision", rev,
                        "totalLimit", 1), adminAll())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PROMOTION_TOTAL_LIMIT_BELOW_USED"));

        // Editing name on an ended in-use promotion while echoing unchanged past endsAt succeeds (R1-22)
        Instant now = Instant.now();
        Instant pastEnd = now.minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
        UUID endedUsedId = trackPromotion(Ids.newId());
        String endedCode = "EUS" + shortToken();
        promotionRepository.saveAndFlush(Promotion.voucher(
                endedUsedId, "Ended Used Old", null, endedCode, DiscountType.AMOUNT, 10_000L, null,
                now.minus(3, ChronoUnit.DAYS), pastEnd, 5, null, false,
                PromotionScope.ALL, PromotionAudienceType.ALL, PromotionStatus.ACTIVE, now, null, null));
        jdbc.update("UPDATE commerce.promotion SET used_count = 1 WHERE id = ?", endedUsedId);
        long endedRev = reloadRevision(endedUsedId);

        JsonNode endedUpdated = mapper.readTree(patchJson(endedUsedId, Map.of(
                        "expectedRevision", endedRev,
                        "name", "Ended Used Renamed",
                        "endsAt", pastEnd.toString()), adminAll())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ended Used Renamed"))
                .andExpect(jsonPath("$.endsAt").value(pastEnd.toString()))
                .andReturn().getResponse().getContentAsString());
        long endedRev2 = endedUpdated.path("revision").asLong();
        assertThat(endedRev2).isGreaterThan(endedRev);

        // Exact no-op PATCH does not bump revision or add empty history row (R1-22)
        patchJson(endedUsedId, Map.of(
                        "expectedRevision", endedRev2,
                        "name", "Ended Used Renamed",
                        "endsAt", pastEnd.toString()), adminAll())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(endedRev2));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM commerce.promotion_change_log WHERE promotion_id = ?",
                Long.class, endedUsedId)).isEqualTo(1L);
    }

    @Test
    void stopAndResumeRules() throws Exception {
        UUID actor = trackActor();
        UUID learner = createActiveLearner();
        UUID examId = insertOnSaleExam("HSA", 200_000L);
        String productCode = jdbc.queryForObject(
                "SELECT code FROM commerce.storefront_product WHERE exam_id = ?", String.class, examId);
        String code = "STP" + shortToken();
        Instant now = Instant.now();

        JsonNode created = createPromo(Map.of(
                "kind", "VOUCHER",
                "name", "Stop Resume Voucher",
                "code", code,
                "discountType", "AMOUNT",
                "discountValue", 25_000,
                "startsAt", now.minus(1, ChronoUnit.HOURS).toString(),
                "endsAt", now.plus(1, ChronoUnit.DAYS).toString(),
                "scope", "ALL"), "k-" + UUID.randomUUID(), adminAs(actor));
        UUID promoId = UUID.fromString(created.path("id").asText());
        long rev1 = created.path("revision").asLong();

        // Also create an AUTO promo for this exam to verify stopping immediately stops storefront pricing
        JsonNode autoCreated = createPromo(Map.of(
                "kind", "AUTO",
                "name", "Stop Auto " + shortToken(),
                "discountType", "AMOUNT",
                "discountValue", 30_000,
                "startsAt", now.minus(1, ChronoUnit.HOURS).toString(),
                "endsAt", now.plus(1, ChronoUnit.DAYS).toString(),
                "scope", "EXAM",
                "examIds", List.of(examId)), "k-" + UUID.randomUUID(), adminAs(actor));
        UUID autoId = UUID.fromString(autoCreated.path("id").asText());
        long autoRev = autoCreated.path("revision").asLong();

        mockMvc.perform(get("/api/v1/public/storefront/products/" + productCode)
                        .header("X-Forwarded-For", "198.18.200.10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(170_000L));

        // Reserve one redemption before stopping
        UUID orderId = stubOrder(learner, "DH-STOP-1");
        redemptionService.reserve(promoId, learner, orderId, "DH-STOP-1", 25_000L);

        // Stop immediately takes effect: voucher -> VOUCHER_INVALID, auto -> storefront reverts to listPrice
        JsonNode stopped = mapper.readTree(mockMvc.perform(post(BASE + "/" + promoId + "/stop")
                        .with(adminAs(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("expectedRevision", rev1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STOPPED"))
                .andReturn().getResponse().getContentAsString());
        long rev2 = stopped.path("revision").asLong();

        mockMvc.perform(post("/api/v1/me/price-quote")
                        .with(testSecuritySupport.apply(
                                new vn.unicom.thi360.shared.security.TestSecurity.SimulatedPrincipal(
                                        learner, "buyer." + learner, "STUDENT", false, List.of())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("code", productCode, "voucherCode", code))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VOUCHER_INVALID"));

        mockMvc.perform(post(BASE + "/" + autoId + "/stop")
                        .with(adminAs(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("expectedRevision", autoRev))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/public/storefront/products/" + productCode)
                        .header("X-Forwarded-For", "198.18.200.11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(200_000L))
                .andExpect(jsonPath("$.promotion").isEmpty());

        // Existing RESERVED redemption can still confirm()
        var confirmed = redemptionService.confirm(promoId, orderId);
        assertThat(confirmed.state().name()).isEqualTo("CONFIRMED");

        // Resume before ends_at succeeds
        JsonNode resumed = mapper.readTree(mockMvc.perform(post(BASE + "/" + promoId + "/resume")
                        .with(adminAs(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("expectedRevision", rev2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString());
        assertThat(resumed.path("revision").asLong()).isGreaterThan(rev2);

        // Ended stopped promotion cannot be resumed -> 409 PROMOTION_ENDED
        Promotion endedStopped = saveEndedStoppedVoucher();
        mockMvc.perform(post(BASE + "/" + endedStopped.getId() + "/resume")
                        .with(adminAs(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("expectedRevision", endedStopped.getRevision()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROMOTION_ENDED"));
    }

    @Test
    void deleteOnlyUnusedPromotion() throws Exception {
        Promotion unused = createRandomVoucher(10, 1);
        mockMvc.perform(delete(BASE + "/" + unused.getId()).with(adminAll()))
                .andExpect(status().isNoContent());

        Map<String, Object> deletedRow = promotionRow(unused.getId());
        assertThat(deletedRow.get("deleted_at")).isNotNull();
        mockMvc.perform(get(BASE + "/" + unused.getId()).with(adminAll()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROMOTION_NOT_FOUND"));
        mockMvc.perform(get(BASE).param("q", unused.getCode()).with(adminAll()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        Promotion used = createRandomVoucher(10, 1);
        UUID usedBuyer = UUID.randomUUID();
        redemptionService.reserve(used.getId(), usedBuyer, stubOrder(usedBuyer), 20_000L);
        mockMvc.perform(delete(BASE + "/" + used.getId()).with(adminAll()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROMOTION_IN_USE"));
    }

    @Test
    void createIsIdempotent() throws Exception {
        UUID actor = trackActor();
        String code = "IDM" + shortToken();
        Map<String, Object> body = voucherPayload("Idempotent Voucher", code, 30_000L);

        // Missing Idempotency-Key -> 400 IDEMPOTENCY_KEY_REQUIRED
        mockMvc.perform(post(BASE)
                        .with(adminAs(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        String idemKey = "idem-" + UUID.randomUUID();
        JsonNode first = createPromo(body, idemKey, adminAs(actor));
        UUID promoId = UUID.fromString(first.path("id").asText());

        // Replay with same key and body returns exact same promotion ID without duplicate row
        JsonNode replay = createPromo(body, idemKey, adminAs(actor));
        assertThat(replay.path("id").asText()).isEqualTo(promoId.toString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.promotion WHERE code_normalized = ?",
                Long.class, code.toLowerCase())).isEqualTo(1L);

        // Same key with different body -> 409 IDEMPOTENCY_KEY_REUSED
        Map<String, Object> differentBody = voucherPayload("Changed Name", "OTH" + shortToken(), 30_000L);
        mockMvc.perform(post(BASE)
                        .with(adminAs(actor))
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(differentBody)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void voucherCodeIsUniqueForever() throws Exception {
        UUID actor = trackActor();
        String baseCode = "UNQ" + shortToken();
        JsonNode created = createPromo(voucherPayload("Unique Voucher", baseCode, 20_000L),
                "k-" + UUID.randomUUID(), adminAs(actor));
        UUID promoId = UUID.fromString(created.path("id").asText());
        long rev = created.path("revision").asLong();

        // Case & whitespace variant -> 409 VOUCHER_CODE_TAKEN
        mockMvc.perform(post(BASE)
                        .with(adminAs(actor))
                        .header("Idempotency-Key", "k-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                voucherPayload("Dup", "  " + baseCode.toLowerCase() + "  ", 20_000L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VOUCHER_CODE_TAKEN"));

        // Stopped voucher still blocks reuse
        mockMvc.perform(post(BASE + "/" + promoId + "/stop")
                        .with(adminAs(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("expectedRevision", rev))))
                .andExpect(status().isOk());
        mockMvc.perform(post(BASE)
                        .with(adminAs(actor))
                        .header("Idempotency-Key", "k-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(voucherPayload("Dup Stopped", baseCode, 20_000L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VOUCHER_CODE_TAKEN"));

        // Soft-deleted voucher still blocks reuse
        mockMvc.perform(delete(BASE + "/" + promoId).with(adminAs(actor))).andExpect(status().isNoContent());
        mockMvc.perform(post(BASE)
                        .with(adminAs(actor))
                        .header("Idempotency-Key", "k-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(voucherPayload("Dup Deleted", baseCode, 20_000L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VOUCHER_CODE_TAKEN"));

        // Concurrent POSTs for the same normalized code: 1 succeeds (201), 1 gets 409 VOUCHER_CODE_TAKEN
        String raceCode = "RACE" + shortToken();
        List<Callable<Integer>> tasks = List.of(
                () -> postCreateStatus(actor, raceCode.toUpperCase()),
                () -> postCreateStatus(actor, " " + raceCode.toLowerCase() + " "));
        List<Outcome<Integer>> outcomes = raceAll(tasks);
        outcomes.forEach(o -> assertThat(o.error()).isNull());
        List<Integer> statuses = outcomes.stream().map(Outcome::value).toList();
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        jdbc.queryForList("SELECT id FROM commerce.promotion WHERE code_normalized = ?",
                UUID.class, raceCode.toLowerCase()).forEach(this::trackPromotion);
    }

    @Test
    void staleRevisionIsRejected() throws Exception {
        Promotion voucher = saveVoucher(10, 1);
        long stale = voucher.getRevision() + 99;

        patchJson(voucher.getId(), Map.of("expectedRevision", stale, "name", "Stale"), adminAll())
                .andExpect(status().isPreconditionFailed());

        mockMvc.perform(post(BASE + "/" + voucher.getId() + "/stop")
                        .with(adminAll())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("expectedRevision", stale))))
                .andExpect(status().isPreconditionFailed());

        mockMvc.perform(post(BASE + "/" + voucher.getId() + "/resume")
                        .with(adminAll())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("expectedRevision", stale))))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void autoPromotionRejectsVoucherFields() throws Exception {
        UUID actor = trackActor();
        Instant now = Instant.now();

        for (String extraField : List.of("code", "perUserLimit", "audience", "combinable")) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("kind", "AUTO");
            body.put("name", "Invalid Auto");
            body.put("discountType", "AMOUNT");
            body.put("discountValue", 10_000);
            body.put("startsAt", now.minus(1, ChronoUnit.HOURS).toString());
            body.put("endsAt", now.plus(1, ChronoUnit.DAYS).toString());
            body.put("scope", "ALL");
            switch (extraField) {
                case "code" -> body.put("code", "NOPE123");
                case "perUserLimit" -> body.put("perUserLimit", 1);
                case "audience" -> body.put("audience", "ALL");
                case "combinable" -> body.put("combinable", true);
            }
            mockMvc.perform(post(BASE)
                            .with(adminAs(actor))
                            .header("Idempotency-Key", "k-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }

        Map<String, Object> bundleBody = Map.of(
                "kind", "AUTO",
                "name", "Bundle Scope",
                "discountType", "AMOUNT",
                "discountValue", 10_000,
                "startsAt", now.minus(1, ChronoUnit.HOURS).toString(),
                "endsAt", now.plus(1, ChronoUnit.DAYS).toString(),
                "scope", "BUNDLE");
        mockMvc.perform(post(BASE)
                        .with(adminAs(actor))
                        .header("Idempotency-Key", "k-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(bundleBody)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void examScopeNeedsPublishedExam() throws Exception {
        UUID actor = trackActor();
        UUID unpublishedExamId = UUID.randomUUID();
        UUID publishedExamId = insertOnSaleExam("HSA", 200_000L);
        Instant now = Instant.now();

        Map<String, Object> draftBody = new LinkedHashMap<>();
        draftBody.put("kind", "AUTO");
        draftBody.put("name", "Draft Scope");
        draftBody.put("discountType", "AMOUNT");
        draftBody.put("discountValue", 10_000);
        draftBody.put("startsAt", now.minus(1, ChronoUnit.HOURS).toString());
        draftBody.put("endsAt", now.plus(1, ChronoUnit.DAYS).toString());
        draftBody.put("scope", "EXAM");
        draftBody.put("examIds", List.of(unpublishedExamId));

        mockMvc.perform(post(BASE)
                        .with(adminAs(actor))
                        .header("Idempotency-Key", "k-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(draftBody)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PROMOTION_SCOPE_INVALID"));

        Map<String, Object> validBody = new LinkedHashMap<>(draftBody);
        validBody.put("name", "Published Scope");
        validBody.put("examIds", List.of(publishedExamId));
        JsonNode created = createPromo(validBody, "k-" + UUID.randomUUID(), adminAs(actor));
        assertThat(created.path("scopeItems").get(0).path("id").asText()).isEqualTo(publishedExamId.toString());
    }

    @Test
    void listFiltersAndDetail() throws Exception {
        UUID actor = trackActor();
        UUID learner = createActiveLearner();
        Instant now = Instant.now();
        String tag = shortToken();

        UUID runningAutoId = trackPromotion(Ids.newId());
        promotionRepository.saveAndFlush(Promotion.auto(
                runningAutoId, "Running Auto " + tag, null, DiscountType.AMOUNT, 20_000L, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.DAYS), null,
                PromotionScope.ALL, PromotionStatus.ACTIVE, now, actor, "promo.admin"));

        UUID upcomingVoucherId = trackPromotion(Ids.newId());
        String upcomingCode = "UPC" + tag;
        promotionRepository.saveAndFlush(Promotion.voucher(
                upcomingVoucherId, "Upcoming Voucher " + tag, null, upcomingCode, DiscountType.PERCENT, 15, null,
                now.plus(2, ChronoUnit.HOURS), now.plus(2, ChronoUnit.DAYS), 50, 2, false,
                PromotionScope.ALL, PromotionAudienceType.ALL, PromotionStatus.ACTIVE, now, actor, "promo.admin"));

        UUID endedStoppedVoucherId = trackPromotion(Ids.newId());
        String endedCode = "END" + tag;
        promotionRepository.saveAndFlush(Promotion.voucher(
                endedStoppedVoucherId, "Ended Stopped " + tag, null, endedCode, DiscountType.AMOUNT, 10_000L, null,
                now.minus(2, ChronoUnit.DAYS), now.minus(1, ChronoUnit.HOURS), null, null, false,
                PromotionScope.ALL, PromotionAudienceType.ALL, PromotionStatus.STOPPED, now, actor, "promo.admin"));

        redemptionService.reserve(runningAutoId, learner, stubOrder(learner, "DH-" + tag), "DH-" + tag, 20_000L);

        // Filter by kind + state + q
        mockMvc.perform(get(BASE)
                        .param("kind", "AUTO")
                        .param("state", "RUNNING")
                        .param("q", tag)
                        .with(adminAll()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(runningAutoId.toString()));

        mockMvc.perform(get(BASE)
                        .param("kind", "VOUCHER")
                        .param("state", "UPCOMING")
                        .param("q", upcomingCode.toLowerCase())
                        .with(adminAll()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(upcomingVoucherId.toString()));

        mockMvc.perform(get(BASE)
                        .param("status", "STOPPED")
                        .param("state", "ENDED")
                        .param("q", tag)
                        .with(adminAll()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(endedStoppedVoucherId.toString()));

        // Detail includes paged redemptions with orderCode, user info, discountVnd, state
        mockMvc.perform(get(BASE + "/" + runningAutoId)
                        .param("redemptionPage", "0")
                        .param("redemptionSize", "10")
                        .with(adminAll()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(runningAutoId.toString()))
                .andExpect(jsonPath("$.redemptions.totalItems").value(1))
                .andExpect(jsonPath("$.redemptions.items[0].orderCode").value("DH-" + tag))
                .andExpect(jsonPath("$.redemptions.items[0].userId").value(learner.toString()))
                .andExpect(jsonPath("$.redemptions.items[0].username").isNotEmpty())
                .andExpect(jsonPath("$.redemptions.items[0].discountVnd").value(20_000))
                .andExpect(jsonPath("$.redemptions.items[0].state").value("RESERVED"));

        // Add more redemptions across CONFIRMED / RELEASED and distinct users to test state, search & paging (R1-19, R1-21)
        UUID learner2 = createActiveLearnerWithDetails("alpha." + tag.toLowerCase(), "Nguyen Minh " + tag);
        UUID learner3 = createActiveLearnerWithDetails("beta." + tag.toLowerCase(), "Tran Thu " + tag);
        UUID order2 = stubOrder(learner2, "DH-ORD2-" + tag);
        UUID order3 = stubOrder(learner3, "DH-ORD3-" + tag);
        UUID order4 = stubOrder(learner3, "DH-ORD4-" + tag);
        redemptionService.reserve(runningAutoId, learner2, order2, "DH-ORD2-" + tag, 20_000L);
        redemptionService.confirm(runningAutoId, order2);
        redemptionService.reserve(runningAutoId, learner3, order3, "DH-ORD3-" + tag, 20_000L);
        redemptionService.release(runningAutoId, order3);
        redemptionService.reserve(runningAutoId, learner3, order4, "DH-ORD4-" + tag, 20_000L);
        redemptionService.confirm(runningAutoId, order4);

        mockMvc.perform(get(BASE + "/" + runningAutoId)
                        .param("redemptionState", "CONFIRMED")
                        .with(adminAll()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redemptions.totalItems").value(2))
                .andExpect(jsonPath("$.redemptions.items[0].state").value("CONFIRMED"))
                .andExpect(jsonPath("$.redemptions.items[1].state").value("CONFIRMED"));

        mockMvc.perform(get(BASE + "/" + runningAutoId)
                        .param("redemptionState", "RELEASED")
                        .with(adminAll()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redemptions.totalItems").value(1))
                .andExpect(jsonPath("$.redemptions.items[0].orderCode").value("DH-ORD3-" + tag));

        // Search by orderCode, username, fullName, and userId
        mockMvc.perform(get(BASE + "/" + runningAutoId)
                        .param("redemptionQ", "dh-ord2-" + tag.toLowerCase())
                        .with(adminAll()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redemptions.totalItems").value(1))
                .andExpect(jsonPath("$.redemptions.items[0].orderCode").value("DH-ORD2-" + tag));

        mockMvc.perform(get(BASE + "/" + runningAutoId)
                        .param("redemptionQ", "ALPHA." + tag)
                        .with(adminAll()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redemptions.totalItems").value(1))
                .andExpect(jsonPath("$.redemptions.items[0].userId").value(learner2.toString()));

        mockMvc.perform(get(BASE + "/" + runningAutoId)
                        .param("redemptionQ", "tran thu " + tag.toLowerCase())
                        .with(adminAll()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redemptions.totalItems").value(2));

        mockMvc.perform(get(BASE + "/" + runningAutoId)
                        .param("redemptionState", "CONFIRMED")
                        .param("redemptionQ", "tran thu " + tag.toLowerCase())
                        .with(adminAll()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redemptions.totalItems").value(1))
                .andExpect(jsonPath("$.redemptions.items[0].orderCode").value("DH-ORD4-" + tag));

        // Multi-page redemptionPage / redemptionSize with non-overlapping pages
        JsonNode page0 = mapper.readTree(mockMvc.perform(get(BASE + "/" + runningAutoId)
                        .param("redemptionPage", "0")
                        .param("redemptionSize", "2")
                        .with(adminAll()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redemptions.totalItems").value(4))
                .andExpect(jsonPath("$.redemptions.totalPages").value(2))
                .andExpect(jsonPath("$.redemptions.items.length()").value(2))
                .andReturn().getResponse().getContentAsString());
        JsonNode page1 = mapper.readTree(mockMvc.perform(get(BASE + "/" + runningAutoId)
                        .param("redemptionPage", "1")
                        .param("redemptionSize", "2")
                        .with(adminAll()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redemptions.items.length()").value(2))
                .andReturn().getResponse().getContentAsString());
        Set<String> page0Ids = Set.of(
                page0.at("/redemptions/items/0/id").asText(),
                page0.at("/redemptions/items/1/id").asText());
        Set<String> page1Ids = Set.of(
                page1.at("/redemptions/items/0/id").asText(),
                page1.at("/redemptions/items/1/id").asText());
        assertThat(page0Ids).doesNotContainAnyElementsOf(page1Ids);
    }

    @Test
    void suggestedCodeAndPricePreview() throws Exception {
        for (int i = 0; i < 10; i++) {
            JsonNode res = mapper.readTree(mockMvc.perform(get(BASE + "/suggested-code").with(adminAll()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            String code = res.path("code").asText();
            assertThat(code).hasSize(8);
            assertThat(code).doesNotContain("0", "O", "1", "I");
            for (char c : code.toCharArray()) {
                assertThat(ALLOWED_CODE_CHARS.indexOf(c)).isGreaterThanOrEqualTo(0);
            }
        }

        UUID examId = insertOnSaleExam("HSA", 200_000L);
        UUID promoId = trackPromotion(Ids.newId());
        Instant now = Instant.now();
        promotionRepository.saveAndFlush(Promotion.auto(
                promoId, "Preview 25%", null, DiscountType.PERCENT, 25, 40_000L,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS), null,
                PromotionScope.ALL, PromotionStatus.ACTIVE, now, null, null));

        mockMvc.perform(post(BASE + "/price-preview")
                        .with(adminAll())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("examId", examId, "promotionId", promoId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listPrice").value(200_000L))
                .andExpect(jsonPath("$.finalPrice").value(160_000L))
                .andExpect(jsonPath("$.promotion.id").value(promoId.toString()))
                .andExpect(jsonPath("$.changed").value(false));

        // Combinable and non-combinable VOUCHER preview alongside active AUTO promo (R1-18)
        UUID stackVoucherId = trackPromotion(Ids.newId());
        promotionRepository.saveAndFlush(Promotion.voucher(
                stackVoucherId, "Stack 10%", null, "PRVS" + shortToken(), DiscountType.PERCENT, 10, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS), null, null, true,
                PromotionScope.ALL, PromotionAudienceType.ALL, PromotionStatus.ACTIVE, now, null, null));
        mockMvc.perform(post(BASE + "/price-preview")
                        .with(adminAll())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("examId", examId, "promotionId", stackVoucherId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountApplied").value("COMBINED"))
                .andExpect(jsonPath("$.promotion.discountVnd").value(40_000L))
                .andExpect(jsonPath("$.voucher.discountVnd").value(16_000L))
                .andExpect(jsonPath("$.finalPrice").value(144_000L));

        UUID betterVoucherId = trackPromotion(Ids.newId());
        promotionRepository.saveAndFlush(Promotion.voucher(
                betterVoucherId, "Better 50k", null, "PRVB" + shortToken(), DiscountType.AMOUNT, 50_000L, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS), null, null, false,
                PromotionScope.ALL, PromotionAudienceType.ALL, PromotionStatus.ACTIVE, now, null, null));
        mockMvc.perform(post(BASE + "/price-preview")
                        .with(adminAll())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("examId", examId, "promotionId", betterVoucherId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountApplied").value("VOUCHER_ONLY"))
                .andExpect(jsonPath("$.voucher.discountVnd").value(50_000L))
                .andExpect(jsonPath("$.finalPrice").value(150_000L))
                .andExpect(jsonPath("$.voucherNotBetter").value(false));

        UUID worseVoucherId = trackPromotion(Ids.newId());
        promotionRepository.saveAndFlush(Promotion.voucher(
                worseVoucherId, "Worse 10k", null, "PRVW" + shortToken(), DiscountType.AMOUNT, 10_000L, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS), null, null, false,
                PromotionScope.ALL, PromotionAudienceType.ALL, PromotionStatus.ACTIVE, now, null, null));
        mockMvc.perform(post(BASE + "/price-preview")
                        .with(adminAll())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("examId", examId, "promotionId", worseVoucherId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountApplied").value("PROMOTION_ONLY"))
                .andExpect(jsonPath("$.voucher").isEmpty())
                .andExpect(jsonPath("$.voucherNotBetter").value(true))
                .andExpect(jsonPath("$.finalPrice").value(160_000L));

        // Out-of-scope AUTO promo preview yields NONE at full listPrice
        UUID qdaKindId = catalogLookup.findActiveItem("EXAM", "QDA").orElseThrow().id();
        UUID outOfScopeId = trackPromotion(Ids.newId());
        promotionRepository.saveAndFlush(Promotion.auto(
                outOfScopeId, "QDA Only", null, DiscountType.AMOUNT, 50_000L, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS), null,
                PromotionScope.EXAM_KIND, PromotionStatus.ACTIVE, now, null, null));
        jdbc.update("""
                INSERT INTO commerce.promotion_scope_item (id, promotion_id, exam_item_id)
                VALUES (?, ?, ?)
                """, Ids.newId(), outOfScopeId, qdaKindId);
        mockMvc.perform(post(BASE + "/price-preview")
                        .with(adminAll())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("examId", examId, "promotionId", outOfScopeId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountApplied").value("NONE"))
                .andExpect(jsonPath("$.finalPrice").value(200_000L));

        // Discontinued exam -> 410 PRODUCT_DISCONTINUED; unknown exam / promotion -> 404 (R1-18)
        ExamRow discontinued = insertProduct("HSA", "DISCONTINUED", 150_000L);
        mockMvc.perform(post(BASE + "/price-preview")
                        .with(adminAll())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "examId", discontinued.examId(), "promotionId", promoId))))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("PRODUCT_DISCONTINUED"))
                .andExpect(jsonPath("$.product.code").value(discontinued.code()))
                .andExpect(jsonPath("$.product.name").value(discontinued.name()));

        mockMvc.perform(post(BASE + "/price-preview")
                        .with(adminAll())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "examId", UUID.randomUUID(), "promotionId", promoId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));

        mockMvc.perform(post(BASE + "/price-preview")
                        .with(adminAll())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "examId", examId, "promotionId", UUID.randomUUID()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROMOTION_NOT_FOUND"));
    }

    @Test
    void writesAreAudited() throws Exception {
        UUID actor = trackActor();
        String code = "AUD" + shortToken();
        JsonNode created = createPromo(voucherPayload("Audited Voucher", code, 20_000L),
                "k-" + UUID.randomUUID(), adminAs(actor));
        UUID promoId = UUID.fromString(created.path("id").asText());
        long rev1 = created.path("revision").asLong();

        JsonNode patched = mapper.readTree(patchJson(promoId, Map.of("expectedRevision", rev1, "name", "Audited 2"),
                        adminAs(actor))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        long rev2 = patched.path("revision").asLong();

        JsonNode stopped = mapper.readTree(mockMvc.perform(post(BASE + "/" + promoId + "/stop")
                        .with(adminAs(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("expectedRevision", rev2))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        long rev3 = stopped.path("revision").asLong();

        mockMvc.perform(post(BASE + "/" + promoId + "/resume")
                        .with(adminAs(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("expectedRevision", rev3))))
                .andExpect(status().isOk());

        mockMvc.perform(delete(BASE + "/" + promoId).with(adminAs(actor)))
                .andExpect(status().isNoContent());

        drainOutbox();
        List<String> actions = jdbc.queryForList("""
                SELECT action FROM audit.audit_event
                WHERE module = 'COMMERCE' AND target_id = ? AND actor_id = ?
                ORDER BY occurred_at ASC
                """, String.class, promoId.toString(), actor);
        assertThat(actions).containsExactly(
                "PROMOTION_CREATED",
                "PROMOTION_UPDATED",
                "PROMOTION_STOPPED",
                "PROMOTION_RESUMED",
                "PROMOTION_DELETED");
    }

    @Test
    void permissionsAreSeparate() throws Exception {
        UUID actor = trackActor();
        UUID examId = insertOnSaleExam("HSA", 200_000L);
        Promotion promo = saveVoucher(10, 1);

        RequestPostProcessor viewOnly = asUser(actor, "viewer", Permissions.PROMOTION_VIEW);
        RequestPostProcessor noPromoPerms = asUser(actor, "other", Permissions.USER_VIEW);
        RequestPostProcessor modifyAndView = asUser(actor, "editor", Permissions.PROMOTION_VIEW, Permissions.PROMOTION_MODIFY);

        // Unauthenticated -> 401
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/" + promo.getId())).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/price-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("examId", examId, "promotionId", promo.getId()))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/suggested-code")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(voucherPayload("NoAuth", "NOA" + shortToken(), 10_000L))))
                .andExpect(status().isUnauthorized());

        // Missing PROMOTION.VIEW -> 403 on read endpoints
        mockMvc.perform(get(BASE).with(noPromoPerms)).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/" + promo.getId()).with(noPromoPerms)).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/price-preview")
                        .with(noPromoPerms)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("examId", examId, "promotionId", promo.getId()))))
                .andExpect(status().isForbidden());

        // PROMOTION.VIEW only -> 200 on read endpoints, 403 on all writes + suggested-code
        mockMvc.perform(get(BASE).with(viewOnly)).andExpect(status().isOk());
        mockMvc.perform(get(BASE + "/" + promo.getId()).with(viewOnly)).andExpect(status().isOk());
        mockMvc.perform(post(BASE + "/price-preview")
                        .with(viewOnly)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("examId", examId, "promotionId", promo.getId()))))
                .andExpect(status().isOk());

        mockMvc.perform(get(BASE + "/suggested-code").with(viewOnly)).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE)
                        .with(viewOnly)
                        .header("Idempotency-Key", "k-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(voucherPayload("NoMod", "NMD" + shortToken(), 10_000L))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(BASE + "/" + promo.getId())
                        .with(viewOnly)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("expectedRevision", promo.getRevision(), "name", "X"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/" + promo.getId() + "/stop")
                        .with(viewOnly)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("expectedRevision", promo.getRevision()))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/" + promo.getId() + "/resume")
                        .with(viewOnly)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("expectedRevision", promo.getRevision()))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(BASE + "/" + promo.getId()).with(viewOnly)).andExpect(status().isForbidden());

        // PROMOTION.MODIFY + PROMOTION.VIEW succeeds
        mockMvc.perform(get(BASE + "/suggested-code").with(modifyAndView)).andExpect(status().isOk());
        JsonNode created = createPromo(voucherPayload("Allowed Mod", "MOD" + shortToken(), 15_000L),
                "k-" + UUID.randomUUID(), modifyAndView);
        assertThat(created.path("id").asText()).isNotBlank();
    }

    @Test
    void crudScopeAudienceAndUnusedPatch() throws Exception {
        UUID actor = trackActor();
        UUID hsaKindId = catalogLookup.findActiveItem("EXAM", "HSA").orElseThrow().id();
        UUID mathSubjectId = catalogLookup.findActiveItem("SUBJECT", "TOAN").orElseThrow().id();
        UUID onSaleExamId = insertOnSaleExam("HSA", 200_000L);
        ExamRow discontinuedExam = insertProduct("HSA", "DISCONTINUED", 180_000L);
        UUID directLearner = createActiveLearner();
        UUID classLearner = createActiveLearner();
        UUID classId = createClassRoom();
        addClassMember(classId, classLearner);
        Instant now = Instant.now();

        // 1. EXAM_KIND scope: valid HSA item succeeds; non-EXAM or unknown item fails with 422 PROMOTION_SCOPE_INVALID
        Map<String, Object> validKindBody = voucherPayload("Kind Scope", "KND" + shortToken(), 15_000L);
        validKindBody.put("scope", "EXAM_KIND");
        validKindBody.put("examKindIds", List.of(hsaKindId));
        JsonNode kindCreated = createPromo(validKindBody, "k-" + UUID.randomUUID(), adminAs(actor));
        assertThat(kindCreated.at("/scopeItems/0/type").asText()).isEqualTo("EXAM_KIND");
        assertThat(kindCreated.at("/scopeItems/0/id").asText()).isEqualTo(hsaKindId.toString());
        assertThat(kindCreated.at("/scopeItems/0/code").asText()).isEqualTo("HSA");

        for (UUID badKindId : List.of(UUID.randomUUID(), mathSubjectId)) {
            Map<String, Object> badKindBody = voucherPayload("Bad Kind", "BKD" + shortToken(), 15_000L);
            badKindBody.put("scope", "EXAM_KIND");
            badKindBody.put("examKindIds", List.of(badKindId));
            mockMvc.perform(post(BASE)
                            .with(adminAs(actor))
                            .header("Idempotency-Key", "k-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(badKindBody)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("PROMOTION_SCOPE_INVALID"));
        }

        // 2. DISCONTINUED exam in EXAM scope is rejected with 422 PROMOTION_SCOPE_INVALID
        Map<String, Object> discontinuedScopeBody = voucherPayload("Discontinued Scope", "DSC" + shortToken(), 10_000L);
        discontinuedScopeBody.put("scope", "EXAM");
        discontinuedScopeBody.put("examIds", List.of(discontinuedExam.examId()));
        mockMvc.perform(post(BASE)
                        .with(adminAs(actor))
                        .header("Idempotency-Key", "k-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(discontinuedScopeBody)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PROMOTION_SCOPE_INVALID"));

        // 3. SELECTED audience: valid user & class resolved; unknown user -> 422; null item -> 400
        Map<String, Object> selectedBody = voucherPayload("Selected Aud", "SEL" + shortToken(), 20_000L);
        selectedBody.put("audience", "SELECTED");
        selectedBody.put("audienceClassIds", List.of(classId));
        selectedBody.put("audienceUserIds", List.of(directLearner));
        JsonNode selectedCreated = createPromo(selectedBody, "k-" + UUID.randomUUID(), adminAs(actor));
        assertThat(selectedCreated.at("/audienceClasses/0/id").asText()).isEqualTo(classId.toString());
        assertThat(selectedCreated.at("/audienceClasses/0/code").asText()).isNotBlank();
        assertThat(selectedCreated.at("/audienceUsers/0/id").asText()).isEqualTo(directLearner.toString());
        assertThat(selectedCreated.at("/audienceUsers/0/username").asText()).isNotBlank();

        Map<String, Object> unknownUserBody = voucherPayload("Unknown Aud", "UNK" + shortToken(), 20_000L);
        unknownUserBody.put("audience", "SELECTED");
        unknownUserBody.put("audienceUserIds", List.of(UUID.randomUUID()));
        mockMvc.perform(post(BASE)
                        .with(adminAs(actor))
                        .header("Idempotency-Key", "k-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(unknownUserBody)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PROMOTION_SCOPE_INVALID"));

        List<UUID> nullUserList = new ArrayList<>();
        nullUserList.add(null);
        Map<String, Object> nullUserBody = voucherPayload("Null Aud", "NUL" + shortToken(), 20_000L);
        nullUserBody.put("audience", "SELECTED");
        nullUserBody.put("audienceUserIds", nullUserList);
        mockMvc.perform(post(BASE)
                        .with(adminAs(actor))
                        .header("Idempotency-Key", "k-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(nullUserBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("audienceUserIds"));

        // 4. Unused-promotion PATCH: replace scope & audience, rename code, duplicate code -> 409, ended -> 409
        Promotion otherVoucher = createRandomVoucher(null, null);
        String originalCode = "UPD" + shortToken();
        JsonNode unusedCreated = createPromo(voucherPayload("Unused Mutate", originalCode, 25_000L),
                "k-" + UUID.randomUUID(), adminAs(actor));
        UUID unusedId = UUID.fromString(unusedCreated.path("id").asText());
        long rev1 = unusedCreated.path("revision").asLong();

        Map<String, Object> patchToKindAndSelected = new LinkedHashMap<>();
        patchToKindAndSelected.put("expectedRevision", rev1);
        patchToKindAndSelected.put("scope", "EXAM_KIND");
        patchToKindAndSelected.put("examKindIds", List.of(hsaKindId));
        patchToKindAndSelected.put("audience", "SELECTED");
        patchToKindAndSelected.put("audienceUserIds", List.of(directLearner));
        JsonNode patched1 = mapper.readTree(patchJson(unusedId, patchToKindAndSelected, adminAs(actor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("EXAM_KIND"))
                .andExpect(jsonPath("$.scopeItems[0].id").value(hsaKindId.toString()))
                .andExpect(jsonPath("$.audience").value("SELECTED"))
                .andExpect(jsonPath("$.audienceUsers[0].id").value(directLearner.toString()))
                .andReturn().getResponse().getContentAsString());
        long rev2 = patched1.path("revision").asLong();

        String renamedCode = "RNM" + shortToken();
        Map<String, Object> patchToExamAndAll = new LinkedHashMap<>();
        patchToExamAndAll.put("expectedRevision", rev2);
        patchToExamAndAll.put("code", renamedCode);
        patchToExamAndAll.put("scope", "EXAM");
        patchToExamAndAll.put("examIds", List.of(onSaleExamId));
        patchToExamAndAll.put("audience", "ALL");
        JsonNode patched2 = mapper.readTree(patchJson(unusedId, patchToExamAndAll, adminAs(actor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(renamedCode))
                .andExpect(jsonPath("$.scope").value("EXAM"))
                .andExpect(jsonPath("$.scopeItems[0].id").value(onSaleExamId.toString()))
                .andExpect(jsonPath("$.audience").value("ALL"))
                .andExpect(jsonPath("$.audienceUsers").isEmpty())
                .andReturn().getResponse().getContentAsString());
        long rev3 = patched2.path("revision").asLong();

        // clearDescription=true + non-null description -> 400 VALIDATION_FAILED (R1-31)
        patchJson(unusedId, Map.of(
                        "expectedRevision", rev3,
                        "clearDescription", true,
                        "description", "Conflicting text"), adminAs(actor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("description"));

        // Renaming voucher code to an already-taken code -> 409 VOUCHER_CODE_TAKEN
        patchJson(unusedId, Map.of(
                        "expectedRevision", rev3,
                        "code", otherVoucher.getCode().toLowerCase()), adminAs(actor))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VOUCHER_CODE_TAKEN"));

        // Re-activating ended stopped promotion via PATCH -> 409 PROMOTION_ENDED
        Promotion endedStopped = saveEndedStoppedVoucher();
        patchJson(endedStopped.getId(), Map.of(
                        "expectedRevision", endedStopped.getRevision(),
                        "status", "ACTIVE",
                        "endsAt", now.minusSeconds(30).toString()), adminAs(actor))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROMOTION_ENDED"));
    }

    // ------------------------------------------------------------------ helpers

    private int postCreateStatus(UUID actor, String code) throws Exception {
        MvcResult res = mockMvc.perform(post(BASE)
                        .with(adminAs(actor))
                        .header("Idempotency-Key", "k-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(voucherPayload("Race " + code, code, 20_000L))))
                .andReturn();
        return res.getResponse().getStatus();
    }

    private JsonNode createPromo(Map<String, Object> body, String idempotencyKey, RequestPostProcessor auth)
            throws Exception {
        MvcResult res = mockMvc.perform(post(BASE)
                        .with(auth)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
        trackPromotion(UUID.fromString(json.path("id").asText()));
        return json;
    }

    private org.springframework.test.web.servlet.ResultActions patchJson(
            UUID id, Map<String, Object> body, RequestPostProcessor auth) throws Exception {
        return mockMvc.perform(patch(BASE + "/" + id)
                .with(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)));
    }

    private Map<String, Object> voucherPayload(String name, String code, long discountValue) {
        Instant now = Instant.now();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("kind", "VOUCHER");
        map.put("name", name);
        map.put("code", code);
        map.put("discountType", "AMOUNT");
        map.put("discountValue", discountValue);
        map.put("startsAt", now.minus(1, ChronoUnit.HOURS).toString());
        map.put("endsAt", now.plus(2, ChronoUnit.DAYS).toString());
        map.put("scope", "ALL");
        map.put("audience", "ALL");
        map.put("combinable", false);
        return map;
    }

    private Promotion createRandomVoucher(Integer totalLimit, Integer perUserLimit) {
        Instant now = Instant.now();
        UUID id = trackPromotion(Ids.newId());
        String code = "VCH" + shortToken();
        return promotionRepository.saveAndFlush(Promotion.voucher(
                id, "Voucher " + code, "Test voucher", code, DiscountType.AMOUNT, 20_000L, null,
                now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS),
                totalLimit, perUserLimit, false, PromotionScope.ALL, PromotionAudienceType.ALL,
                PromotionStatus.ACTIVE, now, null, null));
    }

    private Promotion saveEndedStoppedVoucher() {
        Instant now = Instant.now();
        UUID id = trackPromotion(Ids.newId());
        String code = "END" + shortToken();
        return promotionRepository.saveAndFlush(Promotion.voucher(
                id, "Ended Stopped", null, code, DiscountType.AMOUNT, 10_000L, null,
                now.minus(3, ChronoUnit.DAYS), now.minus(1, ChronoUnit.HOURS),
                null, null, false, PromotionScope.ALL, PromotionAudienceType.ALL,
                PromotionStatus.STOPPED, now, null, null));
    }

    private long reloadRevision(UUID id) {
        return ((Number) promotionRow(id).get("revision")).longValue();
    }

    private RequestPostProcessor adminAll() {
        return asUser(Permissions.PROMOTION_VIEW, Permissions.PROMOTION_MODIFY);
    }

    private RequestPostProcessor adminAs(UUID actorId) {
        return asUser(actorId, "promo.admin." + actorId.toString().substring(0, 6),
                Permissions.PROMOTION_VIEW, Permissions.PROMOTION_MODIFY);
    }

    private UUID trackActor() {
        UUID actor = UUID.randomUUID();
        actorIds.add(actor);
        return actor;
    }

    private UUID insertOnSaleExam(String kindCode, long listPriceVnd) {
        return insertProduct(kindCode, "ON_SALE", listPriceVnd).examId();
    }

    private ExamRow insertProduct(String kindCode, String saleState, long listPriceVnd) {
        UUID examId = UUID.randomUUID();
        UUID kindId = catalogLookup.findActiveItem("EXAM", kindCode).orElseThrow().id();
        String code = "PA" + shortToken();
        String name = "Exam " + code;
        jdbc.update("""
                INSERT INTO commerce.storefront_product
                    (id, exam_id, code, code_normalized, version_no, sale_state, name, search_text, exam_item_id,
                     kind_code, scope, question_count, question_count_max, duration_minutes, duration_minutes_max,
                     build_mode, list_price_vnd, attempts_granted, card, detail, projection_hash, published_at, updated_at)
                VALUES (?, ?, ?, ?, 1, ?, ?, ?, ?, ?, 'FULL', 50, 50, 75, 75, 'STANDARD', ?, 3,
                        '{}'::jsonb, '{}'::jsonb, 'test', now(), now())
                """, UUID.randomUUID(), examId, code, code.toLowerCase(), saleState, name,
                name.toLowerCase(), kindId, kindCode, listPriceVnd);
        examIds.add(examId);
        return new ExamRow(examId, code, name);
    }

    private UUID createActiveLearner() {
        String token = shortToken().toLowerCase();
        return createActiveLearnerWithDetails("pau." + token, "Redemption Buyer " + token);
    }

    private UUID createActiveLearnerWithDetails(String username, String fullName) {
        UUID userId = UUID.randomUUID();
        Long seq = jdbc.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO identity.user_account
                    (id, code, username, username_normalized, full_name, email, email_normalized,
                     source, status, must_change_password, created_at, created_by, updated_at, updated_by, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, 2, false, ?, ?, ?, ?, 0)
                """, userId, String.format("U%06d", seq), username, username.toLowerCase(), fullName,
                userId + "@test.local", userId + "@test.local", now, userId, now, userId);
        userIds.add(userId);
        return userId;
    }

    private UUID createClassRoom() {
        UUID classId = UUID.randomUUID();
        String code = "PAC" + shortToken();
        jdbc.update("""
                INSERT INTO classroom.class_room
                    (id, code, code_normalized, name, status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 1, now(), now(), 0)
                """, classId, code, code.toLowerCase(), "Class " + code);
        classIds.add(classId);
        return classId;
    }

    private void addClassMember(UUID classId, UUID userId) {
        jdbc.update("""
                INSERT INTO classroom.class_member (class_id, user_id, joined_at, added_by)
                VALUES (?, ?, now(), ?)
                """, classId, userId, userId);
    }

    private record ExamRow(UUID examId, String code, String name) {
    }

    private void drainOutbox() {
        for (int i = 0; i < 50; i++) {
            if (outboxRelay.relayBatch(500) == 0) {
                return;
            }
        }
    }

    private static String shortToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
