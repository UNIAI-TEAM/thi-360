package vn.unicom.thi360.commerce.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * AC-7, AC-8, AC-14: Kiểm tra điều kiện công bố, lưu snapshot {@code bundle_version}, đẩy storefront,
 * công bố lại tăng version, và ngừng bán ({@code DISCONTINUED} -> 410).
 */
class BundlePublishIT extends AbstractBundleIT {

    @Test
    void validatesPublishRulesAtomicallyWithoutCreatingVersionRowsOnFailure() throws Exception {
        var auth = bundleAdmin();
        UUID hsaId = kindItem("HSA");
        BundleExamBuilder e1 = partExam("HSA", "TOAN");
        BundleExamBuilder e2 = subjectExam("HSA", "LY");
        serve(e1);
        serve(e2);
        long minAmount = pricingProperties.vnpayMinAmount();

        // Thiếu giá -> LISTING_PRICE_MISSING, không tạo bundle_version
        Map<String, Object> missingPrice = createPayload("PUB-MP-" + shortToken(), "Missing Price",
                hsaId, List.of(e1.examId, e2.examId));
        missingPrice.put("listPriceVnd", null);
        JsonNode created = createBundle(missingPrice, auth);
        UUID bundleId = UUID.fromString(created.path("id").asText());
        long rev = created.path("revision").asLong();

        performPublish(bundleId, rev, auth)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LISTING_PRICE_MISSING"));
        assertThat(versionCount(bundleId)).isZero();

        // Giá = 0 -> LISTING_PRICE_ZERO
        Map<String, Object> zeroPrice = createPayload(created.path("code").asText(), "Zero Price",
                hsaId, List.of(e1.examId, e2.examId));
        zeroPrice.put("expectedRevision", rev);
        zeroPrice.put("listPriceVnd", 0L);
        rev = mapper.readTree(performUpdate(bundleId, zeroPrice, auth).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("revision").asLong();
        performPublish(bundleId, rev, auth)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LISTING_PRICE_ZERO"));
        assertThat(versionCount(bundleId)).isZero();

        // Giá = minAmount - 1 -> LISTING_PRICE_BELOW_MIN
        Map<String, Object> belowMin = createPayload(created.path("code").asText(), "Below Min",
                hsaId, List.of(e1.examId, e2.examId));
        belowMin.put("expectedRevision", rev);
        belowMin.put("listPriceVnd", minAmount - 1);
        rev = mapper.readTree(performUpdate(bundleId, belowMin, auth).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("revision").asLong();
        performPublish(bundleId, rev, auth)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LISTING_PRICE_BELOW_MIN"));
        assertThat(versionCount(bundleId)).isZero();

        // Thiếu description -> BUNDLE_DESCRIPTION_REQUIRED
        Map<String, Object> noDesc = createPayload(created.path("code").asText(), "No Desc",
                hsaId, List.of(e1.examId, e2.examId));
        noDesc.put("expectedRevision", rev);
        noDesc.put("listPriceVnd", minAmount);
        noDesc.put("description", "   ");
        rev = mapper.readTree(performUpdate(bundleId, noDesc, auth).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("revision").asLong();
        performPublish(bundleId, rev, auth)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUNDLE_DESCRIPTION_REQUIRED"));
        assertThat(versionCount(bundleId)).isZero();

        // Thiếu attemptsPerExam -> BUNDLE_ATTEMPTS_INVALID
        Map<String, Object> noAtt = createPayload(created.path("code").asText(), "No Attempts",
                hsaId, List.of(e1.examId, e2.examId));
        noAtt.put("expectedRevision", rev);
        noAtt.put("listPriceVnd", minAmount);
        noAtt.put("attemptsPerExam", null);
        rev = mapper.readTree(performUpdate(bundleId, noAtt, auth).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("revision").asLong();
        performPublish(bundleId, rev, auth)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUNDLE_ATTEMPTS_INVALID"));
        assertThat(versionCount(bundleId)).isZero();

        // Đủ điều kiện, nhưng 1 bài thi thành phần bị discontinued trước khi publish -> 422 BUNDLE_ITEM_NOT_ON_SALE & 0 version
        Map<String, Object> validDraft = createPayload(created.path("code").asText(), "Ready Draft",
                hsaId, List.of(e1.examId, e2.examId));
        validDraft.put("expectedRevision", rev);
        validDraft.put("listPriceVnd", minAmount);
        rev = mapper.readTree(performUpdate(bundleId, validDraft, auth).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("revision").asLong();

        serve(e2.discontinued());
        performPublish(bundleId, rev, auth)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUNDLE_ITEM_NOT_ON_SALE"));
        assertThat(versionCount(bundleId)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM commerce.bundle WHERE id = ?", String.class, bundleId))
                .isEqualTo("DRAFT");
    }

    @Test
    void publishesSnapshotProjectsStorefrontRepublishesAndDiscontinues() throws Exception {
        UUID actor = trackActor();
        var auth = bundleAdminAs(actor);
        UUID hsaId = kindItem("HSA");
        BundleExamBuilder e1 = partExam("HSA", "TOAN").named("Đề Toán").metrics(30, 45, 50);
        BundleExamBuilder e2 = subjectExam("HSA", "ANH").named("Đề Anh").metrics(40, 50, 50);
        serve(e1);
        serve(e2);
        long minAmount = pricingProperties.vnpayMinAmount();

        String code = "BD-PUB-" + shortToken();
        Map<String, Object> createReq = createPayload(code, "Bộ đề Công bố", hsaId, List.of(e1.examId, e2.examId));
        createReq.put("listPriceVnd", minAmount);
        JsonNode created = createBundle(createReq, auth);
        UUID bundleId = UUID.fromString(created.path("id").asText());
        long rev0 = created.path("revision").asLong();

        // Discontinue khi còn DRAFT -> 409 BUNDLE_NOT_PUBLISHED
        performDiscontinue(bundleId, rev0, auth)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUNDLE_NOT_PUBLISHED"));

        // Publish lần 1 với expectedRevision sai -> 412
        performPublish(bundleId, rev0 + 5, auth)
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        // Publish lần 1 thành công
        String pub1Json = performPublish(bundleId, rev0, auth)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.saleState").value("ON_SALE"))
                .andExpect(jsonPath("$.servingVersion").value(1))
                .andExpect(jsonPath("$.hasUnpublishedChanges").value(false))
                .andReturn().getResponse().getContentAsString();
        long rev1 = mapper.readTree(pub1Json).path("revision").asLong();

        Map<String, Object> sf = jdbc.queryForMap(
                "SELECT product_type, scope, bundle_id, exam_id, sale_state, version_no, list_price_vnd, attempts_granted "
                        + "FROM commerce.storefront_product WHERE bundle_id = ?",
                bundleId);
        assertThat(sf.get("product_type")).isEqualTo("BUNDLE");
        assertThat(sf.get("scope")).isEqualTo("BUNDLE");
        assertThat(sf.get("bundle_id")).isEqualTo(bundleId);
        assertThat(sf.get("exam_id")).isNull();
        assertThat(sf.get("sale_state")).isEqualTo("ON_SALE");
        assertThat(((Number) sf.get("version_no")).intValue()).isEqualTo(1);
        assertThat(((Number) sf.get("list_price_vnd")).longValue()).isEqualTo(minAmount);

        String snapshotJson = jdbc.queryForObject(
                "SELECT snapshot::text FROM commerce.bundle_version WHERE bundle_id = ? AND version_no = 1",
                String.class, bundleId);
        JsonNode snap = mapper.readTree(snapshotJson);
        assertThat(snap.path("name").asText()).isEqualTo("Bộ đề Công bố");
        assertThat(snap.path("listPriceVnd").asLong()).isEqualTo(minAmount);
        assertThat(snap.path("attemptsPerExam").asInt()).isEqualTo(3);
        assertThat(snap.path("items").size()).isEqualTo(2);
        assertThat(snap.path("items").get(0).path("examId").asText()).isEqualTo(e1.examId.toString());

        // Sửa sau khi publish -> hasUnpublishedChanges = true, rồi publish lại lên version 2
        Map<String, Object> updateReq = createPayload(code, "Bộ đề V2", hsaId, List.of(e2.examId, e1.examId));
        updateReq.put("expectedRevision", rev1);
        updateReq.put("listPriceVnd", 199_000L);
        String updJson = performUpdate(bundleId, updateReq, auth)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasUnpublishedChanges").value(true))
                .andExpect(jsonPath("$.servingVersion").value(1))
                .andReturn().getResponse().getContentAsString();
        long rev2 = mapper.readTree(updJson).path("revision").asLong();

        String pub2Json = performPublish(bundleId, rev2, auth)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servingVersion").value(2))
                .andExpect(jsonPath("$.hasUnpublishedChanges").value(false))
                .andReturn().getResponse().getContentAsString();
        long rev3 = mapper.readTree(pub2Json).path("revision").asLong();
        assertThat(versionCount(bundleId)).isEqualTo(2L);

        // Stale revision khi discontinue -> 412
        performDiscontinue(bundleId, rev2, auth)
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        // Discontinue hợp lệ -> DISCONTINUED, public API trả 410 PRODUCT_DISCONTINUED
        performDiscontinue(bundleId, rev3, auth)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISCONTINUED"))
                .andExpect(jsonPath("$.saleState").value("DISCONTINUED"));

        mockMvc.perform(get(STOREFRONT_BASE + "/products/" + code)
                        .header("X-Forwarded-For", newClientIp()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("PRODUCT_DISCONTINUED"));

        // Không thể update/publish/discontinue lại sau khi DISCONTINUED
        performDiscontinue(bundleId, rev3 + 1, auth)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUNDLE_ALREADY_DISCONTINUED"));
        performPublish(bundleId, rev3 + 1, auth)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUNDLE_ALREADY_DISCONTINUED"));
        Map<String, Object> blockedEdit = createPayload(code, "Edit Stopped", hsaId, List.of(e1.examId, e2.examId));
        blockedEdit.put("expectedRevision", rev3 + 1);
        performUpdate(bundleId, blockedEdit, auth)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUNDLE_ALREADY_DISCONTINUED"));

        drainOutbox();
        List<String> actions = jdbc.queryForList("""
                SELECT action FROM audit.audit_event
                WHERE module = 'COMMERCE' AND target_id = ?
                ORDER BY occurred_at ASC
                """, String.class, bundleId.toString());
        assertThat(actions).containsExactly(
                "BUNDLE_CREATED",
                "BUNDLE_PUBLISHED",
                "BUNDLE_UPDATED",
                "BUNDLE_PUBLISHED",
                "BUNDLE_DISCONTINUED");
    }

    private long versionCount(UUID bundleId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM commerce.bundle_version WHERE bundle_id = ?",
                Long.class, bundleId);
    }
}
