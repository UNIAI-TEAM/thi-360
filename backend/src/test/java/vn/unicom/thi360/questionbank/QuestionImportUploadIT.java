package vn.unicom.thi360.questionbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import vn.unicom.thi360.shared.storage.ObjectStorage;

class QuestionImportUploadIT extends AbstractIntegrationTest {
    private static final String URL = "/api/v1/admin/question-imports/uploads";
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectStorage storage;

    @Test
    void uploadCsvStoresSourceAndSuggestions() throws Exception {
        byte[] bytes = "type,content,subject,grade,difficulty\nSHORT_ANSWER,Question,TOAN,G10,NB\n"
                .getBytes(StandardCharsets.UTF_8);
        var result = mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("file", "questions.csv", "text/csv", bytes))
                        .with(asUser(Permissions.QUESTION_MODIFY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileType").value("CSV"))
                .andExpect(jsonPath("$.rowCount").value(1))
                .andExpect(jsonPath("$.suggestedMapping.c0").value("type"))
                .andReturn();
        UUID id = UUID.fromString(mapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asText());
        var row = jdbc.queryForMap("select object_key, status, expires_at, created_at from questionbank.question_import_upload where id = ?", id);
        assertThat(row.get("status")).isEqualTo("UPLOADED");
        assertThat(storage.get((String) row.get("object_key"))).contains(bytes);
        assertThat(((java.sql.Timestamp) row.get("expires_at")).toInstant())
                .isEqualTo(((java.sql.Timestamp) row.get("created_at")).toInstant().plusSeconds(86400));
    }

    @Test
    void macroAndOversizedFilesAreRejected() throws Exception {
        mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("file", "test.xlsm", MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[] {1}))
                        .with(asUser(Permissions.QUESTION_MODIFY)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IMPORT_MACRO_NOT_ALLOWED"));
        mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("file", "test.csv", "text/csv", new byte[5 * 1024 * 1024 + 1]))
                        .with(asUser(Permissions.QUESTION_MODIFY)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("IMPORT_FILE_TOO_LARGE"));
    }

    @Test
    void viewerCannotUpload() throws Exception {
        mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("file", "test.csv", "text/csv", new byte[] {1}))
                        .with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isForbidden());
    }
}
