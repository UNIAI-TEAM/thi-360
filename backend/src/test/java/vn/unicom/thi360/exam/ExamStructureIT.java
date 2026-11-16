package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

@Transactional
class ExamStructureIT extends AbstractIntegrationTest {
    private static final String EXAM = "/api/v1/admin/exams/00000000-0000-7000-8700-000000000001";
    @Autowired private ObjectMapper mapper;

    @Test
    void incompleteDraftSavesAndYearChangeNeedsConfirmation() throws Exception {
        String before = mockMvc.perform(get(EXAM + "/structure").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long revision = mapper.readTree(before).path("revision").asLong();
        String saved = mockMvc.perform(put(EXAM + "/structure").with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parts\":[],\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parts").isEmpty())
                .andReturn().getResponse().getContentAsString();
        long nextRevision = mapper.readTree(saved).path("revision").asLong();
        mockMvc.perform(patch(EXAM).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"structureYear\":1990,\"expectedRevision\":" + nextRevision + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXAM_STRUCTURE_REPLACE_CONFIRM"));
    }


    @Test
    void roundTripSubjectMatrixKeepsTemplateFieldsAndAllowsQuotaMismatch() throws Exception {
        JsonNode before = mapper.readTree(mockMvc.perform(get(EXAM + "/structure")
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var parts = before.path("parts").deepCopy();
        var section = (com.fasterxml.jackson.databind.node.ObjectNode) parts.get(0).path("sections").get(0);
        var block = (com.fasterxml.jackson.databind.node.ObjectNode) section.path("blocks").get(0);
        JsonNode originalGroupOrder = section.path("groupOrder").deepCopy();
        JsonNode originalShuffle = block.path("shuffle").deepCopy();
        JsonNode originalLocked = block.path("answerTypeLocked").deepCopy();
        JsonNode originalGroup = block.path("templateGroupCode").deepCopy();
        section.put("groupOrder", "SHUFFLE".equals(originalGroupOrder.asText()) ? "FIXED" : "SHUFFLE");
        block.put("shuffle", "SHUFFLE");
        block.put("answerTypeLocked", !originalLocked.asBoolean());
        block.put("templateGroupCode", "FORGED");
        ((com.fasterxml.jackson.databind.node.ObjectNode) section.path("answerQuotas").path("items").get(0))
                .put("value", 1);
        section.set("knowledgeScope", mapper.readTree(
                "{\"nodeIds\":[\"00000000-0000-7000-8200-000000001201\"],\"match\":\"ALL\"}"));
        JsonNode expectedQuotas = section.path("answerQuotas").deepCopy();
        JsonNode expectedMatrix = section.path("matrix").deepCopy();
        JsonNode expectedScope = section.path("knowledgeScope").deepCopy();
        var request = mapper.createObjectNode();
        request.set("parts", parts);
        request.put("expectedRevision", before.path("revision").asLong());
        mockMvc.perform(put(EXAM + "/structure").with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isOk());
        JsonNode after = mapper.readTree(mockMvc.perform(get(EXAM + "/structure")
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode savedSection = after.path("parts").get(0).path("sections").get(0);
        JsonNode savedBlock = savedSection.path("blocks").get(0);
        assertThat(savedSection.path("answerQuotas")).isEqualTo(expectedQuotas);
        assertThat(savedSection.path("matrix").equals((a, b) ->
                a.isNumber() && b.isNumber()
                        ? a.decimalValue().compareTo(b.decimalValue())
                        : a.equals(b) ? 0 : 1, expectedMatrix)).isTrue();
        assertThat(savedSection.path("knowledgeScope").path("nodeIds")).isEqualTo(expectedScope.path("nodeIds"));
        assertThat(savedSection.path("knowledgeScope").path("match")).isEqualTo(expectedScope.path("match"));
        assertThat(savedSection.path("groupOrder")).isEqualTo(originalGroupOrder);
        assertThat(savedBlock.path("shuffle")).isEqualTo(originalShuffle);
        assertThat(savedBlock.path("answerTypeLocked")).isEqualTo(originalLocked);
        assertThat(savedBlock.path("templateGroupCode")).isEqualTo(originalGroup);
        assertThat(savedBlock.path("knowledgeScope").isMissingNode()).isTrue();
    }
    @Test
    void rejectsDescendantExpansion() throws Exception {
        String before = mockMvc.perform(get(EXAM + "/structure").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode root = mapper.readTree(before);
        ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("parts").get(0).path("sections").get(0))
                .set("knowledgeScope", mapper.readTree("{\"nodeIds\":[],\"match\":\"ANY\",\"includeDescendants\":true}"));
        var request = mapper.createObjectNode();
        request.set("parts", root.path("parts"));
        request.put("expectedRevision", root.path("revision").asLong());
        mockMvc.perform(put(EXAM + "/structure").with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_STRUCTURE_INVALID"));
    }
}
