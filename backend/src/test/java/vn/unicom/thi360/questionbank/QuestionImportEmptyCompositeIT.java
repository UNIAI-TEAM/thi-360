package vn.unicom.thi360.questionbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

class QuestionImportEmptyCompositeIT extends AbstractIntegrationTest {
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void emptySetCanBeCreatedAndUpdatedButNotMarkedReady() throws Exception {
        String create = """
                {"type":"QUESTION_SET","subjectCode":"TOAN","gradeCode":"G10","difficultyCode":"NB",
                 "content":"<p>Passage</p>","body":{"type":"QUESTION_SET","passage":"<p>Passage</p>","children":[]}}
                """;
        var created = mockMvc.perform(post("/api/v1/admin/questions").with(asUser(Permissions.QUESTION_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.summary.points").value(0)).andReturn();
        var json = mapper.readTree(created.getResponse().getContentAsByteArray());
        String id = json.at("/summary/id").asText();
        long revision = json.at("/summary/revision").asLong();
        String update = create.substring(0, create.lastIndexOf('}')) + ",\"expectedRevision\":" + revision + "}";
        var updated = mockMvc.perform(put("/api/v1/admin/questions/{id}", id).with(asUser(Permissions.QUESTION_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk()).andExpect(jsonPath("$.question.summary.points").value(0)).andReturn();
        long nextRevision = mapper.readTree(updated.getResponse().getContentAsByteArray()).at("/question/summary/revision").asLong();
        var ready = mockMvc.perform(post("/api/v1/admin/questions/{id}/ready", id).with(asUser(Permissions.QUESTION_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":" + nextRevision + "}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("QUESTION_NOT_READY"))
                .andReturn();
        assertThat(ready.getResponse().getContentAsString()).contains("SET_WITHOUT_CHILDREN");
    }

    @Test
    void importEmptySetAsDraftWithWarning() throws Exception {
        UUID actor = UUID.randomUUID();
        String source = ("EMPTY-SET-" + UUID.randomUUID()).toUpperCase(java.util.Locale.ROOT);
        String csv = "sourceCode,type,content,subject,grade,difficulty,setCode\n"
                + source + ",QUESTION_SET,Passage,TOAN,G10,NB,SET-X\n";
        var upload = mockMvc.perform(multipart("/api/v1/admin/question-imports/uploads")
                        .file(new MockMultipartFile("file", "empty-set.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .with(asUser(actor, "import-user", Permissions.QUESTION_MODIFY)))
                .andExpect(status().isOk()).andReturn();
        String id = mapper.readTree(upload.getResponse().getContentAsByteArray()).get("id").asText();
        String mapping = "{\"c0\":\"sourceCode\",\"c1\":\"type\",\"c2\":\"content\",\"c3\":\"subject\",\"c4\":\"grade\",\"c5\":\"difficulty\",\"c6\":\"setCode\"}";
        String validate = "{\"uploadId\":\"" + id + "\",\"mapping\":" + mapping + "}";
        var preview = mockMvc.perform(post("/api/v1/admin/question-imports/uploads/{id}/validate", id)
                        .with(asUser(actor, "import-user", Permissions.QUESTION_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(validate))
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.error").value(0)).andReturn();
        assertThat(preview.getResponse().getContentAsString()).contains("SET_NO_CHILDREN");
        String commit = "{\"uploadId\":\"" + id + "\",\"mapping\":" + mapping + ",\"duplicatePolicy\":\"SKIP\",\"skipErrors\":false}";
        mockMvc.perform(post("/api/v1/admin/question-imports/uploads/{id}/commit", id)
                        .with(asUser(actor, "import-user", Permissions.QUESTION_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(commit))
                .andExpect(status().isOk()).andExpect(jsonPath("$.setCount").value(1));
        var row = jdbc.queryForMap("select status, points from questionbank.question where source_code_normalized = ?", source);
        assertThat(row.get("status")).isEqualTo("DRAFT");
        assertThat((java.math.BigDecimal) row.get("points")).isEqualByComparingTo("0");
    }
}
