package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.security.PermissionCatalog;

/** EX-1 AC-1 to AC-5: the listing read and write API. Rolled back per test; covers are rows inserted by SQL. */
@Transactional
class ExamListingApiIT extends AbstractIntegrationTest {
    private static final UUID EXAM = UUID.fromString("00000000-0000-7000-8700-000000000001");
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PermissionCatalog permissions;
    @Autowired jakarta.persistence.EntityManager entityManager;

    private String url() { return "/api/v1/admin/exams/" + EXAM + "/listing"; }

    private JsonNode read() throws Exception {
        return mapper.readTree(mockMvc.perform(get(url()).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private ObjectNode body(long revision) {
        ObjectNode body = mapper.createObjectNode();
        body.put("summary", "Mot de thi thu");
        body.put("objectives", "Luyen de");
        body.put("audience", "Hoc sinh lop 12");
        body.put("listPriceVnd", 120_000);
        body.put("attemptsGranted", 3);
        body.put("benefitAiExplanation", true);
        body.put("benefitStrengthWeakness", false);
        body.put("benefitPractice", true);
        body.put("benefitNote", "Ap dung trong 12 thang");
        body.put("resultView", "FULL_SOLUTION");
        body.put("description", "Mo ta day du cua bai thi");
        body.put("expectedRevision", revision);
        return body;
    }

    private org.springframework.test.web.servlet.ResultActions save(ObjectNode body, String... permission) throws Exception {
        return mockMvc.perform(put(url()).with(asUser(permission.length == 0 ? new String[] {Permissions.EXAM_MODIFY} : permission))
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }

    private UUID cover(UUID exam) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam_cover_asset (id, exam_id, object_key, content_type, size_bytes, width, height) "
                + "VALUES (?, ?, ?, 'image/png', 100, 600, 600)", id, exam, "exam-covers/" + id);
        return id;
    }

    private long auditCount(String action) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT count(*) FROM shared.outbox_event WHERE topic='audit' "
                + "AND payload->>'action' = ? AND payload->>'targetId' = ?", Long.class, action, EXAM.toString());
    }

