package vn.unicom.thi360.questionbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    @Test
    void childOverridesRejectUnknownWrongGroupInactiveAndDuplicatesBeforeProjection() throws Exception {
        var request = (com.fasterxml.jackson.databind.node.ObjectNode)
                mapper.readTree(QuestionTestSupport.createQuestionSetRequest());
        var created = mockMvc.perform(post("/api/v1/admin/questions").with(asUser(Permissions.QUESTION_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn();
        UUID parent = UUID.fromString(mapper.readTree(created.getResponse().getContentAsString())
                .at("/summary/id").asText());
        request.put("expectedRevision", jdbc.queryForObject(
                "SELECT revision FROM questionbank.question WHERE id = ?", Long.class, parent));
        String[] fields = {"gradeItemId", "levelItemId", "knowledgeItemIds"};
        String[] inactive = {"00000000-0000-7000-8200-000000000302",
                "00000000-0000-7000-8200-000000000402", "00000000-0000-7000-8200-000000001002"};
        for (String id : inactive) {
            jdbc.update("UPDATE catalog.catalog_item SET status = 2 WHERE id = ?", UUID.fromString(id));
        }
        for (int i = 0; i < fields.length; i++) {
            for (String bad : new String[] {UUID.randomUUID().toString(),
                    QuestionTestSupport.SUBJECT_TOAN_ID.toString(), inactive[i]}) {
                var candidate = request.deepCopy();
                var child = (com.fasterxml.jackson.databind.node.ObjectNode) candidate.at("/body/children/0");
                if (i == 2) {
                    child.putArray(fields[i]).add(bad);
                } else {
                    child.put(fields[i], bad);
                }
                mockMvc.perform(put("/api/v1/admin/questions/{id}", parent)
                                .with(asUser(Permissions.QUESTION_MODIFY))
                                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(candidate)))
                        .andExpect(status().isUnprocessableEntity())
                        .andExpect(jsonPath("$.code").value("QUESTION_BODY_INVALID"));
            }
        }
        var duplicate = request.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) duplicate.at("/body/children/0"))
                .putArray("knowledgeItemIds").add(QuestionTestSupport.KNOWLEDGE_LEAF_ID.toString())
                .add(QuestionTestSupport.KNOWLEDGE_LEAF_ID.toString());
        mockMvc.perform(put("/api/v1/admin/questions/{id}", parent).with(asUser(Permissions.QUESTION_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(duplicate)))
                .andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM questionbank.question WHERE parent_id = ? AND deleted_at IS NULL",
                Integer.class, parent)).isEqualTo(2);
    }

    @Test
    void multiplePrimaryKnowledgeRefsAreRejectedWithoutChangingRows() throws Exception {
        var request = (com.fasterxml.jackson.databind.node.ObjectNode)
                mapper.readTree(QuestionTestSupport.createQuestionSetRequest());
        var created = mockMvc.perform(post("/api/v1/admin/questions").with(asUser(Permissions.QUESTION_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn();
        UUID parent = UUID.fromString(mapper.readTree(created.getResponse().getContentAsString())
                .at("/summary/id").asText());
        request.put("expectedRevision", jdbc.queryForObject(
                "SELECT revision FROM questionbank.question WHERE id = ?", Long.class, parent));
        var refs = request.putArray("knowledge");
        refs.addObject().put("nodeId", QuestionTestSupport.KNOWLEDGE_LEAF_ID.toString()).put("primary", true);
        refs.addObject().put("nodeId", QuestionTestSupport.KNOWLEDGE_CHAPTER_ID.toString()).put("primary", true);
        mockMvc.perform(put("/api/v1/admin/questions/{id}", parent).with(asUser(Permissions.QUESTION_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("QUESTION_BODY_INVALID"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM questionbank.question_knowledge WHERE question_id = ?",
                Integer.class, parent)).isZero();
    }

    @Test
    void readyRejectsOverrideDeactivatedAfterSave() throws Exception {
        var request = (com.fasterxml.jackson.databind.node.ObjectNode)
                mapper.readTree(QuestionTestSupport.createQuestionSetRequest());
        UUID grade = UUID.fromString("00000000-0000-7000-8200-000000000302");
        ((com.fasterxml.jackson.databind.node.ObjectNode) request.at("/body/children/0"))
                .put("gradeItemId", grade.toString());
        var created = mockMvc.perform(post("/api/v1/admin/questions").with(asUser(Permissions.QUESTION_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn();
        UUID parent = UUID.fromString(mapper.readTree(created.getResponse().getContentAsString())
                .at("/summary/id").asText());
        jdbc.update("UPDATE catalog.catalog_item SET status = 2 WHERE id = ?", grade);
        long revision = jdbc.queryForObject("SELECT revision FROM questionbank.question WHERE id = ?", Long.class, parent);
        mockMvc.perform(post("/api/v1/admin/questions/{id}/ready", parent).with(asUser(Permissions.QUESTION_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("QUESTION_BODY_INVALID"));
    }

    private void assertKnowledge(UUID question, int expected) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM questionbank.question_knowledge WHERE question_id = ? AND knowledge_item_id = ?",
                Integer.class, question, QuestionTestSupport.KNOWLEDGE_LEAF_ID);
        assertThat(count).isEqualTo(expected);
    }
}
