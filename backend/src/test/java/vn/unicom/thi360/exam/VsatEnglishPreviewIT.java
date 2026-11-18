package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * BE-21 rows 3 and 4 (addendum specification 5.2): the structure summary reports the mixed set's
 * 14 commands and the generated paper picks the shared reading set once, numbering its true/false
 * children 8-9 and its single-choice children 10-15.
 */
@Transactional
class VsatEnglishPreviewIT extends AbstractIntegrationTest {
    private static final long SEED = 20261119L;
    @Autowired private ObjectMapper mapper;

    /** Row 3: the block 8-15 reports 8 questions, 14 commands, 48 points; the paper 25/67/150. */
    @Test
    void structureSummaryCountsFourteenCommandsForTheMixedSet() throws Exception {
        String id = created(create("SUBJECT", null));
        JsonNode summary = mapper.readTree(mockMvc.perform(
                        get("/api/v1/admin/exams/{id}/structure/summary", id)
                                .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode part = summary.path("parts").get(0);
        assertThat(part.path("allocated").asInt()).as("questions").isEqualTo(25);
        assertThat(part.path("commandCount").asInt()).as("commands").isEqualTo(67);
        assertThat(part.path("maxPoints").decimalValue()).as("points").isEqualByComparingTo("150");
        JsonNode section = part.path("children").get(0);
        JsonNode reading = null;
        for (JsonNode row : section.path("children")) {
            if ("Bài đọc dùng chung – câu 8–15".equals(row.path("name").asText())) {
                reading = row;
            }
        }
        assertThat(reading).as("shared reading row").isNotNull();
        assertThat(reading.path("allocated").asInt()).isEqualTo(8);
        assertThat(reading.path("commandCount").asInt()).isEqualTo(14);
        assertThat(reading.path("maxPoints").decimalValue()).isEqualByComparingTo("48");
    }

    /** Row 4: the shared reading appears once; children 8-9 are true/false, 10-15 single choice. */
    @Test
    void previewPicksTheSharedReadingOnce() throws Exception {
        String id = created(create("SUBJECT", null));
        JsonNode paper = preview(id);
        assertThat(paper.path("totalQuestions").asInt()).isEqualTo(25);
        assertThat(paper.path("parts")).hasSize(1);
        JsonNode part = paper.path("parts").get(0);
        assertThat(part.path("displaySegments")).hasSize(4);

        List<JsonNode> sets = new ArrayList<>();
        for (JsonNode block : part.path("blocks")) {
            for (JsonNode item : block.path("items")) {
                if ("SET".equals(item.path("kind").asText())) {
                    sets.add(item);
                }
            }
        }
        // One set of eight children is the shared reading; its passage is shown once. The CLOZE
        // item stays a single rendered question: its five blanks count toward totalQuestions.
        assertThat(sets).hasSize(1);
        assertThat(sets.get(0).path("passage").asText()).isNotBlank();
        JsonNode children = sets.get(0).path("children");
        assertThat(children).hasSize(8);
        for (JsonNode child : children) {
            int number = child.path("number").asInt();
            if (number == 8 || number == 9) {
                assertThat(child.path("type").asText()).as("question %d", number)
                        .isEqualTo("TRUE_FALSE");
            } else {
                assertThat(number).as("question %d", number).isBetween(10, 15);
                assertThat(child.path("type").asText()).as("question %d", number)
                        .isEqualTo("SINGLE_CHOICE");
            }
        }
    }

    private ResultActions create(String scope, String partCode) throws Exception {
        ObjectNode body = mapper.createObjectNode().put("kind", "VSAT").put("scope", scope)
                .put("name", "BE21 preview " + scope)
                .put("code", "B21P-" + UUID.randomUUID().toString().substring(0, 8))
                .put("subjectCode", "ANH").put("durationMinutes", 60);
        if (partCode != null) body.put("partCode", partCode);
        return mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }

    private String created(ResultActions result) throws Exception {
        return mapper.readTree(result.andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).path("id").asText();
    }

    private JsonNode preview(String id) throws Exception {
        JsonNode result = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/preview", id)
                        .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":{\"kind\":\"EXAM\"},\"seed\":" + SEED + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(result.path("status").asText()).as(result.path("issues").toString())
                .isEqualTo("GENERATED");
        return result.path("paper");
    }
}
