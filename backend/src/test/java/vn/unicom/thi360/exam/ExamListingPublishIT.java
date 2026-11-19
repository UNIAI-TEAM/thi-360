package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vn.unicom.thi360.identity.spi.Permissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.test.context.TestPropertySource;

/** EX-1 AC-6 to AC-8, AC-10, AC-13: the listing rules at publish, and the snapshot of the listing. */
// The 4.999 / 5.000 boundary below does not depend on the shell's payment threshold (THI360_VNPAY_MIN_AMOUNT).
@TestPropertySource(properties = "thi360.exam.listing.min-price-vnd=5000")
class ExamListingPublishIT extends AbstractExamPublishIT {
    @Autowired PlatformTransactionManager transactionManager;
    private static final String PLAIN = "[{\"id\":\"part\",\"options\":[{\"id\":\"choice\",\"countsByPick\":[17,17,16],\"sections\":[]}]}]";

    private UUID publishable() {
        UUID exam = newExam("HSA", PLAIN);
        saveCheck(exam, paths("default=VALID"));
        return exam;
    }

    private long versions(UUID exam) {
        return jdbc.queryForObject("SELECT count(*) FROM exam.exam_version WHERE exam_id = ?", Long.class, exam);
    }

    private long publishedEvents(UUID exam) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT count(*) FROM shared.outbox_event WHERE topic = 'exam.published' "
                + "AND payload->>'examId' = ?", Long.class, exam.toString());
    }

    private JsonNode saveListing(UUID exam, String fields) throws Exception {
        entityManager.flush();
        entityManager.clear();
        long revision = revision(exam);
        return mapper.readTree(mockMvc.perform(put("/api/v1/admin/exams/" + exam + "/listing").with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content("{" + fields + ",\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void listingOnlyEditKeepsConfigCheckValid() throws Exception {
        UUID exam = publishable();
        publish(exam, "default").andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        entityManager.clear();
        String before = jdbc.queryForObject("SELECT result::text FROM exam.exam_check_run WHERE exam_id = ?", String.class, exam);
        saveListing(exam, "\"summary\":\"Moi\",\"listPriceVnd\":90000,\"attemptsGranted\":5,\"description\":\"Mo ta moi\"");
        JsonNode check = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/" + exam + "/check").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(check.path("stale").asBoolean()).isFalse();
        assertThat(check.path("status").asText()).isEqualTo("VALID");
        assertThat(jdbc.queryForObject("SELECT result::text FROM exam.exam_check_run WHERE exam_id = ?", String.class, exam))
                .isEqualTo(before);
        // The edit alone is publishable: no new check ran between the two publishes.
        publish(exam, "default").andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        assertThat(jdbc.queryForObject("SELECT (config->'listing'->>'listPriceVnd')::bigint FROM exam.exam_version "
                + "WHERE exam_id = ? AND version_no = 2", Long.class, exam)).isEqualTo(90000);
    }

    @Test
    void removedPriceBlocksPublish() throws Exception {
        UUID exam = publishable();
        saveListing(exam, "\"summary\":\"Khong gia\",\"attemptsGranted\":3,\"description\":\"Mo ta\"");
        long events = publishedEvents(exam);
        publish(exam, "default").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LISTING_PRICE_MISSING"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("listPriceVnd"));
        assertThat(versions(exam)).isZero();
        assertThat(publishedEvents(exam)).isEqualTo(events);
        assertThat(jdbc.queryForObject("SELECT status FROM exam.exam WHERE id = ?", String.class, exam)).isEqualTo("DRAFT");
    }

    @Test
    void eachListingRuleBlocksPublish() throws Exception {
        record Rule(String change, String code, String field) { }
        for (Rule rule : List.of(
                new Rule("list_price_vnd = 0", "LISTING_PRICE_ZERO", "listPriceVnd"),
                new Rule("list_price_vnd = 4999", "LISTING_PRICE_BELOW_MIN", "listPriceVnd"),
                new Rule("attempts_granted = NULL", "LISTING_ATTEMPTS_MISSING", "attemptsGranted"))) {
            UUID exam = publishable();
            jdbc.update("UPDATE exam.exam_listing SET " + rule.change() + " WHERE exam_id = ?", exam);
            publish(exam, "default").andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value(rule.code()))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value(rule.field()));
            assertThat(versions(exam)).as(rule.code()).isZero();
        }
        UUID exam = publishable();
        jdbc.update("UPDATE exam.exam SET description = '   ' WHERE id = ?", exam);
        publish(exam, "default").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LISTING_DESCRIPTION_MISSING"));
        assertThat(versions(exam)).isZero();
        // Several problems are all reported; the code is the first one.
        UUID several = publishable();
        jdbc.update("UPDATE exam.exam_listing SET list_price_vnd = NULL, attempts_granted = NULL WHERE exam_id = ?", several);
        publish(several, "default").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LISTING_PRICE_MISSING"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
        // The boundary is inclusive: 5.000 publishes.
        UUID boundary = publishable();
        jdbc.update("UPDATE exam.exam_listing SET list_price_vnd = 5000 WHERE exam_id = ?", boundary);
        publish(boundary, "default").andExpect(status().isOk());
    }

    @Test
    void missingCoverOnlyWarns() throws Exception {
        UUID exam = publishable();
        assertThat(jdbc.queryForObject("SELECT cover_asset_id FROM exam.exam WHERE id = ?", UUID.class, exam)).isNull();
        mockMvc.perform(get("/api/v1/admin/exams/" + exam + "/listing").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.checkIssues[0].code").value("LISTING_COVER_MISSING"))
                .andExpect(jsonPath("$.checkIssues[0].severity").value("WARNING"));
        publish(exam, "default").andExpect(status().isOk());
        assertThat(versions(exam)).isEqualTo(1);
    }

    @Test
    void existingRefusalsKeepTheirCodes() throws Exception {
        // AC-10: the refusals that existed before EX-1 keep their codes whatever the listing looks like (no listing row
        // and no description at all). Nothing here depends on the listing feature: it is green on the base as well.
        UUID invalid = newExam("HSA", PLAIN);
        jdbc.update("DELETE FROM exam.exam_listing WHERE exam_id = ?", invalid);
        jdbc.update("UPDATE exam.exam SET description = NULL WHERE id = ?", invalid);
        saveCheck(invalid, paths("default=INVALID"));
        publish(invalid, "default").andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("PATH_NOT_VALID"));
        UUID unchecked = newExam("HSA", PLAIN);
        jdbc.update("DELETE FROM exam.exam_listing WHERE exam_id = ?", unchecked);
        jdbc.update("UPDATE exam.exam SET description = NULL WHERE id = ?", unchecked);
        publish(unchecked, "default").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CHECK_REQUIRED"));
    }

    /** The new rule of the feature: a valid check and no listing row is refused by the listing rule, and only by it. */
    @Test
    void examWithoutListingIsRefusedByTheListingRuleOnly() throws Exception {
        UUID legacy = newExam("HSA", PLAIN);
        jdbc.update("DELETE FROM exam.exam_listing WHERE exam_id = ?", legacy);
        saveCheck(legacy, paths("default=VALID"));
        publish(legacy, "default").andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("LISTING_PRICE_MISSING"));
        assertThat(versions(legacy)).isZero();
    }

    @Test
    void publishSnapshotsListingImmutably() throws Exception {
        UUID exam = publishable();
        UUID cover = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam_cover_asset (id, exam_id, object_key, content_type, size_bytes, width, height) "
                + "VALUES (?, ?, ?, 'image/png', 10, 600, 600)", cover, exam, "exam-covers/" + cover);
        saveListing(exam, "\"summary\":\"Tom tat\",\"objectives\":\"Muc tieu\",\"audience\":\"Doi tuong\",\"listPriceVnd\":150000,"
                + "\"attemptsGranted\":4,\"benefitAiExplanation\":true,\"benefitStrengthWeakness\":true,\"benefitPractice\":false,"
                + "\"benefitNote\":\"Ghi chu\",\"resultView\":\"FULL_SOLUTION\",\"description\":\"Mo ta v1\",\"coverAssetId\":\"" + cover + "\"");
        publish(exam, "default").andExpect(status().isOk());
        JsonNode frozen = mapper.readTree(jdbc.queryForObject("SELECT config->'listing' FROM exam.exam_version WHERE exam_id = ? AND version_no = 1",
                String.class, exam));
        assertThat(frozen.path("summary").asText()).isEqualTo("Tom tat");
        assertThat(frozen.path("objectives").asText()).isEqualTo("Muc tieu");
        assertThat(frozen.path("audience").asText()).isEqualTo("Doi tuong");
        assertThat(frozen.path("listPriceVnd").asLong()).isEqualTo(150000);
        assertThat(frozen.path("attemptsGranted").asInt()).isEqualTo(4);
        assertThat(frozen.path("benefitAiExplanation").asBoolean()).isTrue();
        assertThat(frozen.path("benefitStrengthWeakness").asBoolean()).isTrue();
        assertThat(frozen.path("benefitPractice").asBoolean()).isFalse();
        assertThat(frozen.path("benefitNote").asText()).isEqualTo("Ghi chu");
        assertThat(frozen.path("resultView").asText()).isEqualTo("FULL_SOLUTION");
        assertThat(frozen.path("name").asText()).isEqualTo("E24 test");
        assertThat(frozen.path("description").asText()).isEqualTo("Mo ta v1");
        assertThat(frozen.path("coverAssetId").asText()).isEqualTo(cover.toString());
        // Every listing column is frozen, the metadata included (R1-7): who saved it last and when.
        assertThat(frozen.path("examId").asText()).isEqualTo(exam.toString());
        UUID savedBy = jdbc.queryForObject("SELECT updated_by FROM exam.exam_listing WHERE exam_id = ?", UUID.class, exam);
        Instant savedAt = jdbc.queryForObject("SELECT updated_at FROM exam.exam_listing WHERE exam_id = ?", java.sql.Timestamp.class, exam).toInstant();
        assertThat(savedBy).isNotNull();
        assertThat(frozen.path("updatedBy").asText()).isEqualTo(savedBy.toString());
        assertThat(OffsetDateTime.parse(frozen.path("updatedAt").asText()).toInstant()).isEqualTo(savedAt);
        // Editing later changes the working copy only; `published` follows the latest version that still serves a path.
        saveListing(exam, "\"summary\":\"Tom tat moi\",\"listPriceVnd\":99000,\"attemptsGranted\":2,\"description\":\"Mo ta v2\"");
        assertThat(mapper.readTree(jdbc.queryForObject("SELECT config->'listing' FROM exam.exam_version WHERE exam_id = ? AND version_no = 1",
                String.class, exam))).isEqualTo(frozen);
        assertThat(jdbc.queryForObject("SELECT updated_at FROM exam.exam_listing WHERE exam_id = ?", java.sql.Timestamp.class, exam).toInstant())
                .as("the later save moved the working metadata, not the frozen one").isAfter(savedAt);
        JsonNode listing = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/" + exam + "/listing").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(listing.path("listPriceVnd").asLong()).isEqualTo(99000);
        assertThat(listing.path("published").path("versionNo").asInt()).isEqualTo(1);
        assertThat(listing.path("published").path("listing").path("listPriceVnd").asLong()).isEqualTo(150000);
        assertThat(listing.path("published").path("listing").path("description").asText()).isEqualTo("Mo ta v1");
        publish(exam, "default").andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        listing = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/" + exam + "/listing").with(asUser(Permissions.EXAM_VIEW)))
                .andReturn().getResponse().getContentAsString());
        assertThat(listing.path("published").path("versionNo").asInt()).isEqualTo(2);
        assertThat(listing.path("published").path("listing").path("listPriceVnd").asLong()).isEqualTo(99000);
    }

    /** R1-7: who saved the listing and when is frozen but is no sale term: a save that changes nothing else is nothing to publish. */
    @Test
    void metadataOnlyDifferenceIsNotASaleChange() throws Exception {
        UUID exam = publishable();
        publish(exam, "default").andExpect(status().isOk());
        jdbc.update("UPDATE exam.exam_listing SET updated_by = ?, updated_at = updated_at + interval '3 hours' WHERE exam_id = ?",
                UUID.randomUUID(), exam);
        jdbc.update("UPDATE exam.exam SET has_unpublished_changes = true WHERE id = ?", exam);
        entityManager.clear();
        mockMvc.perform(get("/api/v1/admin/exams/" + exam + "/versions").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.changes").isEmpty())
                .andExpect(jsonPath("$.workingCopy.unpublishedPaths").isEmpty());
        publish(exam, "default").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOTHING_TO_PUBLISH"));
        assertThat(versions(exam)).isEqualTo(1);
        // A real sale change next to the same metadata is still a change.
        jdbc.update("UPDATE exam.exam_listing SET list_price_vnd = list_price_vnd + 1000 WHERE exam_id = ?", exam);
        entityManager.clear();
        saveCheck(exam, paths("default=VALID"));
        publish(exam, "default").andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
    }

    /**
     * A price edit and a publish started at the same moment queue on the exam row lock: whichever runs second finds a
     * new revision and gets 412. So no version can snapshot a price that has been removed. Runs on committed rows.
     */
    @Test
    void priceEditDuringPublishSerializes() throws Exception {
        TransactionTemplate committed = new TransactionTemplate(transactionManager);
        committed.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        UUID exam = committed.execute(status -> publishable());
        UUID publisher = UUID.randomUUID();
        try {
            long revision = jdbc.queryForObject("SELECT revision FROM exam.exam WHERE id = ?", Long.class, exam);
            CountDownLatch locked = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> committed.executeWithoutResult(status -> {
                jdbc.queryForList("SELECT id FROM exam.exam WHERE id = ? FOR UPDATE", UUID.class, exam);
                locked.countDown();
                try {
                    release.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<Integer> edit = CompletableFuture.supplyAsync(() -> {
                try {
                    return mockMvc.perform(put("/api/v1/admin/exams/" + exam + "/listing").with(asUser(Permissions.EXAM_MODIFY))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"summary\":\"No price\",\"attemptsGranted\":3,\"description\":\"Mo ta\",\"expectedRevision\":" + revision + "}"))
                            .andReturn().getResponse().getStatus();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            CompletableFuture<Integer> publication = CompletableFuture.supplyAsync(() -> {
                try {
                    return mockMvc.perform(post("/api/v1/admin/exams/" + exam + "/publish").with(asUser(publisher, "publisher", Permissions.EXAM_PUBLISH))
                            .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"pathIds\":[\"default\"],\"expectedRevision\":" + revision + "}"))
                            .andReturn().getResponse().getStatus();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            Thread.sleep(500);
            release.countDown();
            holder.get(30, TimeUnit.SECONDS);
            List<Integer> statuses = List.of(edit.get(30, TimeUnit.SECONDS), publication.get(30, TimeUnit.SECONDS));
            assertThat(statuses).as("one command wins, the other sees the new revision").containsExactlyInAnyOrder(200, 412);
            // Whichever order, no version holds a missing price.
            for (String price : jdbc.queryForList("SELECT COALESCE(config->'listing'->>'listPriceVnd', 'MISSING') "
                    + "FROM exam.exam_version WHERE exam_id = ?", String.class, exam)) {
                assertThat(price).isNotEqualTo("MISSING");
            }
            if (statuses.get(1) == 200) {
                assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_version WHERE exam_id = ?", Long.class, exam)).isEqualTo(1);
            } else {
                assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_version WHERE exam_id = ?", Long.class, exam)).isZero();
            }
        } finally {
            committed.executeWithoutResult(status -> {
                for (String table : List.of("questionbank.question_usage_ledger", "exam.exam_block_ref", "exam.exam_version",
                        "exam.exam_check_run", "exam.exam_structure", "exam.exam_listing")) {
                    jdbc.update("DELETE FROM " + table + " WHERE exam_id = ?", exam);
                }
                jdbc.update("DELETE FROM shared.outbox_event WHERE payload->>'examId' = ? OR payload->>'targetId' = ?", exam.toString(), exam.toString());
                jdbc.update("DELETE FROM exam.exam WHERE id = ?", exam);
            });
        }
    }
}
