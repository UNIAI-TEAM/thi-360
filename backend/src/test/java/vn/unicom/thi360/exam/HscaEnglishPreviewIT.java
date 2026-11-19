package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * BE-18 row 13 through preview (R1-1): the H-SCA English listening and reading PART exams generate a paper from the
 * dev seed data, and the audio condition of the section decides what was selected. Audio is read from the stored
 * serving version of every selected ROOT (a single question or the root of a set); children carry no files.
 */
@Transactional
class HscaEnglishPreviewIT extends AbstractIntegrationTest {
    private static final long SEED = 20261118L;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void listeningPartSelectsThirtyFiveQuestionsAndEveryRootHasAudio() throws Exception {
        List<Root> roots = new ArrayList<>();
        assertThat(generatedQuestions(createPart("P1"), roots)).isEqualTo(35);
        assertThat(roots).isNotEmpty();
        assertThat(roots).as("roots with audio").allMatch(root -> hasAudio(root));
        assertThat(roots).as("a listening paper holds question sets as well as single questions")
                .anyMatch(Root::set).anyMatch(root -> !root.set());
    }

    @Test
    void readingPartSelectsFortyQuestionsAndNoRootHasAudio() throws Exception {
        List<Root> roots = new ArrayList<>();
        assertThat(generatedQuestions(createPart("P2"), roots)).isEqualTo(40);
        assertThat(roots).isNotEmpty();
        assertThat(roots).as("roots with audio").noneMatch(root -> hasAudio(root));
        assertThat(roots).as("a reading paper holds question sets").anyMatch(Root::set);
    }

    private record Root(UUID id, int version, boolean set) {}

    private String createPart(String partCode) throws Exception {
        ObjectNode body = mapper.createObjectNode().put("kind", "HSCA").put("scope", "PART")
                .put("name", "BE-18 preview " + partCode).put("code", "B18P-" + UUID.randomUUID().toString().substring(0, 8))
                .put("partCode", partCode).put("subjectCode", "ANH").put("durationMinutes", 40);
        JsonNode exam = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return exam.path("id").asText();
    }

    /** Previews the exam with the fixed seed, collects the selected roots and returns the number of questions. */
    private int generatedQuestions(String examId, List<Root> roots) throws Exception {
        JsonNode result = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/preview", examId)
                        .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":{\"kind\":\"EXAM\"},\"seed\":" + SEED + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(result.path("status").asText()).as(result.path("issues").toString()).isEqualTo("GENERATED");
        JsonNode paper = result.path("paper");
        int questions = 0;
        for (JsonNode part : paper.path("parts")) {
            for (JsonNode block : part.path("blocks")) {
                for (JsonNode item : block.path("items")) {
                    if ("SET".equals(item.path("kind").asText())) {
                        roots.add(new Root(UUID.fromString(item.path("setId").asText()), item.path("version").asInt(), true));
                        questions += item.path("children").size();
                    } else {
                        JsonNode question = item.path("question");
                        roots.add(new Root(UUID.fromString(question.path("questionId").asText()),
                                question.path("version").asInt(), false));
                        questions++;
                    }
                }
            }
        }
        assertThat(paper.path("totalQuestions").asInt()).isEqualTo(questions);
        return questions;
    }

    private boolean hasAudio(Root root) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (select 1 from questionbank.question_version v
                    cross join lateral jsonb_array_elements_text(coalesce(v.snapshot -> 'attachmentIds', '[]'::jsonb)) a(id)
                    join questionbank.question_asset qa on qa.id = a.id::uuid
                    where v.question_id = ? and v.version_no = ? and lower(qa.mime) like 'audio/%')
                """, Boolean.class, root.id(), root.version()));
    }
}
