package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * UNI-2754 (R2-5): a mixed question set whose positional child conditions include ESSAY keeps the exam's
 * {@code hasEssay} summary even when every child count sits in the Bộ câu hỏi quota row and no ESSAY quota exists.
 */
@Transactional
class ExamEssaySummaryIT extends AbstractIntegrationTest {
    @Autowired private ObjectMapper mapper;

    @Test
    void positionalEssayChildOfAMixedSetKeepsHasEssayWithoutAnEssayQuota() throws Exception {
        JsonNode exam = create();
        String id = exam.path("id").asText();
        mockMvc.perform(post("/api/v1/admin/exams/" + id + "/structure/template").with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.createObjectNode().put("templateCode", "HSA_SUBJECT_TOAN").put("confirmReplace", true)
                                .put("expectedRevision", exam.path("revision").asLong()).toString()))
                .andExpect(status().isOk());
        assertThat(listed(exam.path("code").asText())).as("the template has no essay").isFalse();

        JsonNode structure = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/" + id + "/structure")
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        ArrayNode parts = structure.path("parts").deepCopy();
        ObjectNode section = (ObjectNode) parts.get(0).path("sections").get(0);
        boolean essayQuota = false;
        boolean setRow = false;
        for (JsonNode item : section.path("answerQuotas").path("items")) {
            essayQuota |= "ESSAY".equals(item.path("answerType").asText());
            setRow |= "QUESTION_SET".equals(item.path("answerType").asText());
        }
        assertThat(setRow).as("the child counts are in the Bộ câu hỏi row").isTrue();
        assertThat(essayQuota).as("no ESSAY quota row").isFalse();

        ObjectNode set = null;
        for (JsonNode block : section.path("blocks")) {
            if ("QUESTION_SET".equals(block.path("questionType").asText())) set = (ObjectNode) block;
        }
        assertThat(set).isNotNull();
        set.remove("childAnswerType");
        ArrayNode conditions = set.putArray("childConditions");
        conditions.addObject().put("from", 1).put("to", 2).put("answerType", "SINGLE_CHOICE").putArray("nodeIds");
        conditions.addObject().put("from", 3).put("to", 3).put("answerType", "ESSAY").putArray("nodeIds");
        ObjectNode request = mapper.createObjectNode().put("expectedRevision", structure.path("revision").asLong());
        request.set("parts", parts);
        request.set("examQuotas", structure.path("examQuotas"));
        mockMvc.perform(put("/api/v1/admin/exams/" + id + "/structure").with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isOk());

        assertThat(listed(exam.path("code").asText())).as("hasEssay=true lists the exam").isTrue();
        JsonNode detail = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/" + id).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(detail.path("hasEssay").asBoolean()).isTrue();
    }

    private JsonNode create() throws Exception {
        var request = mapper.createObjectNode().put("kind", "HSA").put("scope", "SUBJECT").put("subjectCode", "TOAN")
                .put("buildMode", "STANDARD").put("name", "Essay summary")
                .put("code", "T-" + UUID.randomUUID().toString().substring(0, 8)).put("durationMinutes", 60);
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    /** True when the exam is in the admin list filtered by hasEssay=true. */
    private boolean listed(String code) throws Exception {
        JsonNode page = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams").param("q", code).param("hasEssay", "true")
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode item : page.path("items")) {
            if (code.equalsIgnoreCase(item.path("code").asText())) return true;
        }
        return false;
    }
}
