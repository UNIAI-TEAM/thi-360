package vn.unicom.thi360.commerce.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.commerce.storefront.domain.SaleState;
import vn.unicom.thi360.exam.spi.ExamEventTopics;

/**
 * AC-12, AC-13, AC-30: Đồng bộ tự động bộ đề qua single {@code StorefrontEventHandler} khi bài thi thành phần
 * công bố bản mới / ngừng bán; cô lập lỗi handler bằng {@code REQUIRES_NEW} để không chặn outbox relay.
 */
class BundleEventIT extends AbstractBundleIT {

    @Test
    void togglesUnavailableAndRestoresOnSaleAndPropagatesExamMetadataWithoutChangingBundlePriceOrVersion()
            throws Exception {
        var auth = bundleAdmin();
        UUID hsaId = kindItem("HSA");
        BundleExamBuilder e1 = partExam("HSA", "TOAN").named("Toán V1").metrics(25, 40, 50);
        BundleExamBuilder e2 = subjectExam("HSA", "VAN").named("Văn V1").metrics(35, 50, 50);
        serve(e1);
        serve(e2);

        String pubCode = "EVT-PUB-" + shortToken();
        Map<String, Object> req = createPayload(pubCode, "Bộ đề Sự kiện", hsaId, List.of(e1.examId, e2.examId));
        req.put("listPriceVnd", 180_000L);
        req.put("attemptsPerExam", 5);
        JsonNode createdPub = createBundle(req, auth);
        UUID pubId = UUID.fromString(createdPub.path("id").asText());
        performPublish(pubId, createdPub.path("revision").asLong(), auth).andExpect(status().isOk());

        // Bộ đề nháp và bộ đề ngừng bán không bị sự kiện bài thi ghi/thay đổi
        JsonNode draft = createBundle(createPayload("EVT-DFT-" + shortToken(), "Draft", hsaId,
                List.of(e1.examId, e2.examId)), auth);
        UUID draftId = UUID.fromString(draft.path("id").asText());
        JsonNode disc = createBundle(createPayload("EVT-DSC-" + shortToken(), "Disc", hsaId,
                List.of(e1.examId, e2.examId)), auth);
        UUID discId = UUID.fromString(disc.path("id").asText());
        String discPubJson = performPublish(discId, disc.path("revision").asLong(), auth)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        performDiscontinue(discId, mapper.readTree(discPubJson).path("revision").asLong(), auth)
                .andExpect(status().isOk());

        // e1 ngừng bán -> bundle published thành UNAVAILABLE, bundle discontinued giữ nguyên DISCONTINUED, và e1 thành DISCONTINUED
        serve(e1.discontinued());
        emitExamEvent(ExamEventTopics.DISCONTINUED, e1.examId, 1);
        assertThat(saleStateOf(pubId)).isEqualTo("UNAVAILABLE");
        assertThat(saleStateOf(discId)).isEqualTo("DISCONTINUED");
        assertThat(examSaleStateOf(e1.examId)).isEqualTo("DISCONTINUED");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM commerce.storefront_product WHERE bundle_id = ?", Long.class, draftId)).isZero();

        // e1 công bố bản v2 mới -> bundle tự động mở bán lại (ON_SALE) và cập nhật metadata e1, giữ nguyên giá & version bộ; đồng thời e1 cập nhật lên v2 ON_SALE
        e1.status = "PUBLISHED";
        e1.nextVersion(2, "Toán V2 Mới", 30, 45, 60);
        serve(e1);
        emitExamEvent(ExamEventTopics.PUBLISHED, e1.examId, 2);

        assertThat(saleStateOf(pubId)).isEqualTo("ON_SALE");
        assertThat(examSaleStateOf(e1.examId)).isEqualTo("ON_SALE");
        assertThat(jdbc.queryForObject(
                "SELECT version_no FROM commerce.storefront_product WHERE exam_id = ?", Integer.class, e1.examId))
                .isEqualTo(2);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT version_no, list_price_vnd, attempts_granted, updated_at FROM commerce.storefront_product WHERE bundle_id = ?",
                pubId);
        assertThat(((Number) row.get("version_no")).intValue()).isEqualTo(1);
        assertThat(((Number) row.get("list_price_vnd")).longValue()).isEqualTo(180_000L);
        assertThat(((Number) row.get("attempts_granted")).intValue()).isEqualTo(5);

