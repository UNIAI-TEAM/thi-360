package vn.unicom.thi360.questionbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

class QuestionImportDuplicateIT extends AbstractIntegrationTest {
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void newVersionPreservesReadyServingProjectionAndSkipWritesNothing() throws Exception {
        String source = code();
        JsonNode created = create(source);
        String id = created.at("/summary/id").asText();
        long revision = created.at("/summary/revision").asLong();
        mockMvc.perform(post("/api/v1/admin/questions/{id}/ready", id).with(asUser(Permissions.QUESTION_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isOk());
        var before = jdbc.queryForMap("select status, current_version_no, working_version_no, content_text from questionbank.question where id = ?", UUID.fromString(id));
        String uploadId = upload(source, "SHORT_ANSWER", "Replacement");
        commit(uploadId, "NEW_VERSION").andExpect(jsonPath("$.newVersionCount").value(1));
        var after = jdbc.queryForMap("select status, current_version_no, working_version_no, content_text from questionbank.question where id = ?", UUID.fromString(id));
        assertThat(after.get("status")).isEqualTo("READY");
        assertThat(after.get("current_version_no")).isEqualTo(before.get("current_version_no"));
        assertThat(after.get("working_version_no")).isNotNull();
        assertThat(after.get("content_text")).isEqualTo(before.get("content_text"));
        String snapshot = jdbc.queryForObject("select snapshot::text from questionbank.question_version where question_id = ? and version_no = ?",
                String.class, UUID.fromString(id), after.get("working_version_no"));
        assertThat(snapshot).contains("Replacement");
        String second = upload(source, "SHORT_ANSWER", "Skipped");
        commit(second, "SKIP").andExpect(jsonPath("$.skippedDuplicateCount").value(1));
        assertThat(jdbc.queryForObject("select snapshot::text from questionbank.question_version where question_id = ? and version_no = ?",
                String.class, UUID.fromString(id), after.get("working_version_no"))).isEqualTo(snapshot);
    }

    @Test
    void newVersionOfReadySetKeepsServingChildrenAndRevision() throws Exception {
        String source = code();
        String createBody = QuestionTestSupport.createQuestionSetRequest()
                .replaceFirst("\\{\"type\"", "{\"sourceCode\":\"" + source + "\",\"type\"");
        var created = mockMvc.perform(post("/api/v1/admin/questions").with(asUser(Permissions.QUESTION_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated()).andReturn();
        var question = mapper.readTree(created.getResponse().getContentAsByteArray());
        UUID questionId = UUID.fromString(question.at("/summary/id").asText());
        long revision = question.at("/summary/revision").asLong();
        mockMvc.perform(post("/api/v1/admin/questions/{id}/ready", questionId).with(asUser(Permissions.QUESTION_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isOk());
        var childrenBefore = jdbc.queryForList("select id, content_text, position from questionbank.question where parent_id = ? and deleted_at is null order by position", questionId);
        Long sourceRevision = jdbc.queryForObject("select revision from questionbank.question_source_revision where subject_item_id = ?",
                Long.class, QuestionTestSupport.SUBJECT_TOAN_ID);
        String csv = "sourceCode,type,content,subject,grade,difficulty,correctAnswer,setCode\n"
                + source + ",QUESTION_SET,New passage,TOAN,G10,NB,,set\n"
                + ",SHORT_ANSWER,New child one,,,,42,set\n"
                + ",SHORT_ANSWER,New child two,,,,42,set\n";
        var uploaded = mockMvc.perform(multipart("/api/v1/admin/question-imports/uploads")
                        .file(new MockMultipartFile("file", "new-set.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .with(asUser(Permissions.QUESTION_MODIFY))).andExpect(status().isOk()).andReturn();
        String uploadId = mapper.readTree(uploaded.getResponse().getContentAsByteArray()).get("id").asText();
        String body = """
                {"uploadId":"%s","mapping":{"c0":"sourceCode","c1":"type","c2":"content","c3":"subject","c4":"grade","c5":"difficulty","c6":"correctAnswer","c7":"setCode"},"duplicatePolicy":"NEW_VERSION","skipErrors":false}
                """.formatted(uploadId);
        mockMvc.perform(post("/api/v1/admin/question-imports/uploads/{id}/commit", uploadId)
                        .with(asUser(Permissions.QUESTION_MODIFY)).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.newVersionCount").value(1));
        assertThat(jdbc.queryForList("select id, content_text, position from questionbank.question where parent_id = ? and deleted_at is null order by position", questionId))
                .isEqualTo(childrenBefore);
        assertThat(jdbc.queryForObject("select revision from questionbank.question_source_revision where subject_item_id = ?",
                Long.class, QuestionTestSupport.SUBJECT_TOAN_ID)).isEqualTo(sourceRevision);
        var row = jdbc.queryForMap("select status, current_version_no, working_version_no from questionbank.question where id = ?", questionId);
        assertThat(row.get("status")).isEqualTo("READY");
        assertThat(row.get("working_version_no")).isNotNull();
        assertThat(row.get("current_version_no")).isNotEqualTo(row.get("working_version_no"));
    }

    @Test
    void typeMismatchIsBlocking() throws Exception {
        String source = code();
        create(source);
        String id = upload(source, "ESSAY", "Another type");
        String body = "{\"uploadId\":\"" + id + "\",\"mapping\":" + mapping() + "}";
        var result = mockMvc.perform(post("/api/v1/admin/question-imports/uploads/{id}/validate", id)
                        .with(asUser(Permissions.QUESTION_MODIFY)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.error").value(1)).andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("IMPORT_SOURCE_TYPE_MISMATCH");
    }

    @Test
    void retiredQuestionStaysRetiredAfterImport() throws Exception {
        String source = code();
        JsonNode question = create(source);
        String id = question.at("/summary/id").asText();
        long revision = question.at("/summary/revision").asLong();
        var retired = mockMvc.perform(post("/api/v1/admin/questions/{id}/retire", id)
                        .with(asUser(Permissions.QUESTION_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isOk()).andReturn();
        assertThat(mapper.readTree(retired.getResponse().getContentAsByteArray()).get("status").asText())
                .isEqualTo("RETIRED");
        commit(upload(source, "SHORT_ANSWER", "Edited while retired"), "NEW_VERSION")
                .andExpect(jsonPath("$.newVersionCount").value(1));
        assertThat(jdbc.queryForObject("select status from questionbank.question where id = ?", String.class, UUID.fromString(id)))
                .isEqualTo("RETIRED");
    }

    @Test
    void sourceCodeHeldOnlyByWorkingVersionIsDuplicate() throws Exception {
        String servingCode = code();
        String workingCode = code();
        JsonNode question = create(servingCode);
        String id = question.at("/summary/id").asText();
        long revision = question.at("/summary/revision").asLong();
        var ready = mockMvc.perform(post("/api/v1/admin/questions/{id}/ready", id)
                        .with(asUser(Permissions.QUESTION_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isOk()).andReturn();
        long readyRevision = mapper.readTree(ready.getResponse().getContentAsByteArray()).get("revision").asLong();
        String update = """
                {"expectedRevision":%d,"type":"SHORT_ANSWER","sourceCode":"%s","subjectCode":"TOAN",
                 "gradeCode":"G10","difficultyCode":"NB","content":"<p>Working</p>",
                 "body":{"type":"SHORT_ANSWER","answers":["42"]}}
                """.formatted(readyRevision, workingCode);
        mockMvc.perform(put("/api/v1/admin/questions/{id}", id).with(asUser(Permissions.QUESTION_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isOk());
        commit(upload(workingCode, "SHORT_ANSWER", "Should skip"), "SKIP")
                .andExpect(jsonPath("$.skippedDuplicateCount").value(1));
        assertThat(jdbc.queryForObject("select source_code_normalized from questionbank.question where id = ?",
                String.class, UUID.fromString(id))).isEqualTo(servingCode);
    }

    private JsonNode create(String source) throws Exception {
        String body = """
                {"type":"SHORT_ANSWER","sourceCode":"%s","subjectCode":"TOAN","gradeCode":"G10",
                 "difficultyCode":"NB","content":"<p>Original</p>","body":{"type":"SHORT_ANSWER","answers":["42"]}}
                """.formatted(source);
        var result = mockMvc.perform(post("/api/v1/admin/questions").with(asUser(Permissions.QUESTION_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String upload(String source, String type, String content) throws Exception {
        String csv = "sourceCode,type,content,subject,grade,difficulty,correctAnswer\n"
                + source + "," + type + "," + content + ",TOAN,G10,NB,42\n";
        var result = mockMvc.perform(multipart("/api/v1/admin/question-imports/uploads")
                        .file(new MockMultipartFile("file", "duplicates.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .with(asUser(Permissions.QUESTION_MODIFY))).andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions commit(String id, String policy) throws Exception {
        String body = "{\"uploadId\":\"" + id + "\",\"mapping\":" + mapping()
                + ",\"duplicatePolicy\":\"" + policy + "\",\"skipErrors\":false}";
        return mockMvc.perform(post("/api/v1/admin/question-imports/uploads/{id}/commit", id)
                        .with(asUser(Permissions.QUESTION_MODIFY)).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }

    private static String code() { return ("DUP-" + UUID.randomUUID()).toUpperCase(java.util.Locale.ROOT); }
    private static String mapping() {
        return "{\"c0\":\"sourceCode\",\"c1\":\"type\",\"c2\":\"content\",\"c3\":\"subject\",\"c4\":\"grade\",\"c5\":\"difficulty\",\"c6\":\"correctAnswer\"}";
    }
}
