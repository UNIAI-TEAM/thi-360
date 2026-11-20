package vn.unicom.thi360.exam.followup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/** FU (UNI-2700) API contract: preview request shape, OpenAPI types, template part types, access, structure PUT. */
@Transactional
@ActiveProfiles({"api", "openapi", "test"})
class ExamApiFollowUpIT extends AbstractIntegrationTest {
    private static final String EXAM = "/api/v1/admin/exams/00000000-0000-7000-8700-000000000001";

    @Autowired private ObjectMapper mapper;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    /** An exam with an empty structure: its target preview answers 200 (FAILED, INFO_MISSING), never 404. */
    private String emptyExamUrl() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam (id,code,code_normalized,name,scope,timing) "
                + "VALUES (?,?,?,'Legacy preview','FULL','SHARED')", id, id.toString(), id.toString());
        jdbc.update("INSERT INTO exam.exam_structure (exam_id,parts,structure_hash) VALUES (?,'{\"parts\":[]}'::jsonb,'empty')", id);
        return "/api/v1/admin/exams/" + id + "/preview";
    }

    @Test
    void legacyPreviewFieldsRejected() throws Exception {
        String url = emptyExamUrl();
        // The retired root fields are answered with 400 whatever else the body carries.
        for (String body : List.of(
                "{\"pathId\":\"default\",\"seed\":1}",
                "{\"pathId\":\"default\",\"seed\":1,\"subjectCodes\":[\"TOAN\"]}",
                "{\"subjectCodes\":[\"TOAN\"],\"seed\":1}",
                "{\"target\":{\"kind\":\"EXAM\"},\"seed\":1,\"pathId\":\"default\"}",
                "{\"target\":{\"kind\":\"EXAM\"},\"seed\":1,\"subjectCodes\":[\"TOAN\"]}")) {
            mockMvc.perform(post(url).with(asUser(Permissions.EXAM_VIEW))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        // The explicit target is the one accepted shape.
        mockMvc.perform(post(url).with(asUser(Permissions.EXAM_VIEW))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"target\":{\"kind\":\"EXAM\"},\"seed\":1}"))
                .andExpect(status().isOk());
    }

    @Test
    void openApiHasTypedStructureFields() throws Exception {
        JsonNode schemas = mapper.readTree(mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("components").path("schemas");
        assertThat(schemas.path("ExamKindOption").path("properties").path("trueFalseScoring").path("$ref").asText())
                .isEqualTo("#/components/schemas/ExamTrueFalseScoring");
        assertThat(schemas.path("ExamTrueFalseScoring").path("properties").fieldNames())
                .toIterable().contains("method", "table", "statementPoints");
        assertThat(schemas.path("ExamTemplateResponse").path("properties").path("structure").path("$ref").asText())
                .isEqualTo("#/components/schemas/ExamStructureDocument");
        assertThat(schemas.path("ExamStructureDocument").path("properties").path("parts").path("items").path("$ref")
                .asText()).isEqualTo("#/components/schemas/ExamPart");
        assertThat(schemas.path("ExamStructureSummaryRow").path("properties").path("children").path("items")
                .path("$ref").asText()).isEqualTo("#/components/schemas/ExamStructureSummaryRow");
        assertThat(referencesJsonNode(schemas.path("ExamKindOption"))).isFalse();
        assertThat(referencesJsonNode(schemas.path("ExamTemplateResponse"))).isFalse();
    }

    private static boolean referencesJsonNode(JsonNode schema) {
        return schema.toString().contains("#/components/schemas/JsonNode");
    }

    @Test
    void templatePartQuestionTypesExposed() throws Exception {
        JsonNode templates = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exam-templates")
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        java.util.Map<String, JsonNode> byCode = new java.util.LinkedHashMap<>();
        for (JsonNode template : templates) {
            byCode.put(template.path("code").asText(), template);
            for (JsonNode part : template.path("parts")) {
                int sum = 0;
                for (JsonNode entry : part.path("questionTypes")) {
                    int count = entry.path("questionCount").asInt();
                    assertThat(count).as("%s part %s type %s count > 0",
                            template.path("code").asText(), part.path("name").asText(), entry.path("questionType").asText())
                            .isPositive();
                    sum += count;
                }
                if (part.hasNonNull("questionCount")) {
                    assertThat(sum).as("%s part %s questionTypes sum <= part.questionCount",
                            template.path("code").asText(), part.path("name").asText())
                            .isLessThanOrEqualTo(part.path("questionCount").asInt());
                }
            }
        }
        JsonNode math = byCode.get("SP2E_MATH_2026");
        assertThat(math).as("SP2E_MATH_2026 is listed").isNotNull();
        JsonNode parts = math.path("parts");
        assertThat(parts).hasSize(math.path("structure").path("parts").size());
        for (int i = 0; i < parts.size(); i++) {
            assertThat(parts.get(i).path("name").asText())
                    .isEqualTo(math.path("structure").path("parts").get(i).path("name").asText());
        }
        assertThat(typeCounts(parts.get(0))).containsExactly("SINGLE_CHOICE=20", "MULTIPLE_CHOICE=5");
        assertThat(typeCounts(parts.get(parts.size() - 1))).containsExactly("SHORT_ANSWER=10");

        JsonNode bcaFull = byCode.get("BCA_FULL");
        assertThat(bcaFull).isNotNull();
        assertThat(typeCounts(bcaFull.path("parts").get(0))).containsExactly("ESSAY=1");
        assertThat(typeCounts(bcaFull.path("parts").get(1))).containsExactly(
                "SINGLE_CHOICE=44", "QUESTION_SET=16", "SHORT_ANSWER=5");
        assertThat(typeCounts(bcaFull.path("parts").get(2))).isEmpty();

        JsonNode bcaPartChoice = byCode.get("BCA_PART_TN_TU_CHON");
        assertThat(bcaPartChoice).isNotNull();
        assertThat(typeCounts(bcaPartChoice.path("parts").get(0))).isEmpty();

        JsonNode qdaFull = byCode.get("QDA_FULL");
        assertThat(qdaFull).isNotNull();
        assertThat(typeCounts(qdaFull.path("parts").get(0))).containsExactly(
                "SINGLE_CHOICE=32", "QUESTION_SET=3", "SHORT_ANSWER=15");
        assertThat(typeCounts(qdaFull.path("parts").get(1))).containsExactly(
                "SINGLE_CHOICE=25", "QUESTION_SET=25");
        assertThat(typeCounts(qdaFull.path("parts").get(2))).isEmpty();

        JsonNode hsaFull = byCode.get("HSA_FULL");
        assertThat(hsaFull).isNotNull();
        assertThat(typeCounts(hsaFull.path("parts").get(0))).containsExactly(
                "SINGLE_CHOICE=32", "SHORT_ANSWER=15", "QUESTION_SET=3");
        assertThat(typeCounts(hsaFull.path("parts").get(1))).containsExactly(
                "SINGLE_CHOICE=25", "QUESTION_SET=25");
        assertThat(typeCounts(hsaFull.path("parts").get(2))).isEmpty();

        JsonNode vactReview = byCode.get("VACT_REVIEW");
        assertThat(vactReview).isNotNull();
        assertThat(typeCounts(vactReview.path("parts").get(0))).isEmpty();
    }

    private static List<String> typeCounts(JsonNode part) {
        List<String> counts = new ArrayList<>();
        part.path("questionTypes").forEach(entry ->
                counts.add(entry.path("questionType").asText() + "=" + entry.path("questionCount").asInt()));
        return counts;
    }

    @Test
    void accessUnchanged() throws Exception {
        String body = "{\"target\":{\"kind\":\"EXAM\"},\"seed\":1}";
        mockMvc.perform(post(EXAM + "/preview").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(EXAM + "/preview").with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        String structure = "{\"parts\":[],\"expectedRevision\":0}";
        mockMvc.perform(put(EXAM + "/structure").contentType(MediaType.APPLICATION_JSON).content(structure))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(EXAM + "/structure").with(asUser(Permissions.EXAM_VIEW))
                        .contentType(MediaType.APPLICATION_JSON).content(structure))
                .andExpect(status().isForbidden());
        String overview = "{\"overview\":[],\"expectedRevision\":0}";
        mockMvc.perform(put(EXAM + "/overview").contentType(MediaType.APPLICATION_JSON).content(overview))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(EXAM + "/overview").with(asUser(Permissions.EXAM_VIEW))
                        .contentType(MediaType.APPLICATION_JSON).content(overview))
                .andExpect(status().isForbidden());
        String template = "{\"templateCode\":\"HSA_FULL\",\"expectedRevision\":0}";
        mockMvc.perform(post(EXAM + "/structure/template").contentType(MediaType.APPLICATION_JSON).content(template))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(EXAM + "/structure/template").with(asUser(Permissions.EXAM_VIEW))
                        .contentType(MediaType.APPLICATION_JSON).content(template))
                .andExpect(status().isForbidden());
    }

    /** CR-1:5: a client that omits the empty arrays the server returns as [] saves exactly the same structure. */
    @Test
    void structurePutTreatsOmittedArraysAsEmpty() throws Exception {
        JsonNode before = mapper.readTree(mockMvc.perform(get(EXAM + "/structure").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode parts = before.path("parts").deepCopy();
        int[] removed = {0};
        removeEmptyArrays(parts, removed);
        assertThat(removed[0]).as("the seeded structure has empty arrays to omit").isPositive();
        ObjectNode request = mapper.createObjectNode();
        request.set("parts", parts);
        request.put("expectedRevision", before.path("revision").asLong());
        mockMvc.perform(put(EXAM + "/structure").with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
        JsonNode after = mapper.readTree(mockMvc.perform(get(EXAM + "/structure").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(after.path("parts")).isEqualTo(before.path("parts"));
    }

    private static void removeEmptyArrays(JsonNode node, int[] removed) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            List<String> names = new ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                JsonNode child = object.get(name);
                if (child.isArray() && ((ArrayNode) child).isEmpty()) {
                    object.remove(name);
                    removed[0]++;
                } else {
                    removeEmptyArrays(child, removed);
                }
            }
        } else if (node.isArray()) {
            node.forEach(child -> removeEmptyArrays(child, removed));
        }
    }
}
