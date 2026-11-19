package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

class ExamContractStubIT extends AbstractIntegrationTest {
    private static final String ID = "00000000-0000-0000-0000-000000000001";
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    @Test
    void summaryRequiresViewAndReturnsNotFoundForMissingExam() throws Exception {
        String url = "/api/v1/admin/exams/" + ID + "/structure/summary";
        mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(url).with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isForbidden());
        mockMvc.perform(get(url).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isNotFound());
    }

    @Test
    void overviewRequiresModify() throws Exception {
        String url = "/api/v1/admin/exams/" + ID + "/overview";
        String body = "{\"overview\":[],\"expectedRevision\":0}";
        mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(url).with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void scopedCheckReturnsNotFoundForMissingExam() throws Exception {
        mockMvc.perform(post("/api/v1/admin/exams/" + ID + "/check")
                        .with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"SECTION\",\"sectionId\":\"section\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void targetPreviewRequiresViewAndValidatesRequests() throws Exception {
        String url = "/api/v1/admin/exams/" + ID + "/preview";
        String body = "{\"target\":{\"kind\":\"SECTION\",\"sectionId\":\"section\"}}";
        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pathId\":\"default\",\"seed\":1,\"regeneratePartIds\":[\"part\"]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_VIEW))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void previewsExposeImplementedFieldsAndStructureReportsUncheckedSection() throws Exception {
        UUID examId = UUID.randomUUID();
        UUID seededQuestion = jdbc.queryForObject("select id from questionbank.question where code = ?",
                UUID.class, "SEED-MATH-G10-NB-SC-001");
        jdbc.update("insert into questionbank.question_knowledge(question_id, knowledge_item_id, is_primary) "
                + "values (?, ?, true) on conflict (question_id, knowledge_item_id) do update set is_primary = true",
                seededQuestion, UUID.fromString("00000000-0000-7000-8200-000000001201"));
        jdbc.update("insert into exam.exam (id, code, code_normalized, name, scope, timing, question_count, duration_minutes) "
                + "values (?, ?, ?, 'Preview', 'FULL', 'SHARED', 1, 60)", examId, examId.toString(), examId.toString());
        String parts = """
                {"parts":[{"id":"part","order":1,"name":"Part","questionCount":1,
                  "participation":{"mode":"ALL"},"sections":[{"id":"section","subjectCode":"TOAN",
                  "questionCount":1,"blocks":[{"id":"group","name":"Group",
                  "questionType":"SINGLE_CHOICE","questionCount":1,
                  "knowledgeScope":{"nodeIds":["00000000-0000-7000-8200-000000001201"]}}]}],"options":[]}]}
                """;
        jdbc.update("insert into exam.exam_structure (exam_id, parts, structure_hash) values (?, cast(? as jsonb), 'test')",
                examId, parts);
        var response = mockMvc.perform(post("/api/v1/admin/exams/" + examId + "/preview")
                        .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pathId\":\"default\",\"seed\":123}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        var body = mapper.readTree(response.getContentAsString());
        assertThat(body.path("status").asText()).isEqualTo("GENERATED");
        var item = body.path("paper").path("parts").get(0).path("blocks").get(0).path("items").get(0);
        assertThat(item.path("originalOrder").asInt()).isEqualTo(1);
        assertThat(item.path("question").path("optionOrder").isArray()).isTrue();
        assertThat(item.path("question").path("originalOptionOrder").isArray()).isTrue();
        assertThat(item.path("question").path("originalNumber").asInt()).isEqualTo(1);
        assertThat(body.path("compliance").isArray()).isTrue();
        assertThat(UUID.fromString(body.path("previewId").asText())).isNotNull();
        mockMvc.perform(post("/api/v1/admin/exams/" + examId + "/preview")
                        .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":{\"kind\":\"SECTION\",\"sectionId\":\"section\"},\"seed\":123}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("GENERATED"))
                .andExpect(jsonPath("$.previewId").isString())
                .andExpect(jsonPath("$.paper.totalQuestions").value(1));
        var structure = mockMvc.perform(get("/api/v1/admin/exams/" + examId + "/structure")
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse();
        var states = mapper.readTree(structure.getContentAsString()).path("sectionStates");
        assertThat(states.get(0).path("sectionId").asText()).isEqualTo("section");
        assertThat(states.get(0).path("state").asText()).isEqualTo("NEEDS_CHECK");
    }
}
