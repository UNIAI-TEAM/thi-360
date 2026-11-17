package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.exam.spi.ExamKnowledgeScopes;
import vn.unicom.thi360.identity.spi.Permissions;

@Transactional
class ExamBlockRefIT extends AbstractIntegrationTest {
    private static final String ID = "00000000-0000-7000-8700-000000000001";
    private static final String URL = "/api/v1/admin/exams/" + ID + "/structure";
    private static final UUID NODE = UUID.fromString("00000000-0000-7000-8200-000000001201");
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ExamKnowledgeScopes knowledgeScopes;

    @Test
    void inheritedKnowledgeScopeFollowsSection() throws Exception {
        var before = mapper.readTree(mockMvc.perform(get(URL).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var parts = before.path("parts");
        ObjectNode section = (ObjectNode) parts.get(0).path("sections").get(0);
        String blockId = section.path("blocks").get(0).path("id").asText();
        section.set("knowledgeScope", mapper.readTree("{\"nodeIds\":[\"" + NODE + "\"]}"));
        ObjectNode request = mapper.createObjectNode();
        request.set("parts", parts);
        request.put("expectedRevision", before.path("revision").asLong());
        mockMvc.perform(put(URL).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isOk());
        Boolean inherited = jdbc.queryForObject("SELECT knowledge_inherited AND ? = ANY(knowledge_item_ids) "
                        + "FROM exam.exam_block_ref WHERE exam_id = CAST(? AS uuid) AND block_id = ? AND version_no IS NULL",
                Boolean.class, NODE, ID, blockId);
        assertThat(inherited).isTrue();
    }

    @Test
    void parentKnowledgeIncludesDescendantsAndReplacesPreviousScope() throws Exception {
        String first = "00000000-0000-7000-8200-000000001001";
        String second = "00000000-0000-7000-8200-000000001002";
        String firstLesson = "00000000-0000-7000-8200-000000001101";
        String firstLeaf = "00000000-0000-7000-8200-000000001201";
        String secondLesson = "00000000-0000-7000-8200-000000001104";
        var before = mapper.readTree(mockMvc.perform(get(URL).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var parts = before.path("parts");
        ObjectNode section = (ObjectNode) parts.get(0).path("sections").get(0);
        String blockId = section.path("blocks").get(0).path("id").asText();
        section.set("knowledgeScope", mapper.readTree("{\"nodeIds\":[\"" + first + "\"]}"));
        ObjectNode request = mapper.createObjectNode();
        request.set("parts", parts);
        request.put("expectedRevision", before.path("revision").asLong());
        var saved = mapper.readTree(mockMvc.perform(put(URL).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var firstScope = knowledgeScopes.findDraftScopes(UUID.fromString(ID)).stream()
                .filter(scope -> scope.blockId().equals(blockId)).findFirst().orElseThrow();
        assertThat(firstScope.matches(Set.of(UUID.fromString(first)))).isTrue();
        assertThat(firstScope.matches(Set.of(UUID.fromString(firstLesson)))).isTrue();
        assertThat(firstScope.matches(Set.of(UUID.fromString(firstLeaf)))).isTrue();
        assertThat(firstScope.matches(Set.of(UUID.fromString(secondLesson)))).isFalse();
        section.set("knowledgeScope", mapper.readTree("{\"nodeIds\":[\"" + second + "\",\"" + firstLeaf
                + "\"]}"));
        request.put("expectedRevision", saved.path("revision").asLong());
        mockMvc.perform(put(URL).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT ?::uuid = ANY(knowledge_item_ids) "
                        + "AND ?::uuid = ANY(knowledge_item_ids) AND NOT (?::uuid = ANY(knowledge_item_ids)) "
                        + "FROM exam.exam_block_ref WHERE exam_id = ?::uuid AND block_id = ? AND version_no IS NULL",
                Boolean.class, second, firstLeaf, first, ID, blockId)).isTrue();
        var secondScope = knowledgeScopes.findDraftScopes(UUID.fromString(ID)).stream()
                .filter(scope -> scope.blockId().equals(blockId)).findFirst().orElseThrow();
        assertThat(secondScope.matches(Set.of(UUID.fromString(secondLesson), UUID.fromString(firstLeaf)))).isTrue();
        assertThat(secondScope.matches(Set.of(UUID.fromString(secondLesson)))).isTrue();
        assertThat(secondScope.matches(Set.of(UUID.fromString(firstLeaf)))).isTrue();
    }

    @Test
    void anyMatchAcrossTwoRootsMatchesEitherSubtree() throws Exception {
        String rootA = "00000000-0000-7000-8200-000000001001";
        String rootB = "00000000-0000-7000-8200-000000001002";
        String leafA = "00000000-0000-7000-8200-000000001201";
        String lessonB = "00000000-0000-7000-8200-000000001104";
        String outside = "00000000-0000-7000-8200-000000001106";
        var before = mapper.readTree(mockMvc.perform(get(URL).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var parts = before.path("parts");
        ObjectNode section = (ObjectNode) parts.get(0).path("sections").get(0);
        String blockId = section.path("blocks").get(0).path("id").asText();
        section.set("knowledgeScope", mapper.readTree(
                "{\"nodeIds\":[\"" + rootA + "\",\"" + rootB + "\"]}"));
        ObjectNode request = mapper.createObjectNode();
        request.set("parts", parts);
        request.put("expectedRevision", before.path("revision").asLong());
        mockMvc.perform(put(URL).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isOk());
        var scope = knowledgeScopes.findDraftScopes(UUID.fromString(ID)).stream()
                .filter(s -> s.blockId().equals(blockId)).findFirst().orElseThrow();
        assertThat(scope.matches(Set.of(UUID.fromString(leafA)))).isTrue();
        assertThat(scope.matches(Set.of(UUID.fromString(lessonB)))).isTrue();
        assertThat(scope.matches(Set.of(UUID.fromString(outside)))).isFalse();
    }
}
