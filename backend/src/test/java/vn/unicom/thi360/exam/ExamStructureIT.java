package vn.unicom.thi360.exam;

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