    @Test
    void savesAndReadsEveryListingField() throws Exception {
        JsonNode before = read();
        assertThat(before.path("minPriceVnd").asLong()).isEqualTo(5000);
        assertThat(before.has("published")).isTrue();
        UUID cover = cover(EXAM);
        ObjectNode body = body(before.path("revision").asLong());
        body.put("coverAssetId", cover.toString());
        long audits = auditCount("EXAM_LISTING_UPDATED");
        JsonNode saved = mapper.readTree(save(body).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        for (JsonNode fresh : List.of(saved, read())) {
            assertThat(fresh.path("summary").asText()).isEqualTo("Mot de thi thu");
            assertThat(fresh.path("objectives").asText()).isEqualTo("Luyen de");
            assertThat(fresh.path("audience").asText()).isEqualTo("Hoc sinh lop 12");
            assertThat(fresh.path("listPriceVnd").asLong()).isEqualTo(120_000);
            assertThat(fresh.path("attemptsGranted").asInt()).isEqualTo(3);
            assertThat(fresh.path("benefitAiExplanation").asBoolean()).isTrue();
            assertThat(fresh.path("benefitStrengthWeakness").asBoolean()).isFalse();
            assertThat(fresh.path("benefitPractice").asBoolean()).isTrue();
            assertThat(fresh.path("benefitNote").asText()).isEqualTo("Ap dung trong 12 thang");
            assertThat(fresh.path("resultView").asText()).isEqualTo("FULL_SOLUTION");
            assertThat(fresh.path("description").asText()).isEqualTo("Mo ta day du cua bai thi");
            assertThat(fresh.path("coverAssetId").asText()).isEqualTo(cover.toString());
            assertThat(fresh.path("coverUrl").asText()).isEqualTo("/api/v1/admin/exams/" + EXAM + "/cover/" + cover);
            assertThat(fresh.path("checkIssues")).isEmpty();
            assertThat(fresh.path("revision").asLong()).isEqualTo(before.path("revision").asLong() + 1);
        }
        assertThat(auditCount("EXAM_LISTING_UPDATED")).isEqualTo(audits + 1);
        assertThat(jdbc.queryForObject("SELECT description FROM exam.exam WHERE id = ?", String.class, EXAM))
                .isEqualTo("Mo ta day du cua bai thi");
        assertThat(jdbc.queryForObject("SELECT cover_asset_id FROM exam.exam WHERE id = ?", UUID.class, EXAM)).isEqualTo(cover);
    }

    @Test
    void rejectsWrongTypesButKeepsIncompleteDrafts() throws Exception {
        long revision = read().path("revision").asLong();
        ObjectNode wrong = body(revision);
        wrong.put("summary", "x".repeat(301));
        wrong.put("attemptsGranted", 0);
        wrong.put("listPriceVnd", -1);
        wrong.put("resultView", "EVERYTHING");
        JsonNode error = mapper.readTree(save(wrong).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_LISTING_INVALID"))
                .andReturn().getResponse().getContentAsString());
        Set<String> fields = new TreeSet<>(error.path("fieldErrors").findValuesAsText("field"));
        assertThat(fields).containsExactly("attemptsGranted", "listPriceVnd", "resultView", "summary");
        for (int attempts : new int[] {0, 101}) {
            ObjectNode one = body(revision);
            one.put("attemptsGranted", attempts);
            save(one).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.fieldErrors[0].field").value("attemptsGranted"));
        }
        for (int attempts : new int[] {1, 100}) {
            ObjectNode edge = body(read().path("revision").asLong());
            edge.put("attemptsGranted", attempts);
            edge.put("summary", "y".repeat(300));
            save(edge).andExpect(status().isOk());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_listing WHERE exam_id = ?", Long.class, EXAM)).isEqualTo(1);
        // A draft without a price, attempts or description is saved; the publish issues are reported, not refused.
        ObjectNode draft = mapper.createObjectNode().put("summary", "Ban nhap").put("expectedRevision", read().path("revision").asLong());
        JsonNode saved = mapper.readTree(save(draft).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(saved.path("listPriceVnd").isNull()).isTrue();
        assertThat(saved.path("checkIssues").findValuesAsText("code"))
                .contains("LISTING_PRICE_MISSING", "LISTING_ATTEMPTS_MISSING", "LISTING_DESCRIPTION_MISSING", "LISTING_COVER_MISSING");
    }

    @Test
    void staleRevisionIsRejected() throws Exception {
        long revision = read().path("revision").asLong();
        JsonNode first = mapper.readTree(save(body(revision)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        ObjectNode other = body(revision);
        other.put("summary", "Lan luu thu hai");
        save(other).andExpect(status().isPreconditionFailed()).andExpect(jsonPath("$.code").value("EXAM_REVISION_CONFLICT"));
        JsonNode after = read();
        assertThat(after.path("summary").asText()).isEqualTo("Mot de thi thu");
        assertThat(after.path("revision").asLong()).isEqualTo(first.path("revision").asLong());
        // The newer revision is accepted.
        save(other.put("expectedRevision", after.path("revision").asLong())).andExpect(status().isOk());
    }

    @Test
    void eachEndpointRequiresItsOwnPermission() throws Exception {
        String base = "/api/v1/admin/exams/" + EXAM;
        long revision = read().path("revision").asLong();
        String json = body(revision).toString();
        mockMvc.perform(get(url())).andExpect(status().isUnauthorized());
        mockMvc.perform(put(url()).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isUnauthorized());
        mockMvc.perform(multipart(base + "/cover").file(new MockMultipartFile("file", "a.png", "image/png", new byte[] {1})))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(base + "/cover/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        Set<String> everythingBut = new TreeSet<>(permissions.allCodes());
        everythingBut.remove(Permissions.EXAM_VIEW);
        mockMvc.perform(get(url()).with(asUser(everythingBut.toArray(String[]::new)))).andExpect(status().isForbidden());
        mockMvc.perform(get(base + "/cover/" + UUID.randomUUID()).with(asUser(everythingBut.toArray(String[]::new))))
                .andExpect(status().isForbidden());
        everythingBut = new TreeSet<>(permissions.allCodes());
        everythingBut.remove(Permissions.EXAM_MODIFY);
        mockMvc.perform(put(url()).with(asUser(everythingBut.toArray(String[]::new))).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart(base + "/cover").file(new MockMultipartFile("file", "a.png", "image/png", new byte[] {1}))
                .with(asUser(everythingBut.toArray(String[]::new)))).andExpect(status().isForbidden());
        // Each permission alone.
        mockMvc.perform(put(url()).with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(url()).with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isForbidden());
        mockMvc.perform(get(url()).with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk());
        save(body(revision), Permissions.EXAM_MODIFY).andExpect(status().isOk());
        mockMvc.perform(get(url()).with(asUser(Permissions.EXAM_PUBLISH))).andExpect(status().isForbidden());
    }

    @Test
    void editMarksUnpublishedChanges() throws Exception {
        jdbc.update("UPDATE exam.exam SET has_unpublished_changes = false WHERE id = ?", EXAM);
        JsonNode before = read();
        assertThat(before.path("hasUnpublishedChanges").asBoolean()).isFalse();
        String checkStatus = jdbc.queryForObject("SELECT check_status FROM exam.exam WHERE id = ?", String.class, EXAM);
        JsonNode saved = mapper.readTree(save(body(before.path("revision").asLong())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(saved.path("hasUnpublishedChanges").asBoolean()).isTrue();
        assertThat(saved.path("revision").asLong()).isEqualTo(before.path("revision").asLong() + 1);
        assertThat(jdbc.queryForObject("SELECT revision FROM exam.exam WHERE id = ?", Long.class, EXAM))
                .isEqualTo(before.path("revision").asLong() + 1);
        assertThat(jdbc.queryForObject("SELECT has_unpublished_changes FROM exam.exam WHERE id = ?", Boolean.class, EXAM)).isTrue();
        assertThat(jdbc.queryForObject("SELECT check_status FROM exam.exam WHERE id = ?", String.class, EXAM)).isEqualTo(checkStatus);
    }
}
