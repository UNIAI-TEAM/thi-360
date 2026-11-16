package vn.unicom.thi360.questionbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

class QuestionImportValidationIT extends AbstractIntegrationTest {
    private static final String URL = "/api/v1/admin/question-imports/uploads";
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void validationBlocksUnsafeHtmlAndDoesNotWriteQuestions() throws Exception {
        String csv = "type,content,subject,grade,difficulty\n"
                + "SHORT_ANSWER,Safe question,TOAN,G10,NB\n"
                + "SHORT_ANSWER,<script>alert(1)</script>,TOAN,G10,NB\n";
        var uploaded = mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("file", "question.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .with(asUser(Permissions.QUESTION_MODIFY)))
                .andExpect(status().isOk()).andReturn();
        String id = mapper.readTree(uploaded.getResponse().getContentAsByteArray()).get("id").asText();
        Long before = jdbc.queryForObject("select count(*) from questionbank.question", Long.class);
        String request = """
                {"uploadId":"%s","mapping":{"c0":"type","c1":"content","c2":"subject","c3":"grade","c4":"difficulty"}}
                """.formatted(id);
        var response = mockMvc.perform(post(URL + "/{id}/validate", id).with(asUser(Permissions.QUESTION_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.valid").value(1))
                .andExpect(jsonPath("$.summary.error").value(1))
                .andExpect(jsonPath("$.rows[0].status").value("VALID"))
                .andExpect(jsonPath("$.rows[0].draft.acceptedAnswers").isArray())
                .andExpect(jsonPath("$.rows[0].draft.options").isArray())
                .andExpect(jsonPath("$.rows[0].draft.statements").isArray())
                .andExpect(jsonPath("$.rows[1].status").value("ERROR"))
                .andReturn();
        JsonNode issues = mapper.readTree(response.getResponse().getContentAsByteArray()).at("/rows/1/issues");
        assertThat(issues.toString()).contains("UNSAFE_CONTENT");
        assertThat(jdbc.queryForObject("select count(*) from questionbank.question", Long.class)).isEqualTo(before);
    }

    @Test
    void expiredUploadReturnsSourceGone() throws Exception {
        byte[] csv = "type,content\nSHORT_ANSWER,Question\n".getBytes(StandardCharsets.UTF_8);
        var uploaded = mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("file", "expired.csv", "text/csv", csv))
                        .with(asUser(Permissions.QUESTION_MODIFY)))
                .andExpect(status().isOk()).andReturn();
        String id = mapper.readTree(uploaded.getResponse().getContentAsByteArray()).get("id").asText();
        jdbc.update("update questionbank.question_import_upload set expires_at = now() - interval '1 minute' where id = ?",
                java.util.UUID.fromString(id));
        mockMvc.perform(post(URL + "/{id}/validate", id).with(asUser(Permissions.QUESTION_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mapping\":{\"c0\":\"type\",\"c1\":\"content\"}}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("IMPORT_SOURCE_GONE"));
    }

    @Test
    @Transactional
    void knowledgeInheritedFromMultipleSubjectsAcceptsEachSubject() throws Exception {
        jdbc.update("""
                insert into catalog.catalog_item_ref (item_id, ref_item_id)
                select chapter.id, subject.id from catalog.catalog_item chapter, catalog.catalog_item subject
                where chapter.code = '0D1' and subject.code = 'ANH'
                """);
        byte[] csv = ("type,content,subject,grade,difficulty,knowledge\n"
                + "SHORT_ANSWER,Mathematics,TOAN,G10,NB,0D1-1-1\n"
                + "SHORT_ANSWER,English,ANH,G10,NB,0D1-1-1\n").getBytes(StandardCharsets.UTF_8);
        var upload = mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("file", "subjects.csv", "text/csv", csv))
                        .with(asUser(Permissions.QUESTION_MODIFY)))
                .andExpect(status().isOk()).andReturn();
        String id = mapper.readTree(upload.getResponse().getContentAsByteArray()).get("id").asText();
        mockMvc.perform(post(URL + "/{id}/validate", id).with(asUser(Permissions.QUESTION_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mapping\":{\"c0\":\"type\",\"c1\":\"content\",\"c2\":\"subject\",\"c3\":\"grade\",\"c4\":\"difficulty\",\"c5\":\"knowledge\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].status").value("VALID"))
                .andExpect(jsonPath("$.rows[1].status").value("VALID"));
        Long before = jdbc.queryForObject("select count(*) from questionbank.question", Long.class);
        mockMvc.perform(post(URL + "/{id}/commit", id).with(asUser(Permissions.QUESTION_MODIFY))
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mapping\":{\"c0\":\"type\",\"c1\":\"content\",\"c2\":\"subject\",\"c3\":\"grade\",\"c4\":\"difficulty\",\"c5\":\"knowledge\"},\"duplicatePolicy\":\"SKIP\",\"skipErrors\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.createdCount").value(2));
        assertThat(jdbc.queryForObject("select count(*) from questionbank.question", Long.class)).isEqualTo(before + 2);
    }

    @Test
    void invalidTypeCannotLeakUnsafeHtmlThroughPreview() throws Exception {
        byte[] csv = "type,content,subject,grade,difficulty\nUNKNOWN,<script>alert(1)</script>,TOAN,G10,NB\n".getBytes(StandardCharsets.UTF_8);
        var upload = mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("file", "unsafe.csv", "text/csv", csv))
                        .with(asUser(Permissions.QUESTION_MODIFY)))
                .andExpect(status().isOk()).andReturn();
        String id = mapper.readTree(upload.getResponse().getContentAsByteArray()).get("id").asText();
        var result = mockMvc.perform(post(URL + "/{id}/validate", id).with(asUser(Permissions.QUESTION_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mapping\":{\"c0\":\"type\",\"c1\":\"content\",\"c2\":\"subject\",\"c3\":\"grade\",\"c4\":\"difficulty\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].status").value("ERROR")).andReturn();
        var response = mapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(response.at("/rows/0/draft").isNull()).isTrue();
        assertThat(response.at("/rows/0/issues").toString()).contains("UNSAFE_CONTENT");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("<script>");
    }
}
