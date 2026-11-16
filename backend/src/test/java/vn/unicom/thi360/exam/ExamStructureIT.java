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
        // Mất id đồng nghĩa mất danh tính nút: các trường thuộc mẫu bị xóa, không theo vị trí.
        section.remove("id");
        section.put("groupOrder", "SHUFFLE");
        block.remove("id");
        block.put("templateGroupCode", "FORGED");
        block.put("answerTypeLocked", true);
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
        assertThat(savedSection.path("groupOrder").isMissingNode()).isTrue();
        assertThat(savedBlock.path("templateGroupCode").isMissingNode()).isTrue();
        assertThat(savedBlock.path("answerTypeLocked").asBoolean()).isFalse();
        assertThat(savedBlock.path("shuffle").isMissingNode()).isTrue();
        JsonNode savedExtra = savedSection.path("blocks").get(2);
        assertThat(savedExtra.path("name").asText()).isEqualTo("Additional block");
        assertThat(savedExtra.path("templateGroupCode").isMissingNode()).isTrue();
        assertThat(savedExtra.path("answerTypeLocked").asBoolean()).isFalse();
        assertThat(savedExtra.path("shuffle").isMissingNode()).isTrue();
    }

    @Test
    void retainedNodesKeepTemplateValuesAcrossDeleteAndReorder() throws Exception {
        JsonNode before = mapper.readTree(mockMvc.perform(get(EXAM + "/structure")
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var parts = (com.fasterxml.jackson.databind.node.ArrayNode) before.path("parts").deepCopy();

        var math = (com.fasterxml.jackson.databind.node.ObjectNode) parts.get(0)
                .path("sections").get(0);
        var mathBlocks = (com.fasterxml.jackson.databind.node.ArrayNode) math.path("blocks");
        JsonNode single = mathBlocks.get(0).deepCopy();
        JsonNode context = mathBlocks.get(1).deepCopy();
        mathBlocks.removeAll();
        mathBlocks.add(context);
        mathBlocks.add(single);

        var van = (com.fasterxml.jackson.databind.node.ObjectNode) parts.get(1)
                .path("sections").get(0);
        var vanBlocks = (com.fasterxml.jackson.databind.node.ArrayNode) van.path("blocks");
        JsonNode wordUsage = vanBlocks.get(0).deepCopy();
        vanBlocks.remove(0);
        vanBlocks.add(wordUsage);

        var scienceOption = (com.fasterxml.jackson.databind.node.ObjectNode) parts.get(2)
                .path("options").get(0);
        var science = (com.fasterxml.jackson.databind.node.ArrayNode) scienceOption.path("sections");
        JsonNode physics = science.get(0).deepCopy();
        science.remove(0);
        science.add(physics);

        var anhSection = (com.fasterxml.jackson.databind.node.ObjectNode) parts.get(2)
                .path("options").get(1).path("sections").get(0);
        var anhBlocks = (com.fasterxml.jackson.databind.node.ArrayNode) anhSection.path("blocks");
        JsonNode anh0 = anhBlocks.get(0).deepCopy();
        JsonNode anh1 = anhBlocks.get(1).deepCopy();
        JsonNode anh2 = anhBlocks.get(2).deepCopy();
        JsonNode anh3 = anhBlocks.get(3).deepCopy();
        anhBlocks.removeAll();
        anhBlocks.add(anh3);
        anhBlocks.add(anh2);
        anhBlocks.add(anh1);
        anhBlocks.add(anh0);

        // Giá trị giả mạo trên nút giữ id phải bị bỏ qua.
        ((com.fasterxml.jackson.databind.node.ObjectNode) mathBlocks.get(0))
                .put("templateGroupCode", "FORGED").put("answerTypeLocked", false)
                .put("shuffle", "SHUFFLE");
        science.forEach(s -> ((com.fasterxml.jackson.databind.node.ObjectNode) s)
                .put("groupOrder", "SHUFFLE"));

        var request = mapper.createObjectNode();
        request.set("parts", parts);
        request.put("expectedRevision", before.path("revision").asLong());
        JsonNode saved = mapper.readTree(mockMvc.perform(put(EXAM + "/structure")
                        .with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        JsonNode savedMath = saved.path("parts").get(0).path("sections").get(0).path("blocks");
        assertThat(savedMath.get(0).path("id").asText()).isEqualTo(context.path("id").asText());
        assertThat(savedMath.get(0).path("templateGroupCode").asText()).isEqualTo("HSA_MATH_CONTEXT");
        assertThat(savedMath.get(0).path("answerTypeLocked").asBoolean()).isTrue();
        assertThat(savedMath.get(0).path("shuffle").isMissingNode()).isTrue();
        assertThat(savedMath.get(1).path("id").asText()).isEqualTo(single.path("id").asText());
        assertThat(savedMath.get(1).path("templateGroupCode").asText()).isEqualTo("HSA_MATH_SINGLE");
        assertThat(savedMath.get(1).path("answerTypeLocked").asBoolean()).isFalse();

        JsonNode savedVan = saved.path("parts").get(1).path("sections").get(0);
        assertThat(savedVan.path("groupOrder").asText()).isEqualTo("SHUFFLE");
        JsonNode savedVanBlocks = savedVan.path("blocks");
        assertThat(savedVanBlocks.get(0).path("id").asText()).isEqualTo(
                vanBlocks.get(0).path("id").asText());
        assertThat(savedVanBlocks.get(0).path("templateGroupCode").asText())
                .isEqualTo("HSA_VN_PASSAGE");
        assertThat(savedVanBlocks.get(1).path("templateGroupCode").asText())
                .isEqualTo("HSA_VN_WORD_USAGE");
        assertThat(savedVanBlocks.get(1).path("answerTypeLocked").asBoolean()).isTrue();

        JsonNode savedScience = saved.path("parts").get(2).path("options").get(0).path("sections");
        assertThat(savedScience).hasSize(5);
        String[] subjects = {"HOA", "SINH", "SU", "DIA", "LY"};
        String[] groups = {"HSA_HOA_REMAINDER", "HSA_SINH_REMAINDER", "HSA_SU_REMAINDER",
                "HSA_DIA_REMAINDER", "HSA_LY_REMAINDER"};
        for (int i = 0; i < 5; i++) {
            assertThat(savedScience.get(i).path("subjectCode").asText()).isEqualTo(subjects[i]);
            assertThat(savedScience.get(i).path("groupOrder").asText()).isEqualTo("FIXED");
            assertThat(savedScience.get(i).path("blocks").get(0).path("templateGroupCode").asText())
                    .isEqualTo(groups[i]);
        }

        JsonNode savedAnh = saved.path("parts").get(2).path("options").get(1).path("sections")
                .get(0).path("blocks");
        String[] anhGroups = {"HSA_EN_READING", "HSA_EN_DIALOGUE", "HSA_EN_ANTONYM", "HSA_EN_SYNONYM"};
        for (int i = 0; i < 4; i++) {
            assertThat(savedAnh.get(i).path("templateGroupCode").asText()).isEqualTo(anhGroups[i]);
            assertThat(savedAnh.get(i).path("answerTypeLocked").asBoolean()).isTrue();
        }

        // Xóa khối đầu tiên của mẫu (SINGLE), giữ id khối thứ hai (CONTEXT): khối giữ lại
        // vẫn mang giá trị mẫu của chính nó dù đứng đầu mảng.
        JsonNode current = mapper.readTree(mockMvc.perform(get(EXAM + "/structure")
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var shrunk = (com.fasterxml.jackson.databind.node.ArrayNode)
                current.path("parts").get(0).path("sections").get(0).path("blocks");
        String contextId = shrunk.get(0).path("id").asText();
        shrunk.remove(1);
        var next = mapper.createObjectNode();
        next.set("parts", current.path("parts"));
        next.put("expectedRevision", current.path("revision").asLong());
        mockMvc.perform(put(EXAM + "/structure").with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(next.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parts[0].sections[0].blocks[0].id").value(contextId))
                .andExpect(jsonPath("$.parts[0].sections[0].blocks[0].templateGroupCode")
                        .value("HSA_MATH_CONTEXT"))
                .andExpect(jsonPath("$.parts[0].sections[0].blocks[0].answerTypeLocked").value(true))
                .andExpect(jsonPath("$.parts[0].sections[0].blocks").value(
                        org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void countsByPickNullElementRejectedButMissingListAllowed() throws Exception {
        JsonNode before = mapper.readTree(mockMvc.perform(get(EXAM + "/structure")
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var parts = before.path("parts").deepCopy();
        var option = (com.fasterxml.jackson.databind.node.ObjectNode)
                parts.get(2).path("options").get(0);
        ((com.fasterxml.jackson.databind.node.ArrayNode) option.putArray("countsByPick"))
                .add(17).addNull().add(16);
        var request = mapper.createObjectNode();
        request.set("parts", parts);
        request.put("expectedRevision", before.path("revision").asLong());
        mockMvc.perform(put(EXAM + "/structure").with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_STRUCTURE_INVALID"));
        JsonNode unchanged = mapper.readTree(mockMvc.perform(get(EXAM + "/structure")
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(unchanged).isEqualTo(before);

        option.remove("countsByPick");
        mockMvc.perform(put(EXAM + "/structure").with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void pathLimitRejectsPutWithoutMutation() throws Exception {
        JsonNode before = mapper.readTree(mockMvc.perform(get(EXAM + "/structure")
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var parts = mapper.createArrayNode();
        for (int i = 0; i < 8; i++) {
            var part = parts.addObject();
            part.set("participation", mapper.readTree("{\"mode\":\"CHOICE\",\"choose\":1}"));
            var options = part.putArray("options");
            options.addObject().put("kind", "BRANCH").put("code", "A");
            options.addObject().put("kind", "BRANCH").put("code", "B");
        }
        var request = mapper.createObjectNode();
        request.set("parts", parts);
        request.put("expectedRevision", before.path("revision").asLong());
        mockMvc.perform(put(EXAM + "/structure").with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_PATH_LIMIT_EXCEEDED"));
        JsonNode after = mapper.readTree(mockMvc.perform(get(EXAM + "/structure")
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(after).isEqualTo(before);
    }

    @Test
    void templateReplacementRequiresConfirmationAndMatchingKind() throws Exception {
        JsonNode before = mapper.readTree(mockMvc.perform(get(EXAM + "/structure")
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode edited = mapper.readTree(mockMvc.perform(put(EXAM + "/structure")
                        .with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parts\":[],\"expectedRevision\":" + before.path("revision").asLong() + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long revision = edited.path("revision").asLong();
        String url = EXAM + "/structure/template";
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateCode\":\"HSA_FULL\",\"confirmReplace\":false,\"expectedRevision\":"
                                + revision + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXAM_STRUCTURE_REPLACE_CONFIRM"));
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateCode\":\"QDA_FULL\",\"confirmReplace\":true,\"expectedRevision\":"
                                + revision + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_TEMPLATE_MISMATCH"));
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateCode\":\"HSA_FULL\",\"confirmReplace\":true,\"expectedRevision\":"
                                + revision + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateCode").value("HSA_FULL"))
                .andExpect(jsonPath("$.parts[0].sections[0].subjectCode").value("TOAN"));
    }
}
