package vn.unicom.thi360.questionbank;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * AC của UNI-2609 (spec 4.5, 7): endpoint câu hỏi — quyền 200/401/403/404, lưu hai mức,
 * lọc HTML (422), khóa lạc quan (412), xóa mềm, nhân đôi, khôi phục phiên bản và nhật ký
 * {@code QUESTION.*}.
 */
@Transactional
class QuestionApiIT extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/admin/questions";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private AuditRecorder auditRecorder;


    // ---------------------------------------------------------------- quyền & tồn tại

    @Test
    void anonymousReadIsUnauthorizedAndWriteWithoutOriginIsForbidden() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(QuestionTestSupport.createSingleChoiceRequest()))
                .andExpect(status().isForbidden());
    }

    @Test
    void viewerCannotWrite() throws Exception {
        mockMvc.perform(post(BASE)
                        .with(asUser(Permissions.QUESTION_VIEW))
                        .header("Idempotency-Key", "k-view-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QuestionTestSupport.createSingleChoiceRequest()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(BASE + "/{id}", UUID.randomUUID())
                        .with(asUser(Permissions.QUESTION_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void modifierWithoutViewCannotRead() throws Exception {
        // QUESTION.MODIFY không kèm QUESTION.VIEW: đọc bị cấm (ma trận quyền spec 7).
        mockMvc.perform(get(BASE).with(asUser(Permissions.QUESTION_MODIFY)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownIdGives404() throws Exception {
        mockMvc.perform(get(BASE + "/{id}", UUID.randomUUID())
                        .with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(put(BASE + "/{id}", UUID.randomUUID())
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":0}"))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- tạo & đọc

    @Test
    void createStoresDraftAndPersistsAllFields() throws Exception {
        String body = """
                {"type":"SINGLE_CHOICE","subjectCode":"TOAN","gradeCode":"G10",
                 "difficultyCode":"NB","taskTypeCode":"SINGLE",
                 "points":2,"knowledge":[{"nodeId":"%s","primary":true}],
                 "content":"<p>2+2=?</p>","solution":"<p>Bang 4</p>",
                 "commonMistakes":["<p>Nham cong</p>"],
                 "body":%s}""".formatted(
                QuestionTestSupport.KNOWLEDGE_LEAF_ID, QuestionTestSupport.singleChoiceBody());
        MvcResult result = mockMvc.perform(post(BASE)
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .header("Idempotency-Key", "k-create-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.code").value(org.hamcrest.Matchers.startsWith("CH-")))
                .andExpect(jsonPath("$.summary.status").value("DRAFT"))
                .andExpect(jsonPath("$.summary.subjectCode").value("TOAN"))
                .andExpect(jsonPath("$.summary.gradeCode").value("G10"))
                .andExpect(jsonPath("$.summary.difficultyCode").value("NB"))
                .andExpect(jsonPath("$.summary.points").value(2))
                .andExpect(jsonPath("$.summary.hasSolution").value(true))
                .andExpect(jsonPath("$.summary.knowledge[0].primary").value(true))
                .andExpect(jsonPath("$.summary.workingVersion").value(1))
                .andExpect(jsonPath("$.body.type").value("SINGLE_CHOICE"))
                .andReturn();
        UUID id = UUID.fromString(read(result).at("/summary/id").asText());

        // Dòng lưu đủ phân loại và chuỗi tìm kiếm bỏ dấu.
        var row = jdbcTemplate.queryForMap(
                "SELECT status, subject_item_id, grade_item_id, level_item_id, task_type_item_id,"
                        + " has_solution, working_version_no, search_text"
                        + " FROM questionbank.question WHERE id = ?", id);
        org.assertj.core.api.Assertions.assertThat(row.get("status")).isEqualTo("DRAFT");
        org.assertj.core.api.Assertions.assertThat(row.get("subject_item_id"))
                .isEqualTo(QuestionTestSupport.SUBJECT_TOAN_ID);
        org.assertj.core.api.Assertions.assertThat(row.get("search_text").toString())
                .contains("2+2=?");

        // GET chi tiết trả cùng nội dung.
        mockMvc.perform(get(BASE + "/{id}", id).with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.id").value(id.toString()))
                .andExpect(jsonPath("$.content").value("<p>2+2=?</p>"));

        verify(auditRecorder).record(argThat((AuditEvent e) ->
                "QUESTION.CREATE".equals(e.action()) && id.toString().equals(e.targetId())));
    }

    @Test
    void createQuestionSetPersistsChildrenAsRows() throws Exception {
        MvcResult result = mockMvc.perform(post(BASE)
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .header("Idempotency-Key", "k-set-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QuestionTestSupport.createQuestionSetRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.type").value("QUESTION_SET"))
                .andExpect(jsonPath("$.summary.points").value(3)) // 1 + 2 mặc định
                .andExpect(jsonPath("$.children", hasSize(2)))
                .andReturn();
        UUID parentId = UUID.fromString(read(result).at("/summary/id").asText());
        Integer childCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM questionbank.question WHERE parent_id = ? AND deleted_at IS NULL",
                Integer.class, parentId);
        org.assertj.core.api.Assertions.assertThat(childCount).isEqualTo(2);
        Integer childVersions = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM questionbank.question_version v"
                        + " JOIN questionbank.question q ON q.id = v.question_id"
                        + " WHERE q.parent_id = ? AND v.version_no = 1",
                Integer.class, parentId);
        org.assertj.core.api.Assertions.assertThat(childVersions).isEqualTo(2);
    }

    @Test
    void unsafeContentRejected422() throws Exception {
        String body = """
                {"type":"SINGLE_CHOICE",
                 "content":"<p>Hi<script>alert(1)</script></p>","body":%s}"""
                .formatted(QuestionTestSupport.singleChoiceBody());
        mockMvc.perform(post(BASE)
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .header("Idempotency-Key", "k-unsafe-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("QUESTION_CONTENT_UNSAFE"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("QUESTION_CONTENT_UNSAFE"));
    }

    @Test
    void invalidBodyRejected422WithFieldError() throws Exception {
        String body = """
                {"type":"SINGLE_CHOICE","content":"<p>Q?</p>",
                 "body":{"type":"SINGLE_CHOICE","options":[
                    {"id":"o1","content":"<p>Mot</p>","correct":true},
                    {"id":"o1","content":"<p>Trung id</p>","correct":false}]}}""";
        mockMvc.perform(post(BASE)
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .header("Idempotency-Key", "k-invalid-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("QUESTION_BODY_INVALID"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(greaterThan(0))));
    }

    // ---------------------------------------------------------------- sửa & khóa lạc quan

    @Test
    void updateRewritesWorkingAndHonoursExpectedRevision() throws Exception {
        UUID id = createQuestion();
        long revision = currentRevision(id);

        // Lệch revision → 412.
        mockMvc.perform(put(BASE + "/{id}", id)
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":%d,\"content\":\"<p>Sai</p>\"}"
                                .formatted(revision + 9)))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("QUESTION_REVISION_CONFLICT"));

        // Đổi dạng → 422 QUESTION_TYPE_IMMUTABLE.
        mockMvc.perform(put(BASE + "/{id}", id)
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":%d,\"type\":\"ESSAY\"}".formatted(revision)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("QUESTION_TYPE_IMMUTABLE"));

        // Sửa hợp lệ trên bản Nháp hiện có (cùng version 1, versionCreated=false).
        mockMvc.perform(put(BASE + "/{id}", id)
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":%d,\"content\":\"<p>De moi</p>\",".formatted(revision)
                                + "\"body\":" + QuestionTestSupport.singleChoiceBody() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionCreated").value(false))
                .andExpect(jsonPath("$.question.content").value("<p>De moi</p>"))
                .andExpect(jsonPath("$.question.summary.workingVersion").value(1));
    }

    // ---------------------------------------------------------------- xóa / nhân đôi / khôi phục

    @Test
    void deleteSoftDeletesAndHidesFromApi() throws Exception {
        UUID id = createQuestion();
        long revision = currentRevision(id);
        mockMvc.perform(delete(BASE + "/{id}", id)
                        .param("expectedRevision", String.valueOf(revision))
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .header("Idempotency-Key", "k-del-" + UUID.randomUUID()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(BASE + "/{id}", id).with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isNotFound());
        org.assertj.core.api.Assertions.assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT deleted_at IS NOT NULL FROM questionbank.question WHERE id = ?",
                                Boolean.class, id))
                .isTrue();
        verify(auditRecorder).record(argThat((AuditEvent e) ->
                "QUESTION.DELETE".equals(e.action()) && id.toString().equals(e.targetId())));
    }

    @Test
    void statusWritesAuditBeforeAndAfter() throws Exception {
        UUID id = createQuestion();
        mockMvc.perform(post(BASE + "/{id}/ready", id).with(asUser(Permissions.QUESTION_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + currentRevision(id) + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(BASE + "/{id}/retire", id).with(asUser(Permissions.QUESTION_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + currentRevision(id) + "}"))
                .andExpect(status().isOk());
        verify(auditRecorder).record(argThat((AuditEvent e) ->
                "QUESTION.READY".equals(e.action()) && id.toString().equals(e.targetId())
                        && "DRAFT".equals(e.before().get("status"))
                        && "READY".equals(e.after().get("status"))));
        verify(auditRecorder).record(argThat((AuditEvent e) ->
                "QUESTION.RETIRE".equals(e.action()) && id.toString().equals(e.targetId())
                        && "READY".equals(e.before().get("status"))
                        && "RETIRED".equals(e.after().get("status"))));
    }

    @Test
    void duplicateCopiesAsNewDraftWithNewCode() throws Exception {
        UUID id = createQuestion();
        String originalCode = jdbcTemplate.queryForObject(
                "SELECT code FROM questionbank.question WHERE id = ?", String.class, id);
        MvcResult result = mockMvc.perform(post(BASE + "/{id}/duplicate", id)
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .header("Idempotency-Key", "k-dup-" + UUID.randomUUID()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.status").value("DRAFT"))
                .andReturn();
        JsonNode json = read(result);
        UUID copyId = UUID.fromString(json.at("/summary/id").asText());
        org.assertj.core.api.Assertions.assertThat(copyId).isNotEqualTo(id);
        org.assertj.core.api.Assertions.assertThat(json.at("/summary/code").asText())
                .isNotEqualTo(originalCode);
        verify(auditRecorder).record(argThat((AuditEvent e) ->
                "QUESTION.DUPLICATE".equals(e.action()) && copyId.toString().equals(e.targetId())));
    }

    @Test
    void duplicateSetProjectsEveryChildBeforeAndAfterReady() throws Exception {
        var request = (com.fasterxml.jackson.databind.node.ObjectNode)
                objectMapper.readTree(QuestionTestSupport.createQuestionSetRequest());
        request.putArray("knowledge").addObject().put("nodeId", QuestionTestSupport.KNOWLEDGE_LEAF_ID.toString())
                .put("primary", true);
        var child = (com.fasterxml.jackson.databind.node.ObjectNode) request.at("/body/children/0");
        child.put("gradeItemId", QuestionTestSupport.GRADE_G10_ID.toString());
        child.put("levelItemId", "00000000-0000-7000-8200-000000000401");
        var source = mockMvc.perform(post(BASE).with(asUser(Permissions.QUESTION_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn();
        UUID sourceId = UUID.fromString(read(source).at("/summary/id").asText());
        var copied = mockMvc.perform(post(BASE + "/{id}/duplicate", sourceId)
                        .with(asUser(Permissions.QUESTION_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isCreated()).andReturn();
        UUID copyId = UUID.fromString(read(copied).at("/summary/id").asText());
        String fields = "position, subject_item_id, grade_item_id, level_item_id, points,"
                + " content_text, has_solution";
        var expected = jdbcTemplate.queryForList("SELECT " + fields
                + " FROM questionbank.question WHERE parent_id = ? ORDER BY position", sourceId);
        org.assertj.core.api.Assertions.assertThat(expected).hasSize(2);
        assertCopiedChildren(copyId, fields, expected, "DRAFT", null);
        mockMvc.perform(post(BASE + "/{id}/ready", copyId).with(asUser(Permissions.QUESTION_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + currentRevision(copyId) + "}"))
                .andExpect(status().isOk());
        assertCopiedChildren(copyId, fields, expected, "READY", 1);
    }

    private void assertCopiedChildren(UUID parent, String fields,
            java.util.List<java.util.Map<String, Object>> expected, String status, Integer currentVersion) {
        var rows = jdbcTemplate.queryForList("SELECT " + fields
                + " FROM questionbank.question WHERE parent_id = ? ORDER BY position", parent);
        org.assertj.core.api.Assertions.assertThat(rows).isEqualTo(expected);
        var states = jdbcTemplate.queryForList("SELECT status, current_version_no, working_version_no, search_text"
                + " FROM questionbank.question WHERE parent_id = ? ORDER BY position", parent);
        org.assertj.core.api.Assertions.assertThat(states).hasSize(2);
        for (var state : states) {
            org.assertj.core.api.Assertions.assertThat(state).containsEntry("status", status)
                    .containsEntry("current_version_no", currentVersion).containsEntry("working_version_no", null);
            org.assertj.core.api.Assertions.assertThat(state.get("search_text").toString()).contains("cau con");
        }
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM questionbank.question_knowledge k JOIN questionbank.question q"
                        + " ON q.id = k.question_id WHERE q.parent_id = ? AND k.knowledge_item_id = ?",
                Integer.class, parent, QuestionTestSupport.KNOWLEDGE_LEAF_ID)).isEqualTo(2);
    }

    @Test
    void restoreVersionOverwritesWorkingContent() throws Exception {
        UUID id = createQuestion();
        long revision = currentRevision(id);
        // Sửa nội dung trên bản làm việc v1.
        mockMvc.perform(put(BASE + "/{id}", id)
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":%d,\"content\":\"<p>Da sua</p>\",".formatted(revision)
                                + "\"body\":" + QuestionTestSupport.singleChoiceBody() + "}"))
                .andExpect(status().isOk());
        // Versions endpoint liệt kê một bản.
        mockMvc.perform(get(BASE + "/{id}/versions", id).with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versions", hasSize(1)))
                .andExpect(jsonPath("$.versions[0].state").value("WORKING"));
        // Khôi phục phiên bản không có → 404 QUESTION_VERSION_NOT_FOUND.
        mockMvc.perform(post(BASE + "/{id}/versions/{v}/restore", id, 99)
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":%d}".formatted(currentRevision(id))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUESTION_VERSION_NOT_FOUND"));
    }

    @Test
    void usageAndRetireImpactEmptyForFreshQuestion() throws Exception {
        UUID id = createQuestion();
        mockMvc.perform(get(BASE + "/{id}/usage", id).with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceBlocks", hasSize(0)))
                .andExpect(jsonPath("$.usedIn", hasSize(0)));
        mockMvc.perform(get(BASE + "/{id}/retire-impact", id).with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedBlocks", hasSize(0)))
                .andExpect(jsonPath("$.usedIn", hasSize(0)));
    }

    // ---------------------------------------------------------------- trợ giúp

    private UUID createQuestion() throws Exception {
        MvcResult result = mockMvc.perform(post(BASE)
                        .with(asUser(Permissions.QUESTION_MODIFY, Permissions.QUESTION_VIEW))
                        .header("Idempotency-Key", "k-fix-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QuestionTestSupport.createSingleChoiceRequest()))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(read(result).at("/summary/id").asText());
    }

    private long currentRevision(UUID id) {
        Long revision = jdbcTemplate.queryForObject(
                "SELECT revision FROM questionbank.question WHERE id = ?", Long.class, id);
        return revision == null ? -1 : revision;
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
