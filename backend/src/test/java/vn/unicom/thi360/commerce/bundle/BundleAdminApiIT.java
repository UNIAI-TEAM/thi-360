package vn.unicom.thi360.commerce.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * AC-2, AC-6: CRUD bộ đề, expectedRevision (412), audit events, và API lấy danh sách bài thi ứng viên.
 */
class BundleAdminApiIT extends AbstractBundleIT {

    @Test
    void crudRoundTripUpdatesWorkingCopyChecksRevisionAndWritesAuditEvents() throws Exception {
        UUID actor = trackActor();
        var auth = bundleAdminAs(actor);

        BundleExamBuilder e1 = partExam("HSA", "TOAN").metrics(20, 30, 40);
        BundleExamBuilder e2 = subjectExam("HSA", "VAN").metrics(35, 50, 60);
        BundleExamBuilder e3 = partExam("HSA", "ANH").metrics(25, 35, 30);
        serve(e1);
        serve(e2);
        serve(e3);
        UUID hsaId = kindItem("HSA");

        String code = "BD-CRUD-" + shortToken();
        JsonNode created = createBundle(
                createPayload(code, "Bộ đề ĐGNL Số 1", hsaId, List.of(e1.examId, e2.examId)), auth);
        UUID bundleId = UUID.fromString(created.path("id").asText());
        assertThat(created.path("status").asText()).isEqualTo("DRAFT");
        assertThat(created.path("hasUnpublishedChanges").asBoolean()).isFalse();
        assertThat(created.path("items").size()).isEqualTo(2);
        assertThat(created.path("items").get(0).path("examId").asText()).isEqualTo(e1.examId.toString());
        assertThat(created.path("items").get(1).path("examId").asText()).isEqualTo(e2.examId.toString());
        long rev0 = created.path("revision").asLong();

        // Get & List
        mockMvc.perform(get(ADMIN_BASE + "/" + bundleId).with(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.examKindCode").value("HSA"))
                .andExpect(jsonPath("$.items.length()").value(2));

        mockMvc.perform(get(ADMIN_BASE)
                        .param("q", code)
                        .param("examKind", "HSA")
                        .param("status", "DRAFT")
                        .with(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", hasItem(bundleId.toString())));

        // Stale expectedRevision -> 412 VERSION_CONFLICT
        Map<String, Object> staleUpdate = createPayload(code, "Đổi tên", hsaId, List.of(e2.examId, e3.examId));
        staleUpdate.put("expectedRevision", rev0 + 99);
        performUpdate(bundleId, staleUpdate, auth)
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        // Valid update reorders/replaces items
        Map<String, Object> validUpdate = createPayload(code, "Bộ đề ĐGNL Đã Sửa", hsaId, List.of(e3.examId, e2.examId));
        validUpdate.put("expectedRevision", rev0);
        validUpdate.put("listPriceVnd", 180_000L);
        String updatedJson = performUpdate(bundleId, validUpdate, auth)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bộ đề ĐGNL Đã Sửa"))
                .andExpect(jsonPath("$.listPriceVnd").value(180_000))
                .andExpect(jsonPath("$.items[0].examId").value(e3.examId.toString()))
                .andExpect(jsonPath("$.items[1].examId").value(e2.examId.toString()))
                .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(updatedJson).path("revision").asLong()).isGreaterThan(rev0);

        drainOutbox();
        List<String> actions = jdbc.queryForList("""
                SELECT action FROM audit.audit_event
                WHERE module = 'COMMERCE' AND target_id = ?
                ORDER BY occurred_at ASC
                """, String.class, bundleId.toString());
        assertThat(actions).containsExactly("BUNDLE_CREATED", "BUNDLE_UPDATED");
    }

    @Test
    void candidatesFilterByExamKindOnlyOnSaleAndNonFullWithMetricsAndBuildMode() throws Exception {
        var auth = bundleAdmin();
        BundleExamBuilder partHsa = partExam("HSA", "TOAN").metrics(25, 45, 50);
        BundleExamBuilder subjectHsa = subjectExam("HSA", "LY").metrics(40, 60, 100);
        BundleExamBuilder reviewHsa = partExam("HSA", "HOA").review().metrics(30, 35, 30);
        BundleExamBuilder fullHsa = fullExam("HSA", "TOAN");
        BundleExamBuilder discontinuedHsa = partExam("HSA", "SINH").discontinued();
        BundleExamBuilder nullPriceHsa = partExam("HSA", "SU").price(null);
        BundleExamBuilder otherKind = partExam("SP2E", "TOAN");
        serve(partHsa);
        serve(subjectHsa);
        serve(reviewHsa);
        serve(fullHsa);
        serve(discontinuedHsa);
        serve(nullPriceHsa);
        serve(otherKind);

        // Hỗ trợ cả mã kỳ thi ("HSA") lẫn UUID
        UUID hsaId = kindItem("HSA");
        String bodyByCode = mockMvc.perform(get(ADMIN_BASE + "/candidates").param("examKind", "HSA").with(auth))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode listByCode = mapper.readTree(bodyByCode);
        List<String> idsByCode = listByCode.findValuesAsText("examId");
        assertThat(idsByCode).contains(partHsa.examId.toString(), subjectHsa.examId.toString(), reviewHsa.examId.toString());
        assertThat(idsByCode).doesNotContain(
                fullHsa.examId.toString(),
                discontinuedHsa.examId.toString(),
                nullPriceHsa.examId.toString(),
                otherKind.examId.toString());

        JsonNode reviewNode = findByExamId(listByCode, reviewHsa.examId);
        assertThat(reviewNode.path("buildMode").asText()).isEqualTo("REVIEW");
        assertThat(reviewNode.path("questionCount").path("min").asInt()).isEqualTo(30);
        assertThat(reviewNode.path("durationMinutes").path("min").asInt()).isEqualTo(35);
        assertThat(reviewNode.path("maxPoints").path("min").decimalValue()).isEqualByComparingTo("30");

        String bodyByUuid = mockMvc.perform(get(ADMIN_BASE + "/candidates").param("examKind", hsaId.toString()).with(auth))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(bodyByUuid).findValuesAsText("examId"))
                .containsExactlyElementsOf(idsByCode);

        // examKind không hợp lệ -> 422 BUNDLE_EXAM_KIND_INVALID
        mockMvc.perform(get(ADMIN_BASE + "/candidates").param("examKind", "NON_EXISTENT_KIND").with(auth))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUNDLE_EXAM_KIND_INVALID"));
    }

    private static JsonNode findByExamId(JsonNode array, UUID examId) {
        for (JsonNode node : array) {
            if (examId.toString().equals(node.path("examId").asText())) {
                return node;
            }
        }
        throw new AssertionError("Candidate not found: " + examId);
    }
}
