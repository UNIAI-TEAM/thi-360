package vn.unicom.thi360.questionbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

@Transactional
class QuestionChildKnowledgeIT extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    @Test
    void nullInheritsParentKnowledgeButEmptyOverrideDoesNot() throws Exception {
        String body = """
                {"type":"QUESTION_SET","knowledge":[{"nodeId":"%s","primary":true}],
                 "body":{"type":"QUESTION_SET","passage":"<p>Context</p>","children":[
                 {"type":"SHORT_ANSWER","content":"<p>A</p>","knowledgeItemIds":null,
                  "body":{"type":"SHORT_ANSWER","answers":["a"]}},
                 {"type":"SHORT_ANSWER","content":"<p>B</p>","knowledgeItemIds":[],
                  "body":{"type":"SHORT_ANSWER","answers":["b"]}}]}}
                """.formatted(QuestionTestSupport.KNOWLEDGE_LEAF_ID);
        var response = mockMvc.perform(post("/api/v1/admin/questions")
                        .with(asUser(Permissions.QUESTION_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        JsonNode result = mapper.readTree(response.getResponse().getContentAsString());
        UUID parent = UUID.fromString(result.at("/summary/id").asText());
        UUID inherited = UUID.fromString(result.at("/children/0/summary/id").asText());
        UUID explicitEmpty = UUID.fromString(result.at("/children/1/summary/id").asText());
        assertKnowledge(inherited, 1);
        assertKnowledge(explicitEmpty, 0);
        assertThat(result.at("/snapshot/body/children/0/id").asText()).isEqualTo(inherited.toString());
        assertThat(result.at("/snapshot/body/children/1/id").asText()).isEqualTo(explicitEmpty.toString());
        Long revision = jdbc.queryForObject("SELECT revision FROM questionbank.question WHERE id = ?", Long.class, parent);
        mockMvc.perform(post("/api/v1/admin/questions/{id}/versions/1/restore", parent)
                        .with(asUser(Permissions.QUESTION_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isOk());
        assertKnowledge(inherited, 1);
        assertKnowledge(explicitEmpty, 0);
    }

    private void assertKnowledge(UUID question, int expected) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM questionbank.question_knowledge WHERE question_id = ? AND knowledge_item_id = ?",
                Integer.class, question, QuestionTestSupport.KNOWLEDGE_LEAF_ID);
        assertThat(count).isEqualTo(expected);
    }
}