        mockMvc.perform(get(STOREFRONT_BASE + "/products/" + pubCode)
                        .header("X-Forwarded-For", newClientIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.listPrice").value(180_000))
                .andExpect(jsonPath("$.questionCount.min").value(65))
                .andExpect(jsonPath("$.items[0].name").value("Toán V2 Mới"))
                .andExpect(jsonPath("$.items[0].versionNo").value(2));

        // Replay sự kiện trùng -> projection_hash giữ nguyên updated_at không đổi
        Timestamp beforeReplay = (Timestamp) row.get("updated_at");
        emitExamEvent(ExamEventTopics.PUBLISHED, e1.examId, 2);
        Timestamp afterReplay = jdbc.queryForObject(
                "SELECT updated_at FROM commerce.storefront_product WHERE bundle_id = ?", Timestamp.class, pubId);
        assertThat(afterReplay).isEqualTo(beforeReplay);

        // Stale events (versionNo = 1 cũ hơn v2 hiện tại) không làm tụt version hay đổi UNAVAILABLE
        emitExamEvent(ExamEventTopics.DISCONTINUED, e1.examId, 1);
        emitExamEvent(ExamEventTopics.PUBLISHED, e1.examId, 1);
        assertThat(saleStateOf(pubId)).isEqualTo("ON_SALE");
        mockMvc.perform(get(STOREFRONT_BASE + "/products/" + pubCode)
                        .header("X-Forwarded-For", newClientIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].versionNo").value(2));
    }

    @Test
    void handlerFailureDoesNotBlockOutboxRelayAndRecoversOnRetry() throws Exception {
        var auth = bundleAdmin();
        UUID hsaId = kindItem("HSA");
        BundleExamBuilder eFail = partExam("HSA", "TOAN").named("Fail V1");
        BundleExamBuilder ePeer = subjectExam("HSA", "LY");
        BundleExamBuilder standaloneOk = fullExam("HSA", "TOAN").named("Standalone OK");
        serve(eFail);
        serve(ePeer);
        serve(standaloneOk);

        String code = "EVT-RETRY-" + shortToken();
        JsonNode created = createBundle(createPayload(code, "Retry Bundle", hsaId,
                List.of(eFail.examId, ePeer.examId)), auth);
        UUID bundleId = UUID.fromString(created.path("id").asText());
        performPublish(bundleId, created.path("revision").asLong(), auth).andExpect(status().isOk());

        // Cố tình gây lỗi port cho ePeer (bài thi anh em trong bộ) khi publish eFail v2 -> projection của eFail thành công, lỗi xảy ra trong BundleEventService.
        eFail.nextVersion(2, "Fail V2 Recovered", 50, 60, 100);
        serve(eFail);
        port.failFor(ePeer.examId);

        UUID failEventId = UUID.randomUUID();
        UUID okEventId = UUID.randomUUID();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
                outbox.publish(failEventId, ExamEventTopics.PUBLISHED, new ExamEventTopics.Payload(eFail.examId, 2));
                outbox.publish(okEventId, ExamEventTopics.PUBLISHED,
                        new ExamEventTopics.Payload(standaloneOk.examId, 1));
            });

            relay.relayBatch(500);

            // R1-12: storefront_product của chính eFail đã cập nhật lên v2, standaloneOk thành công,
            // còn eFail outbox event tăng attempts & chưa processed, và bundle vẫn ở v1.
            assertThat(jdbc.queryForObject(
                    "SELECT version_no FROM commerce.storefront_product WHERE exam_id = ?",
                    Integer.class, eFail.examId)).isEqualTo(2);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM commerce.storefront_product WHERE exam_id = ?",
                    Long.class, standaloneOk.examId)).isEqualTo(1L);
            Map<String, Object> failOutbox = jdbc.queryForMap(
                    "SELECT attempts, processed_at FROM shared.outbox_event WHERE id = ?", failEventId);
            assertThat(((Number) failOutbox.get("attempts")).intValue()).isGreaterThanOrEqualTo(1);
            assertThat(failOutbox.get("processed_at")).isNull();
            mockMvc.perform(get(STOREFRONT_BASE + "/products/" + code)
                            .header("X-Forwarded-For", newClientIp()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].versionNo").value(1))
                    .andExpect(jsonPath("$.items[0].name").value("Fail V1"));
        } finally {
            port.recover(ePeer.examId);
        }

        // Cho phép retry ngay và chạy lại relay
        jdbc.update("UPDATE shared.outbox_event SET next_attempt_at = now() - interval '1 second' WHERE id = ?",
                failEventId);
        drainOutbox();

        assertThat(jdbc.queryForObject(
                "SELECT processed_at FROM shared.outbox_event WHERE id = ?", Timestamp.class, failEventId))
                .isNotNull();
        mockMvc.perform(get(STOREFRONT_BASE + "/products/" + code)
                        .header("X-Forwarded-For", newClientIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].versionNo").value(2))
                .andExpect(jsonPath("$.items[0].name").value("Fail V2 Recovered"));
    }

    @Test
    void draftCodeEditOnPublishedBundleDoesNotLeakOnExamEventOrRebuildUntilRepublish() throws Exception {
        var auth = bundleAdmin();
        UUID hsaId = kindItem("HSA");
        BundleExamBuilder e1 = partExam("HSA", "TOAN").named("Toán V1");
        BundleExamBuilder e2 = subjectExam("HSA", "LY").named("Lý V1");
        serve(e1);
        serve(e2);

        String codeV1 = "EVT-C1-" + shortToken();
        String codeV2 = "EVT-C2-" + shortToken();
        JsonNode created = createBundle(
                createPayload(codeV1, "Bộ đề V1", hsaId, List.of(e1.examId, e2.examId)), auth);
        UUID bundleId = UUID.fromString(created.path("id").asText());
        String pub1Json = performPublish(bundleId, created.path("revision").asLong(), auth)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long revAfterPub1 = mapper.readTree(pub1Json).path("revision").asLong();

        // Sửa draft code sang codeV2 nhưng CHƯA publish lại
        Map<String, Object> draftEdit = createPayload(codeV2, "Bộ đề V2 Nháp", hsaId, List.of(e1.examId, e2.examId));
        draftEdit.put("expectedRevision", revAfterPub1);
        String updJson = performUpdate(bundleId, draftEdit, auth)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long revAfterDraftEdit = mapper.readTree(updJson).path("revision").asLong();

        // Bộ đề khác cố chiếm codeV1 đang phục vụ phải bị chặn 409 BUNDLE_CODE_TAKEN
        performCreate(createPayload(codeV1, "Steal C1", hsaId, List.of(e1.examId, e2.examId)), auth)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUNDLE_CODE_TAKEN"));

        // Bắn sự kiện ExamPublished v2 + chạy storefront rebuild -> storefront vẫn phục vụ codeV1, codeV2 vẫn 404
        e1.nextVersion(2, "Toán V2", 30, 45, 50);
        serve(e1);
        emitExamEvent(ExamEventTopics.PUBLISHED, e1.examId, 2);
        mockMvc.perform(post("/api/v1/admin/storefront/rebuild").with(auth))
                .andExpect(status().isOk());

        mockMvc.perform(get(STOREFRONT_BASE + "/products/" + codeV1)
                        .header("X-Forwarded-For", newClientIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(codeV1))
                .andExpect(jsonPath("$.name").value("Bộ đề V1"))
                .andExpect(jsonPath("$.items[0].name").value("Toán V2"));
        mockMvc.perform(get(STOREFRONT_BASE + "/products/" + codeV2)
                        .header("X-Forwarded-For", newClientIp()))
                .andExpect(status().isNotFound());

        // Publish v2 -> chuyển sang codeV2 trên storefront
        performPublish(bundleId, revAfterDraftEdit, auth).andExpect(status().isOk());
        mockMvc.perform(get(STOREFRONT_BASE + "/products/" + codeV2)
                        .header("X-Forwarded-For", newClientIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(codeV2))
                .andExpect(jsonPath("$.versionNo").value(2));
    }

    @Test
    void staleEventDoesNotDowngradeWhenLiveVersionIsStillBelowExistingItemVersion() throws Exception {
        var auth = bundleAdmin();
        UUID hsaId = kindItem("HSA");
        BundleExamBuilder e1 = partExam("HSA", "TOAN").named("Toán V3").version(3);
        BundleExamBuilder e2 = subjectExam("HSA", "LY").named("Lý V1");
        serve(e1);
        serve(e2);

        String code = "EVT-STALE-" + shortToken();
        JsonNode created = createBundle(createPayload(code, "Stale Guard", hsaId, List.of(e1.examId, e2.examId)), auth);
        UUID bundleId = UUID.fromString(created.path("id").asText());
        performPublish(bundleId, created.path("revision").asLong(), auth).andExpect(status().isOk());

        // Giả lập port trả về liveVer = 2 (< existingItemVersion = 3) và nhận sự kiện cũ ExamPublished(e1, 1)
        e1.nextVersion(2, "Toán V2 Cũ", 20, 30, 40);
        serve(e1);
        emitExamEvent(ExamEventTopics.PUBLISHED, e1.examId, 1);

        mockMvc.perform(get(STOREFRONT_BASE + "/products/" + code)
                        .header("X-Forwarded-For", newClientIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].versionNo").value(3))
                .andExpect(jsonPath("$.items[0].name").value("Toán V3"));
    }

    @Test
    void singleBundleFailureOnExamEventDoesNotRollBackHealthySiblingBundle() throws Exception {
        var auth = bundleAdmin();
        UUID hsaId = kindItem("HSA");
        BundleExamBuilder eShared = partExam("HSA", "TOAN");
        BundleExamBuilder eGood = subjectExam("HSA", "LY");
        BundleExamBuilder eBad = subjectExam("HSA", "HOA");
        serve(eShared);
        serve(eGood);
        serve(eBad);

        JsonNode b1Created = createBundle(createPayload("SIB-OK-" + shortToken(), "Sibling OK", hsaId,
                List.of(eShared.examId, eGood.examId)), auth);
        UUID b1Id = UUID.fromString(b1Created.path("id").asText());
        performPublish(b1Id, b1Created.path("revision").asLong(), auth).andExpect(status().isOk());

        JsonNode b2Created = createBundle(createPayload("SIB-ERR-" + shortToken(), "Sibling Err", hsaId,
                List.of(eShared.examId, eBad.examId)), auth);
        UUID b2Id = UUID.fromString(b2Created.path("id").asText());
        performPublish(b2Id, b2Created.path("revision").asLong(), auth).andExpect(status().isOk());

        eShared.discontinued();
        serve(eShared);
        port.failFor(eBad.examId);
        UUID eventId = UUID.randomUUID();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(
                    s -> outbox.publish(eventId, ExamEventTopics.DISCONTINUED,
                            new ExamEventTopics.Payload(eShared.examId, 1)));
            relay.relayBatch(500);

            // R1-26: b1 khỏe mạnh phải lập tức chuyển UNAVAILABLE, trong khi b2 lỗi giữ ON_SALE & đợi retry.
            assertThat(saleStateOf(b1Id)).isEqualTo(SaleState.UNAVAILABLE.name());
            assertThat(saleStateOf(b2Id)).isEqualTo(SaleState.ON_SALE.name());
            Map<String, Object> outboxRow = jdbc.queryForMap(
                    "SELECT attempts, processed_at FROM shared.outbox_event WHERE id = ?", eventId);
            assertThat(((Number) outboxRow.get("attempts")).intValue()).isGreaterThanOrEqualTo(1);
            assertThat(outboxRow.get("processed_at")).isNull();
        } finally {
            port.recover(eBad.examId);
        }

        jdbc.update("UPDATE shared.outbox_event SET next_attempt_at = now() - interval '1 second' WHERE id = ?",
                eventId);
        drainOutbox();

        assertThat(jdbc.queryForObject(
                "SELECT processed_at FROM shared.outbox_event WHERE id = ?", Timestamp.class, eventId)).isNotNull();
        assertThat(saleStateOf(b2Id)).isEqualTo(SaleState.UNAVAILABLE.name());
    }

    private String saleStateOf(UUID bundleId) {
        return jdbc.queryForObject(
                "SELECT sale_state FROM commerce.storefront_product WHERE bundle_id = ?", String.class, bundleId);
    }

    private String examSaleStateOf(UUID examId) {
        return jdbc.queryForObject(
                "SELECT sale_state FROM commerce.storefront_product WHERE exam_id = ?", String.class, examId);
    }
}
