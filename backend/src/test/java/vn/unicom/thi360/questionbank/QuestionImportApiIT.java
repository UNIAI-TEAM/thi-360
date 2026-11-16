package vn.unicom.thi360.questionbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.infrastructure.QuestionImport;
import vn.unicom.thi360.questionbank.infrastructure.QuestionImportRepository;

class QuestionImportApiIT extends AbstractIntegrationTest {
    private static final String BASE = "/api/v1/admin/question-imports";
    @Autowired private QuestionImportRepository imports;
    @Autowired private ObjectMapper mapper;

    @Test
    void templateAndHistoryRequireTheirOwnPermissions() throws Exception {
        mockMvc.perform(get(BASE + "/template")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/template").with(asUser(Permissions.QUESTION_VIEW))).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/template").with(asUser(Permissions.QUESTION_MODIFY))).andExpect(status().isOk());
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE).with(asUser(Permissions.QUESTION_MODIFY))).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE).with(asUser(Permissions.QUESTION_VIEW))).andExpect(status().isOk());
    }

    @Test
    void uploadsValidateAndCommitRequireModify() throws Exception {
        String id = UUID.randomUUID().toString();
        String body = "{\"uploadId\":\"" + id + "\",\"mapping\":{},\"duplicatePolicy\":\"SKIP\",\"skipErrors\":false}";
        MockMultipartFile file = new MockMultipartFile("file", "questions.csv", "text/csv", "type,content\nSHORT_ANSWER,x\n".getBytes());
        mockMvc.perform(multipart(BASE + "/uploads").file(file).header("Origin", "http://localhost:3001"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart(BASE + "/uploads").file(file).with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart(BASE + "/uploads").file(file).with(asUser(Permissions.QUESTION_MODIFY)))
                .andExpect(status().isOk());
        for (String operation : new String[] {"validate", "commit"}) {
            var anonymous = post(BASE + "/uploads/{id}/" + operation, id).header("Origin", "http://localhost:3001")
                    .header("Idempotency-Key", "test-key").contentType(MediaType.APPLICATION_JSON).content(body);
            mockMvc.perform(anonymous).andExpect(status().isUnauthorized());
            mockMvc.perform(post(BASE + "/uploads/{id}/" + operation, id).with(asUser(Permissions.QUESTION_VIEW))
                            .header("Idempotency-Key", "test-key").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(BASE + "/uploads/{id}/" + operation, id).with(asUser(Permissions.QUESTION_MODIFY))
                            .header("Idempotency-Key", "test-key").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    @Transactional
    void historyIsNewestFirstAndCappedAt200() throws Exception {
        List<QuestionImport> rows = new ArrayList<>();
        Instant base = Instant.parse("2100-01-01T00:00:00Z");
        UUID newest = null;
        for (int i = 0; i < 201; i++) {
            UUID id = UUID.randomUUID();
            rows.add(new QuestionImport(id, "history.csv", 0, 0, 0, 0, 0, 0, 0, 0,
                    List.of(), 0, null, base.plusSeconds(i)));
            newest = id;
        }
        imports.saveAllAndFlush(rows);
        var result = mockMvc.perform(get(BASE).with(asUser(Permissions.QUESTION_VIEW)))
                .andExpect(status().isOk()).andReturn();
        var json = mapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(json.size()).isEqualTo(200);
        assertThat(json.get(0).get("id").asText()).isEqualTo(newest.toString());
    }
}
