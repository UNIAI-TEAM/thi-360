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
        String csv = "sourceCode,type,content,subject,grade,difficulty,points,correctAnswer,setCode\n"
                + source + ",QUESTION_SET,New passage,TOAN,G10,NB,,,set\n"
                + ",SHORT_ANSWER,New child one,,G11,TH,0.5,42,set\n"
                + java.util.stream.IntStream.range(0, 14)
                    .mapToObj(i -> ",SHORT_ANSWER,Standalone " + i + ",ANH,G10,NB,1,42,\n")
                    .collect(java.util.stream.Collectors.joining())
                + ",SHORT_ANSWER,New child two,,,,0.75,42,set\n";
        var uploaded = mockMvc.perform(multipart("/api/v1/admin/question-imports/uploads")
                        .file(new MockMultipartFile("file", "new-set.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .with(asUser(Permissions.QUESTION_MODIFY))).andExpect(status().isOk()).andReturn();
        String uploadId = mapper.readTree(uploaded.getResponse().getContentAsByteArray()).get("id").asText();
        String body = """
                {"uploadId":"%s","mapping":{"c0":"sourceCode","c1":"type","c2":"content","c3":"subject","c4":"grade","c5":"difficulty","c6":"points","c7":"correctAnswer","c8":"setCode"},"duplicatePolicy":"NEW_VERSION","skipErrors":false}
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

        // R1-9: bản working mới phải chứa câu con đúng thứ tự tệp và điểm tổng — không chỉ là
        // con trỏ version mới (mapper bỏ trống children phải làm test đỏ).
        var snapshot = mapper.readTree(jdbc.queryForObject(
                "select snapshot::text from questionbank.question_version where question_id = ? and version_no = ?",
                String.class, questionId, row.get("working_version_no")));
        assertThat(snapshot.at("/points").decimalValue()).isEqualByComparingTo("1.25");
        var importedChildren = snapshot.at("/body/children");
        assertThat(importedChildren.size()).isEqualTo(2);
        assertThat(importedChildren.get(0).at("/content").asText()).isEqualTo("New child one");
        assertThat(importedChildren.get(1).at("/content").asText()).isEqualTo("New child two");
        assertThat(importedChildren.get(0).at("/body/answers").toString()).contains("42");
        assertThat(importedChildren.get(1).at("/body/answers").toString()).contains("42");
        assertThat(importedChildren.get(0).at("/gradeItemId").asText()).isEqualTo(
                jdbc.queryForObject("select id::text from catalog.catalog_item where code = 'G11'", String.class));
        assertThat(importedChildren.get(0).at("/levelItemId").asText()).isEqualTo(
                jdbc.queryForObject("select id::text from catalog.catalog_item where code = 'TH'", String.class));
        assertThat(importedChildren.get(0).at("/points").decimalValue()).isEqualByComparingTo("0.5");
        assertThat(importedChildren.get(1).at("/points").decimalValue()).isEqualByComparingTo("0.75");

        // §8.2: lần nhập sau khi đã có working -> ghi đè tại chỗ (cùng version_no, snapshot mới).
        String csv2 = csv.replace("New child one", "Replaced child one").replace("New child two", "Replaced child two");
        var uploaded2 = mockMvc.perform(multipart("/api/v1/admin/question-imports/uploads")
                        .file(new MockMultipartFile("file", "new-set-2.csv", "text/csv", csv2.getBytes(StandardCharsets.UTF_8)))
                        .with(asUser(Permissions.QUESTION_MODIFY))).andExpect(status().isOk()).andReturn();
        String uploadId2 = mapper.readTree(uploaded2.getResponse().getContentAsByteArray()).get("id").asText();
        String body2 = """
                {"uploadId":"%s","mapping":{"c0":"sourceCode","c1":"type","c2":"content","c3":"subject","c4":"grade","c5":"difficulty","c6":"points","c7":"correctAnswer","c8":"setCode"},"duplicatePolicy":"NEW_VERSION","skipErrors":false}
                """.formatted(uploadId2);
        mockMvc.perform(post("/api/v1/admin/question-imports/uploads/{id}/commit", uploadId2)
                        .with(asUser(Permissions.QUESTION_MODIFY)).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body2))
                .andExpect(status().isOk()).andExpect(jsonPath("$.newVersionCount").value(1));
        var row2 = jdbc.queryForMap("select status, current_version_no, working_version_no from questionbank.question where id = ?", questionId);
        assertThat(row2.get("working_version_no")).isEqualTo(row.get("working_version_no"));
        var snapshot2 = mapper.readTree(jdbc.queryForObject(
                "select snapshot::text from questionbank.question_version where question_id = ? and version_no = ?",
                String.class, questionId, row2.get("working_version_no")));
        assertThat(snapshot2.at("/body/children").size()).isEqualTo(2);
        assertThat(snapshot2.at("/body/children").get(0).at("/content").asText()).isEqualTo("Replaced child one");
        assertThat(snapshot2.at("/body/children").get(1).at("/content").asText()).isEqualTo("Replaced child two");
        assertThat(jdbc.queryForList("select id, content_text, position from questionbank.question where parent_id = ? and deleted_at is null order by position", questionId))
                .isEqualTo(childrenBefore);
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
