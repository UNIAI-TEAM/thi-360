package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

@Transactional
class ExamBlockRefIT extends AbstractIntegrationTest {
    private static final String ID = "00000000-0000-7000-8700-000000000001";
    private static final String URL = "/api/v1/admin/exams/" + ID + "/structure";
    private static final UUID NODE = UUID.fromString("00000000-0000-7000-8200-000000001201");
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void inheritedKnowledgeScopeFollowsSection() throws Exception {
        var before = mapper.readTree(mockMvc.perform(get(URL).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var parts = before.path("parts");
        ObjectNode section = (ObjectNode) parts.get(0).path("sections").get(0);
        String blockId = section.path("blocks").get(0).path("id").asText();
        section.set("knowledgeScope", mapper.readTree("{\"nodeIds\":[\"" + NODE + "\"],\"match\":\"ANY\"}"));
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
}
