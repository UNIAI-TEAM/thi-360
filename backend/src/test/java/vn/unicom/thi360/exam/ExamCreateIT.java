package vn.unicom.thi360.exam;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class ExamCreateIT extends AbstractIntegrationTest {
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void allKindsAllowedScopes() throws Exception {
        String[][] cases = {
                {"HSA", "FULL"}, {"HSA", "PART"}, {"QDA", "FULL"},
                {"BCA", "FULL"}, {"VACT", "FULL"}, {"SPT", "SUBJECT"},
                {"SP2E", "SUBJECT"}, {"THPTQG", "SUBJECT"}
        };
        for (String[] c : cases) {
            String code = c[0] + "-" + UUID.randomUUID().toString().substring(0, 8);
            String selection = switch (c[1]) {
                case "PART" -> ",\"partCode\":\"TOAN\"";
                case "SUBJECT" -> ",\"subjectCode\":\"TOAN\"";
                default -> "";
            };
            mockMvc.perform(post("/api/v1/admin/exams")
                            .with(asUser(Permissions.EXAM_MODIFY))
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"kind\":\"" + c[0] + "\",\"scope\":\"" + c[1]
                                    + "\",\"name\":\"Draft\",\"code\":\"" + code + "\"" + selection + "}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.kind").value(c[0]))
                    .andExpect(jsonPath("$.scope").value(c[1]));
        }
    }

    @Test
    void createsHsaDraftFromLatestTemplate() throws Exception {
        String code = "HSA-F-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/v1/admin/exams")
                        .with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"HSA\",\"scope\":\"FULL\",\"name\":\"Draft\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scope").value("FULL"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.templateCode").value("HSA_FULL"))
                .andExpect(jsonPath("$.parts[0].sections[0].subjectCode").value("TOAN"));
    }

    @Test
    void rejectsFullThptqgScope() throws Exception {
        String code = "THPT-F-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/v1/admin/exams")
                        .with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"THPTQG\",\"scope\":\"FULL\",\"name\":\"Draft\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_SCOPE_NOT_ALLOWED"));
    }

    @Test
    void createsThptqgPartAndScopedVactTemplates() throws Exception {
        String[][] cases = {
                {"THPTQG", "PART", "\"partCode\":\"P3\",\"subjectCode\":\"DIA\"", "THPTQG_GEOGRAPHY_2025", "6", "1.5"},
                {"VACT", "PART", "\"partCode\":\"LOGIC\"", "VACT_PART_LOGIC", "12", "120"},
                {"VACT", "SUBJECT", "\"subjectCode\":\"ANH\"", "VACT_SUBJECT_ANH", "30", "300"},
                {"VACT", "SUBJECT", "\"subjectCode\":\"TOAN\"", "VACT_SUBJECT_TOAN", "30", "300"}
        };
        for (String[] c : cases) {
            String body = "{\"kind\":\"" + c[0] + "\",\"scope\":\"" + c[1] + "\"," + c[2]
                    + ",\"durationMinutes\":30,\"name\":\"Draft\",\"code\":\"T-" + UUID.randomUUID().toString().substring(0, 8) + "\"}";
            var result = mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.templateCode").value(c[3]))
                    .andExpect(jsonPath("$.questionCount").value(Integer.parseInt(c[4])))
                    .andExpect(jsonPath("$.maxScore").value(Double.parseDouble(c[5])))
                    .andReturn().getResponse().getContentAsString();
            if ("THPTQG".equals(c[0])) {
                org.assertj.core.api.Assertions.assertThat(mapper.readTree(result).path("practiceScoring").asText())
                        .isEqualTo("RAW");
            }
        }
    }

    @Test
    void scopedSharedTemplatesRequireDuration() throws Exception {
        for (String selection : new String[]{
                "\"kind\":\"THPTQG\",\"scope\":\"PART\",\"partCode\":\"P3\",\"subjectCode\":\"DIA\"",
                "\"kind\":\"VACT\",\"scope\":\"PART\",\"partCode\":\"LOGIC\"",
                "\"kind\":\"VACT\",\"scope\":\"SUBJECT\",\"subjectCode\":\"ANH\""}) {
            mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{" + selection + ",\"name\":\"Draft\",\"code\":\"T-" + UUID.randomUUID().toString().substring(0, 8) + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("durationMinutes"))
                    .andExpect(jsonPath("$.fieldErrors[0].code").value("EXAM_DURATION_REQUIRED"));
        }
    }

    @Test
    void createsThptqgReviewFromReviewTemplate() throws Exception {
        mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"THPTQG\",\"scope\":\"SUBJECT\",\"subjectCode\":\"DIA\","
                                + "\"buildMode\":\"REVIEW\",\"name\":\"Review\",\"code\":\"T-" + UUID.randomUUID().toString().substring(0, 8) + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.templateCode").value("THPTQG_REVIEW"));
    }

    @Test
    void vactReviewEnablesAllChildrenInScope() throws Exception {
        mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"VACT\",\"scope\":\"SUBJECT\",\"subjectCode\":\"ANH\","
                                + "\"buildMode\":\"REVIEW\",\"name\":\"Review\",\"code\":\"T-" + UUID.randomUUID().toString().substring(0, 8) + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.templateCode").value("VACT_REVIEW"))
                .andExpect(jsonPath("$.parts[0].sections[0].knowledgeScope.everyChildInScope").value(true));
    }

    @Test
    void thptqgEnglishStructurePresentsSequentialBlockRanges() throws Exception {
        String created = mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"THPTQG\",\"scope\":\"SUBJECT\",\"subjectCode\":\"ANH\","
                                + "\"name\":\"English\",\"code\":\"T-" + UUID.randomUUID().toString().substring(0, 8) + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(created).path("id").asText();
        var structure = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure", id)
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        var blocks = structure.path("parts").get(0).path("sections").get(0).path("blocks");
        org.assertj.core.api.Assertions.assertThat(blocks.size()).isEqualTo(6);
        int[] starts = {1, 7, 13, 18, 23, 31};
        int[] ends = {6, 12, 17, 22, 30, 40};
        for (int i = 0; i < starts.length; i++) {
            org.assertj.core.api.Assertions.assertThat(blocks.get(i).path("positionRange").path("from").asInt()).isEqualTo(starts[i]);
            org.assertj.core.api.Assertions.assertThat(blocks.get(i).path("positionRange").path("to").asInt()).isEqualTo(ends[i]);
            org.assertj.core.api.Assertions.assertThat(blocks.get(i).path("requiredPointsPerQuestion").decimalValue())
                    .isEqualByComparingTo("0.25");
        }
        String[][] tags = {{"ANH_DOC_DIEN"}, {"ANH_DOC_DIEN"},
                {"ANH_SAP_XEP_HOI_THOAI", "ANH_SAP_XEP_DOAN"},
                {"ANH_DOC_DIEN_CAU"}, {"ANH_DOC_HIEU"}, {"ANH_DOC_HIEU"}};
        for (int i = 0; i < tags.length; i++) {
            var actual = new java.util.ArrayList<String>();
            blocks.get(i).path("tagCodes").forEach(tag -> actual.add(tag.asText()));
            org.assertj.core.api.Assertions.assertThat(actual).containsExactly(tags[i]);
            if (i != 2) {
                org.assertj.core.api.Assertions.assertThat(blocks.get(i).path("childAnswerType").asText())
                        .isEqualTo("SINGLE_CHOICE");
            }
        }
    }

    @Test
    void thptqgEnglishLastBlockRejectsManyShortReadingSets() throws Exception {
        UUID node = jdbc.queryForObject("SELECT id FROM catalog.catalog_item WHERE code='TEST-TOAN-HEP'", UUID.class);
        jdbc.update("""
                INSERT INTO catalog.catalog_item_ref (item_id, ref_item_id)
                SELECT ?, id FROM catalog.catalog_item WHERE code='ANH'
                  AND group_id='00000000-0000-7000-8100-000000000002'
                ON CONFLICT DO NOTHING
                """, node);
        jdbc.update("""
                INSERT INTO questionbank.question_knowledge (question_id, knowledge_item_id, is_primary)
                SELECT id, ?, false FROM questionbank.question
                WHERE code LIKE 'TESTQ-ANH-READ-S%' AND parent_id IS NULL AND status='READY'
                ON CONFLICT DO NOTHING
                """, node);
        jdbc.update("""
                INSERT INTO questionbank.question_tag (question_id, tag_item_id)
                SELECT q.id, tag.id FROM questionbank.question q
                JOIN catalog.catalog_item tag ON tag.code='ANH_DOC_HIEU'
                WHERE q.code LIKE 'TESTQ-ANH-READ-S%' AND q.parent_id IS NULL AND q.status='READY'
                ON CONFLICT DO NOTHING
                """);
        jdbc.update("""
                UPDATE questionbank.question SET points=0.25
                WHERE parent_id IN (SELECT id FROM questionbank.question
                    WHERE code LIKE 'TESTQ-ANH-READ-S%' AND parent_id IS NULL AND status='READY')
                """);
        assertThatShortReadingPool(node);
        String created = mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"THPTQG\",\"scope\":\"SUBJECT\",\"subjectCode\":\"ANH\","
                                + "\"name\":\"English\",\"code\":\"T-" + UUID.randomUUID().toString().substring(0, 8) + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(mapper.readTree(created).path("id").asText());
        var structure = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure", id)
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        var parts = (com.fasterxml.jackson.databind.node.ArrayNode) structure.path("parts").deepCopy();
        var block = (com.fasterxml.jackson.databind.node.ObjectNode) parts.get(0).path("sections").get(0).path("blocks").get(5);
        String blockId = block.path("id").asText();
        var scope = block.putObject("knowledgeScope");
        scope.putArray("nodeIds").add(node.toString());
        scope.putArray("competencyCodes");
        var request = mapper.createObjectNode().put("expectedRevision", structure.path("revision").asLong());
        request.set("parts", parts);
        mockMvc.perform(put("/api/v1/admin/exams/{id}/structure", id).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isOk());
        var result = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/check", id)
                        .with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(result.path("status").asText()).isEqualTo("INVALID");
        var issues = result.path("groups").findValues("issues").stream()
                .flatMap(array -> java.util.stream.StreamSupport.stream(array.spliterator(), false))
                .filter(issue -> "SET_SHORTAGE".equals(issue.path("code").asText())
                        && blockId.equals(issue.path("blockId").asText())).toList();
        org.assertj.core.api.Assertions.assertThat(issues).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(issues.getFirst().path("required").asInt()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(issues.getFirst().path("available").asInt()).isZero();
    }

    private void assertThatShortReadingPool(UUID node) {
        Integer children = jdbc.queryForObject("""
                SELECT count(*) FROM questionbank.question child
                JOIN questionbank.question parent ON parent.id=child.parent_id
                JOIN questionbank.question_knowledge k ON k.question_id=parent.id
                WHERE k.knowledge_item_id=? AND parent.code LIKE 'TESTQ-ANH-READ-S%'
                """, Integer.class, node);
        org.assertj.core.api.Assertions.assertThat(children).isGreaterThan(10);
    }

    @Test
    void suggestsUnusedCode() throws Exception {
        mockMvc.perform(get("/api/v1/admin/exams/suggested-code")
                        .with(asUser(Permissions.EXAM_MODIFY))
                        .param("kind", "HSA").param("scope", "FULL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern("HSA-F-[0-9]{3,}")));
    }

    @Test
    void vactReferenceScaleIsRetainedByCreateAndDuplicate() throws Exception {
        String code = "VACT-F-" + UUID.randomUUID().toString().substring(0, 8);
        String created = mockMvc.perform(post("/api/v1/admin/exams")
                        .with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"VACT\",\"scope\":\"FULL\",\"name\":\"Draft\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.referenceMax").value(1200))
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(created).path("id").asText();
        mockMvc.perform(get("/api/v1/admin/exams/" + id).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.referenceMax").value(1200));
        mockMvc.perform(post("/api/v1/admin/exams/" + id + "/duplicate")
                        .with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Copy\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.referenceMax").value(1200));
    }

    @Test
    void changingToKindWithoutTemplateAppliesProfileDefaults() throws Exception {
        jdbc.update("UPDATE exam.exam_template SET active=false WHERE code='VACT_FULL'");
        String url = "/api/v1/admin/exams/00000000-0000-7000-8700-000000000001";
        var before = mapper.readTree(mockMvc.perform(get(url).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String revision = before.path("revision").asText();
        mockMvc.perform(patch(url).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"VACT\",\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch(url).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"VACT\",\"durationMinutes\":150,\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timing").value("SHARED"))
                .andExpect(jsonPath("$.durationMinutes").value(150))
                .andExpect(jsonPath("$.referenceMax").value(1200))
                .andExpect(jsonPath("$.practiceScoring").value("LINEAR"))
                .andExpect(jsonPath("$.maxScore").isEmpty())
                .andExpect(jsonPath("$.parts").isEmpty());
    }

    @Test
    void changingToKindWithActiveTemplateAppliesItsProfile() throws Exception {
        String url = "/api/v1/admin/exams/00000000-0000-7000-8700-000000000001";
        var before = mapper.readTree(mockMvc.perform(get(url).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String revision = before.path("revision").asText();
        mockMvc.perform(patch(url).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"VACT\",\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("VACT"))
                .andExpect(jsonPath("$.templateCode").value("VACT_FULL"))
                .andExpect(jsonPath("$.referenceMax").value(1200))
                .andExpect(jsonPath("$.practiceScoring").value("LINEAR"));
        mockMvc.perform(get(url).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateCode").value("VACT_FULL"))
                .andExpect(jsonPath("$.referenceMax").value(1200))
                .andExpect(jsonPath("$.practiceScoring").value("LINEAR"));
    }

    @Test
    void partCodeFromTemplateCreates() throws Exception {
        String code = "HSA-P-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"HSA\",\"scope\":\"PART\",\"partCode\":\"SCIENCE\","
                                + "\"name\":\"Draft\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.templateCode").value("HSA_PART_SCIENCE"));
    }

    @Test
    void partCodeNotInTemplates422() throws Exception {
        String code = "PART-" + UUID.randomUUID().toString().substring(0, 8);
        String request = "{\"kind\":\"HSA\",\"scope\":\"PART\",\"partCode\":\"UNKNOWN\","
                + "\"name\":\"Draft\",\"code\":\"" + code + "\"}";
        mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_PART_OR_SUBJECT_REQUIRED"));
        // 2B §10 adds QDA's Toán part template, so keep the unknown part code.
        mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.replace("\"HSA\"", "\"QDA\"")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_PART_OR_SUBJECT_REQUIRED"));
        jdbc.update("UPDATE exam.exam_template SET active=false WHERE code='HSA_PART_MATH'");
        mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.replace("UNKNOWN", "TOAN")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_PART_OR_SUBJECT_REQUIRED"));
    }

    @Test
    void subjectActiveWithoutTemplateBlankStructure() throws Exception {
        jdbc.update("UPDATE exam.exam_template SET active=false WHERE kind_code='SPT' AND subject_code='TOAN'");
        String code = "SPT-S-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"SPT\",\"scope\":\"SUBJECT\",\"subjectCode\":\"TOAN\","
                                + "\"name\":\"Draft\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.templateCode").isEmpty())
                .andExpect(jsonPath("$.parts").isEmpty());
    }

    @Test
    void subjectUnknown422() throws Exception {
        String code = "SPT-S-" + UUID.randomUUID().toString().substring(0, 8);
        String request = "{\"kind\":\"SPT\",\"scope\":\"SUBJECT\",\"subjectCode\":\"UNKNOWN\","
                + "\"name\":\"Draft\",\"code\":\"" + code + "\"}";
        mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_PART_OR_SUBJECT_REQUIRED"));
        jdbc.update("UPDATE catalog.catalog_item SET status=2 WHERE code='TOAN' AND group_id="
                + "(SELECT id FROM catalog.catalog_group WHERE code='SUBJECT')");
        mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(request.replace("UNKNOWN", "TOAN")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_PART_OR_SUBJECT_REQUIRED"));
    }
}
