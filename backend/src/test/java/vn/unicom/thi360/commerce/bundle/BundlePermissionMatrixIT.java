package vn.unicom.thi360.commerce.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * AC-29: Ma trận phân quyền tách biệt EXAM.VIEW, EXAM.MODIFY, EXAM.PUBLISH trên tất cả endpoint bộ đề.
 */
class BundlePermissionMatrixIT extends AbstractBundleIT {

    @Test
    void enforcesDistinctPermissionsAcrossAllSevenEndpointsAndRejectsMutationsOn403() throws Exception {
        BundleExamBuilder e1 = partExam("HSA", "TOAN");
        BundleExamBuilder e2 = subjectExam("HSA", "LY");
        serve(e1);
        serve(e2);
        UUID hsaId = kindItem("HSA");

        JsonNode created = createBundle(
                createPayload("PERM-" + shortToken(), "Bộ đề phân quyền", hsaId, List.of(e1.examId, e2.examId)),
                bundleAdmin());
        UUID bundleId = UUID.fromString(created.path("id").asText());
        long rev0 = created.path("revision").asLong();

        // 1. Unauthenticated -> 401 trên cả 7 endpoint
        mockMvc.perform(get(ADMIN_BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ADMIN_BASE + "/candidates").param("examKind", "HSA")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ADMIN_BASE + "/" + bundleId)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(ADMIN_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                createPayload("NOAUTH-" + shortToken(), "NoAuth", hsaId, List.of(e1.examId, e2.examId)))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(ADMIN_BASE + "/" + bundleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(updatePayload(rev0, "X-" + shortToken(), hsaId,
                                List.of(e1.examId, e2.examId)))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(ADMIN_BASE + "/" + bundleId + "/publish")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(ADMIN_BASE + "/" + bundleId + "/discontinue")).andExpect(status().isUnauthorized());

        // 2. Giữ ĐÚNG 2 quyền EXAM còn lại nhưng THIẾU quyền bắt buộc -> 403 & 0 bản ghi thay đổi
        var modifyAndPublishOnly = asUser(Permissions.EXAM_MODIFY, Permissions.EXAM_PUBLISH);
        mockMvc.perform(get(ADMIN_BASE).with(modifyAndPublishOnly)).andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN_BASE + "/candidates").param("examKind", "HSA").with(modifyAndPublishOnly))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN_BASE + "/" + bundleId).with(modifyAndPublishOnly)).andExpect(status().isForbidden());

        var viewAndPublishOnly = asUser(Permissions.EXAM_VIEW, Permissions.EXAM_PUBLISH);
        long bundlesBefore = jdbc.queryForObject("SELECT count(*) FROM commerce.bundle", Long.class);
        mockMvc.perform(post(ADMIN_BASE)
                        .with(viewAndPublishOnly)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                createPayload("DENY-" + shortToken(), "Deny Create", hsaId, List.of(e1.examId, e2.examId)))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(ADMIN_BASE + "/" + bundleId)
                        .with(viewAndPublishOnly)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(updatePayload(rev0, "DENY-U-" + shortToken(), hsaId,
                                List.of(e1.examId, e2.examId)))))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.bundle", Long.class)).isEqualTo(bundlesBefore);
        assertThat(jdbc.queryForObject("SELECT revision FROM commerce.bundle WHERE id = ?", Long.class, bundleId))
                .isEqualTo(rev0);

        var viewAndModifyOnly = asUser(Permissions.EXAM_VIEW, Permissions.EXAM_MODIFY);
        mockMvc.perform(post(ADMIN_BASE + "/" + bundleId + "/publish")
                        .with(viewAndModifyOnly)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("expectedRevision", rev0))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(ADMIN_BASE + "/" + bundleId + "/discontinue")
                        .with(viewAndModifyOnly)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("expectedRevision", rev0))))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT status FROM commerce.bundle WHERE id = ?", String.class, bundleId))
                .isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce.bundle_version WHERE bundle_id = ?",
                Long.class, bundleId)).isZero();

        // 3. Giữ DUY NHẤT quyền bắt buộc -> vượt qua guard phân quyền (không 401/403)
        var viewOnly = asUser(Permissions.EXAM_VIEW);
        mockMvc.perform(get(ADMIN_BASE).with(viewOnly)).andExpect(status().isOk());
        mockMvc.perform(get(ADMIN_BASE + "/candidates").param("examKind", "HSA").with(viewOnly)).andExpect(status().isOk());
        mockMvc.perform(get(ADMIN_BASE + "/" + bundleId).with(viewOnly)).andExpect(status().isOk());

        var modifyOnly = asUser(Permissions.EXAM_MODIFY);
        JsonNode createdWithModifyOnly = createBundle(
                createPayload("MOD-" + shortToken(), "Modify Only", hsaId, List.of(e1.examId, e2.examId)),
                modifyOnly);
        UUID modifyBundleId = UUID.fromString(createdWithModifyOnly.path("id").asText());
        long modifyRev = createdWithModifyOnly.path("revision").asLong();
        mockMvc.perform(put(ADMIN_BASE + "/" + modifyBundleId)
                        .with(modifyOnly)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(updatePayload(
                                modifyRev, "MOD2-" + shortToken(), hsaId, List.of(e1.examId, e2.examId)))))
                .andExpect(status().isOk());

        var publishOnly = asUser(Permissions.EXAM_PUBLISH);
        String pubJson = mockMvc.perform(post(ADMIN_BASE + "/" + bundleId + "/publish")
                        .with(publishOnly)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("expectedRevision", rev0))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long revAfterPub = mapper.readTree(pubJson).path("revision").asLong();
        mockMvc.perform(post(ADMIN_BASE + "/" + bundleId + "/discontinue")
                        .with(publishOnly)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("expectedRevision", revAfterPub))))
                .andExpect(status().isOk());
    }

    private Map<String, Object> updatePayload(long expectedRevision, String code, UUID examKindItemId, List<UUID> examIds) {
        Map<String, Object> map = createPayload(code, "Updated " + code, examKindItemId, examIds);
        map.put("expectedRevision", expectedRevision);
        return map;
    }
}
