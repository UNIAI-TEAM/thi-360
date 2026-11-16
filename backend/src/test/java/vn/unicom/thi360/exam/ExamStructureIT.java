package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionCount").value(150));
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

    @Test
    void invalidNestedValuesAndReferencesLeaveStructureUnchanged() throws Exception {
        JsonNode before = mapper.readTree(mockMvc.perform(get(EXAM + "/structure")
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (int scenario = 0; scenario < 13; scenario++) {
            var parts = (com.fasterxml.jackson.databind.node.ArrayNode) before.path("parts").deepCopy();
            var first = (com.fasterxml.jackson.databind.node.ObjectNode) parts.get(0);
            var section = (com.fasterxml.jackson.databind.node.ObjectNode) first.path("sections").get(0);
            var block = (com.fasterxml.jackson.databind.node.ObjectNode) section.path("blocks").get(0);
            var matrix = (com.fasterxml.jackson.databind.node.ObjectNode) section.path("matrix");
            switch (scenario) {
                case 0 -> parts.addNull();
                case 1 -> ((com.fasterxml.jackson.databind.node.ArrayNode) first.path("sections")).addNull();
                case 2 -> ((com.fasterxml.jackson.databind.node.ArrayNode) first.path("options")).addNull();
                case 3 -> ((com.fasterxml.jackson.databind.node.ArrayNode) section.path("blocks")).addNull();
                case 4 -> matrix.putArray("cells").addNull();
                case 5 -> matrix.putArray("byGrade").addNull();
                case 6 -> matrix.putArray("byDifficulty").addNull();
                case 7 -> matrix.putArray("byCompetency").addNull();
                case 8 -> section.put("subjectCode", "MISSING_SUBJECT");
                case 9 -> block.put("taskTypeCode", "MISSING_TASK");
                case 10 -> section.set("knowledgeScope", mapper.readTree(
                        "{\"nodeIds\":[\"00000000-0000-7000-8200-999999999999\"],\"match\":\"ANY\"}"));
                case 11 -> section.set("knowledgeScope", mapper.readTree(
                        "{\"nodeIds\":[],\"match\":\"ANY\",\"competencyCodes\":[\"MISSING_COMPETENCY\"]}"));
                case 12 -> {
                    var option = (com.fasterxml.jackson.databind.node.ObjectNode)
                            parts.get(2).path("options").get(0);
                    option.put("kind", "COMBINATION");
                    option.put("code", "MISSING_COMBINATION");
                }
                default -> throw new AssertionError(scenario);
            }
            var request = mapper.createObjectNode();
            request.set("parts", parts);
            request.put("expectedRevision", before.path("revision").asLong());
            mockMvc.perform(put(EXAM + "/structure").with(asUser(Permissions.EXAM_MODIFY))
                            .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("EXAM_STRUCTURE_INVALID"));
        }
        JsonNode after = mapper.readTree(mockMvc.perform(get(EXAM + "/structure")
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(after).isEqualTo(before);
    }

    @Test
    void countMatrixMayExceedHundredButPercentMayNot() throws Exception {
        JsonNode before = mapper.readTree(mockMvc.perform(get(EXAM + "/structure")
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var parts = before.path("parts").deepCopy();
        var matrix = (com.fasterxml.jackson.databind.node.ObjectNode)
                parts.get(0).path("sections").get(0).path("matrix");
        matrix.put("unit", "PERCENT");
        ((com.fasterxml.jackson.databind.node.ObjectNode) matrix.path("byGrade").get(0)).put("value", 101);
        var request = mapper.createObjectNode();
        request.set("parts", parts);
        request.put("expectedRevision", before.path("revision").asLong());
        mockMvc.perform(put(EXAM + "/structure").with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_STRUCTURE_INVALID"));
        matrix.put("unit", "COUNT");
        mockMvc.perform(put(EXAM + "/structure").with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parts[0].sections[0].matrix.byGrade[0].value").value(101));
    }

    @Test
    void structureLimitsEnforcedAtBoundaries() throws Exception {
        for (String limit : new String[] {"parts", "options", "blocks"}) {
            JsonNode current = mapper.readTree(mockMvc.perform(get(EXAM + "/structure")
                            .with(asUser(Permissions.EXAM_VIEW)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            var parts = mapper.createArrayNode();
            var part = mapper.createObjectNode();
            parts.add(part);
            com.fasterxml.jackson.databind.node.ArrayNode items;
            int maximum;
            if ("parts".equals(limit)) {
                items = parts;
                maximum = 10;
            } else if ("options".equals(limit)) {
                part.set("participation", mapper.readTree("{\"mode\":\"CHOICE\",\"choose\":1}"));
                items = part.putArray("options");
                maximum = 20;
            } else {
                var section = part.putArray("sections").addObject();
                section.put("subjectCode", "TOAN");
                items = section.putArray("blocks");
                maximum = 30;
            }
            while (items.size() < maximum) {
                var item = items.addObject();
                if ("options".equals(limit)) {
                    item.put("kind", "BRANCH");
                    item.put("code", "OPTION-" + items.size());
                }
            }
            var request = mapper.createObjectNode();
            request.set("parts", parts);
            request.put("expectedRevision", current.path("revision").asLong());
            JsonNode saved = mapper.readTree(mockMvc.perform(put(EXAM + "/structure")
                            .with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                            .content(request.toString()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            var extra = items.addObject();
            if ("options".equals(limit)) {
                extra.put("kind", "BRANCH");
                extra.put("code", "OPTION-EXTRA");
            }
            request.put("expectedRevision", saved.path("revision").asLong());
            mockMvc.perform(put(EXAM + "/structure").with(asUser(Permissions.EXAM_MODIFY))
                            .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("EXAM_PATH_LIMIT_EXCEEDED"));
            JsonNode after = mapper.readTree(mockMvc.perform(get(EXAM + "/structure")
                            .with(asUser(Permissions.EXAM_VIEW)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(after.path("parts")).isEqualTo(saved.path("parts"));
            assertThat(after.path("revision")).isEqualTo(saved.path("revision"));
        }
    }

    @Test
    void templateOwnedFieldsCannotBeForgedWithoutIds() throws Exception {
        String key = java.util.UUID.randomUUID().toString();
        String create = "{\"kind\":\"HSA\",\"scope\":\"FULL\",\"name\":\"Draft\","
                + "\"code\":\"HSA-F-" + key.substring(0, 8) + "\"}";
        JsonNode exam = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams")
                        .with(asUser(Permissions.EXAM_MODIFY)).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String url = "/api/v1/admin/exams/" + exam.path("id").asText() + "/structure";
        JsonNode before = mapper.readTree(mockMvc.perform(get(url).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var parts = before.path("parts").deepCopy();
        var section = (com.fasterxml.jackson.databind.node.ObjectNode) parts.get(0).path("sections").get(0);
        var block = (com.fasterxml.jackson.databind.node.ObjectNode) section.path("blocks").get(0);
        JsonNode expectedOrder = section.path("groupOrder").deepCopy();
        JsonNode expectedGroup = block.path("templateGroupCode").deepCopy();
        JsonNode expectedLocked = block.path("answerTypeLocked").deepCopy();
        JsonNode expectedShuffle = block.path("shuffle").deepCopy();
        section.remove("id");
        section.put("groupOrder", "SHUFFLE");
        block.remove("id");
        block.put("templateGroupCode", "FORGED");
        block.put("answerTypeLocked", !expectedLocked.asBoolean());
        block.put("shuffle", "SHUFFLE");
        var extra = ((com.fasterxml.jackson.databind.node.ArrayNode) section.path("blocks")).addObject();
        extra.put("name", "Additional block");
        extra.put("templateGroupCode", "FORGED");
        extra.put("answerTypeLocked", true);
        extra.put("shuffle", "SHUFFLE");
        var request = mapper.createObjectNode();
        request.set("parts", parts);
        request.put("expectedRevision", before.path("revision").asLong());
        JsonNode saved = mapper.readTree(mockMvc.perform(put(url).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode savedSection = saved.path("parts").get(0).path("sections").get(0);
        JsonNode savedBlock = savedSection.path("blocks").get(0);
        assertThat(savedSection.path("groupOrder")).isEqualTo(expectedOrder);
        assertThat(savedBlock.path("templateGroupCode")).isEqualTo(expectedGroup);
        assertThat(savedBlock.path("answerTypeLocked")).isEqualTo(expectedLocked);
        assertThat(savedBlock.path("shuffle")).isEqualTo(expectedShuffle);
        JsonNode savedExtra = savedSection.path("blocks").get(2);
        assertThat(savedExtra.path("templateGroupCode").isMissingNode()).isTrue();
        assertThat(savedExtra.path("answerTypeLocked").asBoolean()).isFalse();
        assertThat(savedExtra.path("shuffle").isMissingNode()).isTrue();
    }
}
