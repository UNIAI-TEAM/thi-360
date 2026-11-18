package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionType;

@Transactional
class ExamCreateIT extends AbstractIntegrationTest {
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private QuestionSourcePort questionSource;

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
                "\"kind\":\"VACT\",\"scope\":\"SUBJECT\",\"subjectCode\":\"ANH\"",
                "\"kind\":\"VACT\",\"scope\":\"SUBJECT\",\"subjectCode\":\"ANH\",\"buildMode\":\"REVIEW\""}) {
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
                                + "\"buildMode\":\"REVIEW\",\"durationMinutes\":45,\"name\":\"Review\",\"code\":\"T-" + UUID.randomUUID().toString().substring(0, 8) + "\"}"))
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

    /** The pool the solver filters for block 6: eligible units exist, but only in sets smaller than 10. */
    private void assertThatShortReadingPool(UUID node) {
        UUID english = jdbc.queryForObject("""
                SELECT id FROM catalog.catalog_item
                WHERE group_id = '00000000-0000-7000-8100-000000000002' AND code = 'ANH' AND status = 1
                """, UUID.class);
        var filter = new QuestionSourcePort.Filter(english, List.of(List.of(node)), List.of(),
                QuestionType.QUESTION_SET, null, QuestionType.SINGLE_CHOICE, List.of("ANH_DOC_HIEU"), List.of(),
                new java.math.BigDecimal("0.25"), null, List.of(), List.of());
        var candidates = questionSource.findCandidates(filter, 100_000);
        org.assertj.core.api.Assertions.assertThat(candidates).isNotEmpty();
        org.assertj.core.api.Assertions.assertThat(candidates.stream().mapToInt(c -> c.units().size()).sum())
                .isGreaterThan(10);
        org.assertj.core.api.Assertions.assertThat(candidates).allMatch(c -> c.units().size() < 10);
    }

    @Test
    void reloadingAThptqgPartTemplateKeepsOnlyThatPartAndItsDuration() throws Exception {
        String created = mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"THPTQG\",\"scope\":\"PART\",\"partCode\":\"P3\",\"subjectCode\":\"DIA\","
                                + "\"durationMinutes\":30,\"name\":\"Part\",\"code\":\"T-"
                                + UUID.randomUUID().toString().substring(0, 8) + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(created).path("id").asText();
        long revision = revisionOf(id);
        var reloaded = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/structure/template", id)
                        .with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateCode\":\"THPTQG_GEOGRAPHY_2025\",\"confirmReplace\":true,"
                                + "\"overviewPolicy\":\"USE_TEMPLATE\",\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(reloaded.path("parts").size()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(reloaded.path("parts").get(0).path("order").asInt()).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(reloaded.path("parts").get(0).path("questionCount").asInt()).isEqualTo(6);
        mockMvc.perform(get("/api/v1/admin/exams/{id}", id).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationMinutes").value(30))
                .andExpect(jsonPath("$.questionCount").value(6))
                .andExpect(jsonPath("$.maxScore").value(1.5));
        long next = revisionOf(id);
        for (String other : new String[]{"THPTQG_PHYSICS_2025", "VACT_FULL"}) {
            mockMvc.perform(post("/api/v1/admin/exams/{id}/structure/template", id)
                            .with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"templateCode\":\"" + other + "\",\"confirmReplace\":true,"
                                    + "\"overviewPolicy\":\"USE_TEMPLATE\",\"expectedRevision\":" + next + "}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("EXAM_TEMPLATE_MISMATCH"));
        }
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

    @Test
    void reloadingTheSplitMathTemplateReplacesAWholeSubjectDraft() throws Exception {
        String id = createMathDraft();
        // Put the draft back into its pre-split shape: one whole-subject part of 22 questions in 90 minutes.
        String legacy;
        try (var stream = getClass().getResourceAsStream("/db/migration/exam/V202611180652__exam_templates_ba_numbers.sql")) {
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            String statement = sql.substring(0, sql.indexOf("WHERE code = 'THPTQG_MATH_2025'"));
            statement = statement.substring(statement.lastIndexOf("structure = '") + "structure = '".length());
            legacy = statement.substring(0, statement.lastIndexOf("'::jsonb")).replace("''", "'");
        }
        var legacyParts = mapper.readTree(legacy).path("parts");
        org.assertj.core.api.Assertions.assertThat(legacyParts.size()).isEqualTo(1);
        storeStructure(id, legacy);
        jdbc.update("UPDATE exam.exam SET timing = 'PER_PART', question_count = 22 WHERE id = CAST(? AS uuid)", id);
        var reloaded = reloadMath(id, "KEEP_EDITED");
        var parts = reloaded.path("parts");
        org.assertj.core.api.Assertions.assertThat(parts.size()).isEqualTo(3);
        int[] counts = {12, 4, 6};
        for (int i = 0; i < counts.length; i++) {
            org.assertj.core.api.Assertions.assertThat(parts.get(i).path("questionCount").asInt()).isEqualTo(counts[i]);
            org.assertj.core.api.Assertions.assertThat(parts.get(i).path("sections").get(0).path("questionCount").asInt())
                    .isEqualTo(counts[i]);
            org.assertj.core.api.Assertions.assertThat(parts.get(i).hasNonNull("durationMinutes")).isFalse();
            org.assertj.core.api.Assertions.assertThat(parts.get(i).path("id").asText())
                    .isNotEqualTo(legacyParts.get(0).path("id").asText());
        }
        mockMvc.perform(get("/api/v1/admin/exams/{id}", id).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timing").value("SHARED"))
                .andExpect(jsonPath("$.durationMinutes").value(90))
                .andExpect(jsonPath("$.questionCount").value(22));
        org.assertj.core.api.Assertions.assertThat(templateLoadFlag(id)).isEqualTo("true");
    }

    @Test
    void sameTopologyReloadKeepsGenuineEdits() throws Exception {
        String id = createMathDraft();
        editThirdPartCount(id, 5);
        long revision = revisionOf(id);
        mockMvc.perform(post("/api/v1/admin/exams/{id}/structure/template", id)
                        .with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateCode\":\"THPTQG_MATH_2025\",\"confirmReplace\":true,"
                                + "\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXAM_OVERVIEW_CONFIRM"));
        var reloaded = reloadMath(id, "KEEP_EDITED");
        org.assertj.core.api.Assertions.assertThat(reloaded.path("parts").get(2).path("sections").get(0).path("questionCount").asInt()).isEqualTo(5);
        org.assertj.core.api.Assertions.assertThat(reloaded.path("parts").get(0).path("questionCount").asInt()).isEqualTo(12);
        org.assertj.core.api.Assertions.assertThat(templateLoadFlag(id)).isEqualTo("false");
    }

    @Test
    void oneSharedPartIdKeepsTheIdBasedReload() throws Exception {
        String id = createMathDraft();
        var document = mapper.readTree(jdbc.queryForObject(
                "SELECT parts::text FROM exam.exam_structure WHERE exam_id = CAST(? AS uuid)", String.class, id));
        var parts = (com.fasterxml.jackson.databind.node.ArrayNode) document.path("parts");
        // A different shape (one part instead of three) that still shares the first part id with the template.
        parts.remove(2);
        parts.remove(1);
        storeStructure(id, document.toString());
        reloadMath(id, "KEEP_EDITED");
        org.assertj.core.api.Assertions.assertThat(templateLoadFlag(id)).isEqualTo("false");
    }

    @Test
    void duplicatedDraftWithNewPartIdsAndSameShapeKeepsItsEdits() throws Exception {
        String id = createMathDraft();
        editThirdPartCount(id, 5);
        var original = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure", id)
                .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String duplicateId = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/duplicate", id)
                        .with(asUser(Permissions.EXAM_MODIFY)).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Copy\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asText();
        var copy = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure", duplicateId)
                .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(copy.path("parts").size()).isEqualTo(original.path("parts").size());
        for (int i = 0; i < copy.path("parts").size(); i++) {
            org.assertj.core.api.Assertions.assertThat(copy.path("parts").get(i).path("id").asText())
                    .isNotEqualTo(original.path("parts").get(i).path("id").asText());
        }
        var reloaded = reloadMath(duplicateId, "KEEP_EDITED");
        org.assertj.core.api.Assertions.assertThat(
                reloaded.path("parts").get(2).path("sections").get(0).path("questionCount").asInt()).isEqualTo(5);
        org.assertj.core.api.Assertions.assertThat(templateLoadFlag(duplicateId)).isEqualTo("false");
    }

    @Test
    void templateWithoutStoredIdsAndADifferentPartCountKeepsTheIdBasedReload() throws Exception {
        String id = createMathDraft();
        editPartCount(id, 0, 10);
        // The draft keeps two of the template's three parts: its stored ids overlap nothing, because the
        // template baseline below stores no ids at all, and its part count differs from the baseline's.
        var document = mapper.readTree(jdbc.queryForObject(
                "SELECT parts::text FROM exam.exam_structure WHERE exam_id = CAST(? AS uuid)", String.class, id));
        ((com.fasterxml.jackson.databind.node.ArrayNode) document.path("parts")).remove(2);
        storeStructure(id, document.toString());
        var template = mapper.readTree(jdbc.queryForObject(
                "SELECT structure::text FROM exam.exam_template WHERE code = 'THPTQG_MATH_2025'", String.class));
        removeIds(template);
        org.assertj.core.api.Assertions.assertThat(document.path("parts").size()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(template.path("parts").size()).isEqualTo(3);
        jdbc.update("UPDATE exam.exam_template SET structure = CAST(? AS jsonb) WHERE code = 'THPTQG_MATH_2025'",
                template.toString());
        entityManager.flush();
        entityManager.clear();
        // Reaching the missing-id guard keeps the id/order-based reload, so the edit survives; a wrongly
        // applied topology replacement would restore 12/4/6 and report true.
        var reloaded = reloadMath(id, "KEEP_EDITED");
        org.assertj.core.api.Assertions.assertThat(reloaded.path("parts").size()).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(reloaded.path("parts").get(0).path("sections").get(0)
                .path("questionCount").asInt()).isEqualTo(10);
        org.assertj.core.api.Assertions.assertThat(templateLoadFlag(id)).isEqualTo("false");
    }

    @Test
    void scopedLinearExamsAreCheckedAgainstTheirOwnMaximum() throws Exception {
        String[][] cases = {
                {"\"scope\":\"PART\",\"partCode\":\"LOGIC\"", "120"},
                {"\"scope\":\"SUBJECT\",\"subjectCode\":\"TOAN\"", "300"},
                {"\"scope\":\"FULL\"", "1200"}};
        for (String[] c : cases) {
            var created = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams")
                            .with(asUser(Permissions.EXAM_MODIFY))
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"kind\":\"VACT\"," + c[0] + ",\"durationMinutes\":60,\"name\":\"Draft\",\"code\":\"T-"
                                    + UUID.randomUUID().toString().substring(0, 8) + "\"}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
            org.assertj.core.api.Assertions.assertThat(created.path("maxScore").decimalValue()).as(c[0])
                    .isEqualByComparingTo(c[1]);
            org.assertj.core.api.Assertions.assertThat(created.path("referenceMax").decimalValue()).as(c[0])
                    .isEqualByComparingTo(c[1]);
            org.assertj.core.api.Assertions.assertThat(created.path("practiceScoring").asText()).as(c[0]).isEqualTo("LINEAR");
        }
        // A maximum entered by the administrator is the scoped reference as well.
        var entered = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"VACT\",\"scope\":\"PART\",\"partCode\":\"LOGIC\",\"maxScore\":150,"
                                + "\"durationMinutes\":60,\"name\":\"Draft\",\"code\":\"T-"
                                + UUID.randomUUID().toString().substring(0, 8) + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(entered.path("referenceMax").decimalValue()).isEqualByComparingTo("150");
    }

    @Test
    void changingScopeReloadsTheScopedReferenceMaximum() throws Exception {
        var exam = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"VACT\",\"scope\":\"FULL\",\"name\":\"Draft\",\"code\":\"T-"
                                + UUID.randomUUID().toString().substring(0, 8) + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String url = "/api/v1/admin/exams/" + exam.path("id").asText();
        long revision = exam.path("revision").asLong();
        String[][] steps = {
                {"\"scope\":\"PART\",\"partCode\":\"LOGIC\"", "120"},
                {"\"scope\":\"SUBJECT\",\"subjectCode\":\"TOAN\"", "300"},
                {"\"scope\":\"FULL\"", "1200"}};
        for (String[] step : steps) {
            var changed = mapper.readTree(mockMvc.perform(patch(url).with(asUser(Permissions.EXAM_MODIFY))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{" + step[0] + ",\"partCode\":" + (step[0].contains("LOGIC") ? "\"LOGIC\"" : "null")
                                    + ",\"durationMinutes\":60,\"confirmReplace\":true,\"expectedRevision\":" + revision + "}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            org.assertj.core.api.Assertions.assertThat(changed.path("referenceMax").decimalValue()).as(step[0])
                    .isEqualByComparingTo(step[1]);
            org.assertj.core.api.Assertions.assertThat(changed.path("practiceScoring").asText()).isEqualTo("LINEAR");
            revision = changed.path("revision").asLong();
        }
    }

    @Test
    void scopedVactChecksReportRealPointMismatchesOnly() throws Exception {
        var exam = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"VACT\",\"scope\":\"PART\",\"partCode\":\"LOGIC\",\"durationMinutes\":60,"
                                + "\"name\":\"Draft\",\"code\":\"T-" + UUID.randomUUID().toString().substring(0, 8) + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String id = exam.path("id").asText();
        org.assertj.core.api.Assertions.assertThat(checkCodes(id)).doesNotContain("POINTS_TOTAL_MISMATCH");
        var structure = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure", id)
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        var parts = (com.fasterxml.jackson.databind.node.ArrayNode) structure.path("parts").deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) parts.get(0).path("sections").get(0).path("blocks").get(0))
                .put("requiredPointsPerQuestion", 11);
        var request = mapper.createObjectNode().put("expectedRevision", structure.path("revision").asLong());
        request.set("parts", parts);
        mockMvc.perform(put("/api/v1/admin/exams/{id}/structure", id).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(checkCodes(id)).contains("POINTS_TOTAL_MISMATCH");
    }

    @Test
    void vactReviewHasNoReferenceMaximumBecauseItHasNoMaximum() throws Exception {
        var review = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"VACT\",\"scope\":\"SUBJECT\",\"subjectCode\":\"ANH\","
                                + "\"buildMode\":\"REVIEW\",\"durationMinutes\":45,\"name\":\"Review\",\"code\":\"T-"
                                + UUID.randomUUID().toString().substring(0, 8) + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(review.path("maxScore").isNull()).isTrue();
        org.assertj.core.api.Assertions.assertThat(review.path("referenceMax").isNull()).isTrue();
        org.assertj.core.api.Assertions.assertThat(review.path("practiceScoring").asText()).isEqualTo("LINEAR");
        org.assertj.core.api.Assertions.assertThat(checkCodes(review.path("id").asText()))
                .doesNotContain("POINTS_TOTAL_MISMATCH");
    }

    @Test
    void filledVactReviewWithTenPointQuestionsHasNoPointsTotalMismatch() throws Exception {
        var review = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"VACT\",\"scope\":\"SUBJECT\",\"subjectCode\":\"ANH\","
                                + "\"buildMode\":\"REVIEW\",\"durationMinutes\":45,\"name\":\"Review\",\"code\":\"T-"
                                + UUID.randomUUID().toString().substring(0, 8) + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String id = review.path("id").asText();
        var structure = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure", id)
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        var parts = (com.fasterxml.jackson.databind.node.ArrayNode) structure.path("parts").deepCopy();
        var section = (com.fasterxml.jackson.databind.node.ObjectNode) parts.get(0).path("sections").get(0);
        section.put("questionCount", 10);
        var block = (com.fasterxml.jackson.databind.node.ObjectNode) section.path("blocks").get(0);
        block.put("questionType", "SINGLE_CHOICE").put("questionCount", 10).put("requiredPointsPerQuestion", 10);
        var request = mapper.createObjectNode().put("expectedRevision", structure.path("revision").asLong());
        request.set("parts", parts);
        mockMvc.perform(put("/api/v1/admin/exams/{id}/structure", id).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isOk());
        // 10 questions x 10 points = 100, which is not the 1,200 full scale.
        org.assertj.core.api.Assertions.assertThat(checkCodes(id)).doesNotContain("POINTS_TOTAL_MISMATCH");
    }

    @Test
    void loadingTheReviewTemplateDropsTheStandardScoringTarget() throws Exception {
        var exam = createVactSubject("ANH", null);
        String id = exam.path("id").asText();
        assertThat(exam.path("referenceMax").decimalValue()).isEqualByComparingTo("300");
        loadTemplate(id, "VACT_REVIEW");
        var loaded = examDetail(id);
        assertThat(loaded.path("practiceScoring").asText()).isEqualTo("LINEAR");
        assertThat(loaded.path("referenceMax").isNull()).isTrue();
        assertThat(loaded.path("maxScore").isNull()).isTrue();
        fillReview(id);
        var result = checkResult(id);
        assertThat(result.path("status").asText()).as("issues %s", checkCodes(id)).isEqualTo("VALID");
        assertThat(scoringResetFlags(id)).containsExactly("true");
    }

    @Test
    void loadingAStandardTemplateIntoAReviewGivesItsScopedTarget() throws Exception {
        var review = createVactSubject("ANH", "REVIEW");
        String id = review.path("id").asText();
        assertThat(review.path("referenceMax").isNull()).isTrue();
        loadTemplate(id, "VACT_SUBJECT_ANH");
        var loaded = examDetail(id);
        assertThat(loaded.path("practiceScoring").asText()).isEqualTo("LINEAR");
        assertThat(loaded.path("referenceMax").decimalValue()).isEqualByComparingTo("300");
        assertThat(loaded.path("maxScore").decimalValue()).isEqualByComparingTo("300");
        assertThat(checkCodes(id)).doesNotContain("POINTS_TOTAL_MISMATCH");
        var structure = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure", id)
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        var parts = (com.fasterxml.jackson.databind.node.ArrayNode) structure.path("parts").deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) parts.get(0).path("sections").get(0).path("blocks").get(0))
                .put("requiredPointsPerQuestion", 11);
        var request = mapper.createObjectNode().put("expectedRevision", structure.path("revision").asLong());
        request.set("parts", parts);
        mockMvc.perform(put("/api/v1/admin/exams/{id}/structure", id).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isOk());
        assertThat(checkCodes(id)).contains("POINTS_TOTAL_MISMATCH");
    }

    @Test
    void reloadingTheSameModeKeepsAReferenceOverride() throws Exception {
        var exam = createVactSubject("ANH", null);
        String id = exam.path("id").asText();
        patchExam(id, "{\"referenceMax\":250,\"expectedRevision\":" + exam.path("revision").asLong() + "}");
        assertThat(examDetail(id).path("referenceMax").decimalValue()).isEqualByComparingTo("250");
        loadTemplate(id, "VACT_SUBJECT_ANH");
        var reloaded = examDetail(id);
        assertThat(reloaded.path("referenceMax").decimalValue()).isEqualByComparingTo("250");
        assertThat(reloaded.path("maxScore").decimalValue()).isEqualByComparingTo("300");
        assertThat(scoringResetFlags(id)).containsExactly("false");
    }

    @Test
    void changingModeGivesAnyKindTheFreshCreateScoringOfTheNewMode() throws Exception {
        var exam = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"SPT\",\"scope\":\"SUBJECT\",\"subjectCode\":\"DIA\",\"name\":\"SPT\","
                                + "\"code\":\"SPT-L-" + UUID.randomUUID().toString().substring(0, 8) + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String id = exam.path("id").asText();
        assertThat(exam.path("practiceScoring").asText()).isEqualTo("RAW");
        patchExam(id, "{\"practiceScoring\":\"LINEAR\",\"confirmScoring\":true,\"referenceMax\":10,"
                + "\"expectedRevision\":" + exam.path("revision").asLong() + "}");
        var before = examDetail(id);
        assertThat(before.path("practiceScoring").asText()).isEqualTo("LINEAR");
        assertThat(before.path("referenceMax").decimalValue()).isEqualByComparingTo("10");
        // ADV:FIX-QA:3: a review is LINEAR with no scale for every kind; a standard exam takes the profile scoring.
        loadTemplate(id, "SPT_REVIEW");
        var review = examDetail(id);
        assertThat(review.path("practiceScoring").asText()).isEqualTo("LINEAR");
        assertThat(review.path("referenceMax").isNull()).isTrue();
        assertThat(review.path("maxScore").isNull()).isTrue();
        loadTemplate(id, "SPT_DIA_2025");
        var standard = examDetail(id);
        assertThat(standard.path("practiceScoring").asText()).isEqualTo("RAW");
        assertThat(standard.path("referenceMax").isNull()).isTrue();
        assertThat(standard.path("maxScore").decimalValue()).isEqualByComparingTo(exam.path("maxScore").decimalValue());
        assertThat(scoringResetFlags(id)).containsOnly("true").hasSize(2);
    }

    @Test
    void patchingLinearScoringOnAFilledReviewKeepsItWithoutAReference() throws Exception {
        var review = createVactSubject("ANH", "REVIEW");
        String id = review.path("id").asText();
        fillReview(id);
        patchExam(id, "{\"practiceScoring\":\"LINEAR\",\"expectedRevision\":" + revisionOf(id) + "}");
        var patched = examDetail(id);
        assertThat(patched.path("practiceScoring").asText()).isEqualTo("LINEAR");
        assertThat(patched.path("referenceMax").isNull()).isTrue();
        assertThat(checkCodes(id)).doesNotContain("POINTS_TOTAL_MISMATCH");
    }

    @Test
    void changingScopeWithAMaximumUsesItAsTheReferenceUnlessOneIsSent() throws Exception {
        var exam = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"VACT\",\"scope\":\"FULL\",\"name\":\"Draft\",\"code\":\"T-"
                                + UUID.randomUUID().toString().substring(0, 8) + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String id = exam.path("id").asText();
        var part = patchExam(id, "{\"scope\":\"PART\",\"partCode\":\"LOGIC\",\"maxScore\":150,"
                + "\"durationMinutes\":60,\"confirmReplace\":true,\"expectedRevision\":" + exam.path("revision").asLong() + "}");
        assertThat(part.path("maxScore").decimalValue()).isEqualByComparingTo("150");
        assertThat(part.path("referenceMax").decimalValue()).isEqualByComparingTo("150");
        var subject = patchExam(id, "{\"scope\":\"SUBJECT\",\"subjectCode\":\"TOAN\",\"partCode\":null,"
                + "\"maxScore\":150,\"referenceMax\":200,\"durationMinutes\":60,\"confirmReplace\":true,"
                + "\"expectedRevision\":" + part.path("revision").asLong() + "}");
        assertThat(subject.path("maxScore").decimalValue()).isEqualByComparingTo("150");
        assertThat(subject.path("referenceMax").decimalValue()).isEqualByComparingTo("200");
    }

    private java.util.Set<String> checkCodes(String id) throws Exception {
        var result = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/check", id)
                        .with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        var codes = new java.util.TreeSet<String>();
        for (var group : result.path("groups")) {
            for (var issue : group.path("issues")) codes.add(issue.path("code").asText());
        }
        return codes;
    }

    private static void removeIds(com.fasterxml.jackson.databind.JsonNode node) {
        if (node.isObject()) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) node).remove("id");
        }
        node.forEach(ExamCreateIT::removeIds);
    }

    private String createMathDraft() throws Exception {
        String created = mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"THPTQG\",\"scope\":\"SUBJECT\",\"subjectCode\":\"TOAN\","
                                + "\"name\":\"Math\",\"code\":\"T-" + UUID.randomUUID().toString().substring(0, 8) + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(created).path("id").asText();
    }

    private void storeStructure(String id, String document) {
        jdbc.update("UPDATE exam.exam_structure SET parts = CAST(? AS jsonb) WHERE exam_id = CAST(? AS uuid)",
                document, id);
        entityManager.flush();
        entityManager.clear();
    }

    private long revisionOf(String id) throws Exception {
        return mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure", id)
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("revision").asLong();
    }

    private void editThirdPartCount(String id, int count) throws Exception {
        editPartCount(id, 2, count);
    }

    private void editPartCount(String id, int index, int count) throws Exception {
        var structure = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure", id)
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        var third = structure.path("parts").get(index);
        mockMvc.perform(put("/api/v1/admin/exams/{id}/overview", id).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of(
                                "expectedRevision", structure.path("revision").asLong(),
                                "overview", java.util.List.of(java.util.Map.of(
                                        "partId", third.path("id").asText(),
                                        "sectionId", third.path("sections").get(0).path("id").asText(),
                                        "questionCount", count))))))
                .andExpect(status().isOk());
    }

    private com.fasterxml.jackson.databind.JsonNode reloadMath(String id, String policy) throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/structure/template", id)
                        .with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateCode\":\"THPTQG_MATH_2025\",\"confirmReplace\":true,"
                                + "\"overviewPolicy\":\"" + policy + "\",\"expectedRevision\":" + revisionOf(id) + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String templateLoadFlag(String id) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT payload->'after'->>'topologyReplaced' FROM shared.outbox_event "
                + "WHERE topic='audit' AND payload->>'action'='EXAM.TEMPLATE_LOAD' AND payload->>'targetId'=?",
                String.class, id);
    }

    private com.fasterxml.jackson.databind.JsonNode createVactSubject(String subject, String buildMode) throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"VACT\",\"scope\":\"SUBJECT\",\"subjectCode\":\"" + subject + "\","
                                + (buildMode == null ? "" : "\"buildMode\":\"" + buildMode + "\",")
                                + "\"durationMinutes\":45,\"name\":\"Draft\",\"code\":\"T-"
                                + UUID.randomUUID().toString().substring(0, 8) + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private com.fasterxml.jackson.databind.JsonNode examDetail(String id) throws Exception {
        entityManager.flush();
        entityManager.clear();
        return mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}", id)
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private com.fasterxml.jackson.databind.JsonNode patchExam(String id, String body) throws Exception {
        return mapper.readTree(mockMvc.perform(patch("/api/v1/admin/exams/{id}", id)
                        .with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void loadTemplate(String id, String templateCode) throws Exception {
        mockMvc.perform(post("/api/v1/admin/exams/{id}/structure/template", id)
                        .with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateCode\":\"" + templateCode + "\",\"confirmReplace\":true,"
                                + "\"overviewPolicy\":\"USE_TEMPLATE\",\"expectedRevision\":" + revisionOf(id) + "}"))
                .andExpect(status().isOk());
    }

    /** Ten single-choice questions of ten points in the review frame, with every count filled in. */
    private void fillReview(String id) throws Exception {
        var structure = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure", id)
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        var parts = (com.fasterxml.jackson.databind.node.ArrayNode) structure.path("parts").deepCopy();
        var part = (com.fasterxml.jackson.databind.node.ObjectNode) parts.get(0);
        part.put("questionCount", 10);
        var section = (com.fasterxml.jackson.databind.node.ObjectNode) part.path("sections").get(0);
        section.put("questionCount", 10);
        var block = (com.fasterxml.jackson.databind.node.ObjectNode) section.path("blocks").get(0);
        block.put("questionType", "SINGLE_CHOICE").put("questionCount", 10).put("requiredPointsPerQuestion", 10);
        var request = mapper.createObjectNode().put("expectedRevision", structure.path("revision").asLong());
        request.set("parts", parts);
        mockMvc.perform(put("/api/v1/admin/exams/{id}/structure", id).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isOk());
    }

    private com.fasterxml.jackson.databind.JsonNode checkResult(String id) throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/check", id)
                        .with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private List<String> scoringResetFlags(String id) {
        entityManager.flush();
        return jdbc.queryForList("SELECT payload->'after'->>'scoringReset' FROM shared.outbox_event "
                + "WHERE topic='audit' AND payload->>'action'='EXAM.TEMPLATE_LOAD' AND payload->>'targetId'=?",
                String.class, id);
    }
}
