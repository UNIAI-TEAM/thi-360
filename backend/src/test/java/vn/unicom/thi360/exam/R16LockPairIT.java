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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/** BE-21 row 14 (BE-18 R1-6 pair): the same Phần II edit locks on the SUBJECT exam, not on the PART one. */
@Transactional
class R16LockPairIT extends AbstractIntegrationTest {
    @Autowired private ObjectMapper mapper;

    @Test
    void aSubjectEditReportsBlockCountLockedAndTheSameEditOnThePartExamDoesNot() throws Exception {
        JsonNode subject = created(create("SUBJECT", null, 90));
        String subjectId = subject.path("id").asText();
        JsonNode stored = structure(subjectId);
        ArrayNode parts = stored.path("parts").deepCopy();
        ObjectNode part = (ObjectNode) parts.get(1);
        ObjectNode section = (ObjectNode) part.path("sections").get(0);
        ObjectNode block = (ObjectNode) section.path("blocks").get(0);
        assertThat(block.path("setCount").asInt()).isEqualTo(2);
        int delta = 3;
        block.put("setCount", 3).put("questionCount", block.path("questionCount").asInt() + delta);
        section.put("questionCount", section.path("questionCount").asInt() + delta);
        part.put("questionCount", part.path("questionCount").asInt() + delta)
                .put("questionCountOnPaper", part.path("questionCountOnPaper").asInt() + delta)
                .put("questionCountRequired", part.path("questionCountRequired").asInt() + delta);
        save(subjectId, stored, parts).andExpect(status().isOk());
        JsonNode subjectCheck = check(subjectId);
        System.out.println("R16 SUBJECT codes=" + subjectCheck.path("groups").findValuesAsText("code"));
        assertThat(subjectCheck.path("groups").findValuesAsText("code")).contains("BLOCK_COUNT_LOCKED");

        JsonNode exam = created(create("PART", "P2", 30));
        String id = exam.path("id").asText();
        JsonNode current = structure(id);
        ArrayNode partParts = current.path("parts").deepCopy();
        ObjectNode partTwo = (ObjectNode) partParts.get(0);
        ObjectNode partSection = (ObjectNode) partTwo.path("sections").get(0);
        ObjectNode partBlock = (ObjectNode) partSection.path("blocks").get(0);
        assertThat(partBlock.path("setCount").asInt()).isEqualTo(2);
        partBlock.put("setCount", 3).put("questionCount", 9);
        partSection.put("questionCount", 9);
        partTwo.put("questionCount", 9).put("questionCountOnPaper", 9).put("questionCountRequired", 9).put("maxPoints", 54);
        save(id, current, partParts).andExpect(status().isOk());
        JsonNode partCheck = check(id);
        System.out.println("R16 PART codes=" + partCheck.path("groups").findValuesAsText("code"));
        assertThat(partCheck.path("groups").findValuesAsText("code"))
                .doesNotContain("BLOCK_COUNT_LOCKED", "STRUCTURE_COUNT_LOCKED");
        JsonNode summary = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure/summary", id)
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode total = summary.path("parts").get(0);
        assertThat(total.path("allocated").asInt()).as("questions").isEqualTo(9);
        assertThat(total.path("commandCount").asInt()).as("prompts").isEqualTo(9);
        assertThat(total.path("maxPoints").decimalValue()).as("points").isEqualByComparingTo("54");
    }

    private ResultActions create(String scope, String partCode, Integer duration) throws Exception {
        ObjectNode body = mapper.createObjectNode().put("kind", "VSAT").put("scope", scope).put("subjectCode", "TOAN")
                .put("name", "R16 " + scope).put("code", "R16-" + UUID.randomUUID().toString().substring(0, 8));
        if (partCode != null) body.put("partCode", partCode);
        if (duration != null) body.put("durationMinutes", duration);
        return mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }

    private JsonNode created(ResultActions result) throws Exception {
        return mapper.readTree(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode structure(String id) throws Exception {
        return mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure", id)
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private ResultActions save(String id, JsonNode current, JsonNode parts) throws Exception {
        ObjectNode request = mapper.createObjectNode().put("expectedRevision", current.path("revision").asLong());
        request.set("parts", parts);
        request.set("examQuotas", current.path("examQuotas"));
        return mockMvc.perform(put("/api/v1/admin/exams/{id}/structure", id).with(asUser(Permissions.EXAM_MODIFY))
                .contentType(MediaType.APPLICATION_JSON).content(request.toString()));
    }

    private JsonNode check(String id) throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/check", id)
                        .with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
}
