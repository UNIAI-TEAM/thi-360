package vn.unicom.thi360.exam;

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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * FIX-QA F2 (ADV:FIX-QA:3, BE spec D11): a review exam is LINEAR with no scale, or with the scale typed in
 * step 1 as both maxScore and referenceMax. The screen writes no requiredPointsPerQuestion (it stays a pool
 * filter), so a fresh review exam passes the check and draws exactly the requested number of questions from a
 * bank of mixed points, for every examination kind.
 */
@Transactional
class ExamReviewAllKindsIT extends AbstractIntegrationTest {
    private static final List<String> KINDS = List.of("HSA", "QDA", "BCA", "SP2E", "SPT", "THPTQG", "VACT");
    private static final UUID TOAN = UUID.fromString("00000000-0000-7000-8200-000000000201");
    private static final String[] POINTS = {"1", "2.5", "10", "15", "0.25"};
    private static final int POOL = 30;

    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void everyKindWithoutAScaleHasNoMaximumAndPassesWithTheCountDrawn() throws Exception {
        seedMixedPointsBank();
        List<String> failures = new ArrayList<>();
        for (String kind : KINDS) {
            for (int count : new int[] {10, 7}) {
                verify(failures, kind, count, null);
            }
        }
        assertThat(failures).isEmpty();
    }

    @Test
    void everyKindWithAScaleKeepsItAsBothMaximumsAndPasses() throws Exception {
        seedMixedPointsBank();
        List<String> failures = new ArrayList<>();
        for (String kind : KINDS) {
            verify(failures, kind, 10, new BigDecimal("40"));
            verify(failures, kind, 7, new BigDecimal("30"));
            verify(failures, kind, 3, new BigDecimal("10"));
        }
        assertThat(failures).isEmpty();
    }

    @Test
    void aScaledReviewWithAPointsFilterIsNotHeldToAFixedRawTotal() throws Exception {
        Set<UUID> matching = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            matching.add(seedQuestion("2.5"));
        }
        for (int i = 0; i < 6; i++) {
            seedQuestion("10");
        }
        JsonNode created = createReview("HSA", 3, new BigDecimal("40"));
        String id = created.path("id").asText();
        saveBlock(id, new BigDecimal("2.5"));
        JsonNode check = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/check", id)
                        .with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        // Raw maximum 3 x 2.5 = 7.5 against the scale 40: a review is never held to a fixed raw total.
        assertThat(check.path("status").asText()).as("check %s", check.path("groups").findValuesAsText("code"))
                .isEqualTo("VALID");
        assertThat(check.path("groups").findValuesAsText("code")).doesNotContain("POINTS_TOTAL_MISMATCH");
        JsonNode trial = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/preview", id)
                        .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":{\"kind\":\"EXAM\"},\"seed\":3}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(trial.path("status").asText()).isEqualTo("GENERATED");
        Set<UUID> drawn = new HashSet<>();
        trial.path("paper").findValuesAsText("questionId").forEach(value -> drawn.add(UUID.fromString(value)));
        // The block rate stays a pool filter: the three 2.5-point questions, never the 10-point distractors.
        assertThat(drawn).isEqualTo(matching);
    }

    @Test
    void vactReviewKeepsTheScreensDuplicatedDurationOnTheExamOnly() throws Exception {
        JsonNode created = createReview("VACT", 10, null);
        assertThat(created.path("durationMinutes").asInt()).isEqualTo(45);
        JsonNode structure = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure",
                        created.path("id").asText()).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode partDuration = structure.path("parts").get(0).path("durationMinutes");
        assertThat(partDuration.isMissingNode() || partDuration.isNull()).isTrue();
    }

    @Test
    void sharedNormalizationDoesNotMaskInvalidReviewOverviewRows() throws Exception {
        String partId = reviewTemplateId("VACT_REVIEW", "parts", "0");
        String sectionId = reviewTemplateId("VACT_REVIEW", "parts", "0", "sections", "0");
        // A section row never carries a duration, even when the exam has a valid top-level duration.
        postReviewRows("VACT", 45, partRow(partId, 10, 45), sectionRow(partId, sectionId, 10, 45))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_STRUCTURE_INVALID"));
        // A negative part duration is not the screen's duplicate: it is rejected, not discarded.
        postReviewRows("VACT", 45, partRow(partId, 10, -1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXAM_STRUCTURE_INVALID"));
    }

    @Test
    void standardSharedCreateKeepsItsDurationRules() throws Exception {
        String partId = reviewTemplateId("VACT_SUBJECT_ANH", "parts", "0");
        // The part duration is never promoted to the exam clock for a standard exam: no exam duration, no create.
        postRows("{\"kind\":\"VACT\",\"scope\":\"SUBJECT\",\"subjectCode\":\"ANH\"", null, partRow(partId, 30, 20))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].code").value("EXAM_DURATION_REQUIRED"));
        // With a duration, the part keeps what the request gave it, as before.
        JsonNode created = mapper.readTree(postRows("{\"kind\":\"VACT\",\"scope\":\"SUBJECT\",\"subjectCode\":\"ANH\"",
                        45, partRow(partId, 30, 20)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertThat(created.path("durationMinutes").asInt()).isEqualTo(45);
        JsonNode structure = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure",
                        created.path("id").asText()).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(structure.path("parts").get(0).path("durationMinutes").asInt()).isEqualTo(20);
    }

    private ObjectNode partRow(String partId, int count, int duration) {
        return mapper.createObjectNode().put("partId", partId).put("questionCount", count)
                .put("durationMinutes", duration);
    }

    private ObjectNode sectionRow(String partId, String sectionId, int count, int duration) {
        return mapper.createObjectNode().put("partId", partId).put("sectionId", sectionId)
                .put("questionCount", count).put("durationMinutes", duration);
    }

    private String reviewTemplateId(String templateCode, String... path) {
        StringBuilder sql = new StringBuilder("SELECT structure");
        for (int i = 0; i < path.length; i += 2) {
            sql.append("->'").append(path[i]).append("'->").append(path[i + 1]);
        }
        sql.append("->>'id' FROM exam.exam_template WHERE code = ?");
        return jdbc.queryForObject(sql.toString(), String.class, templateCode);
    }

    private org.springframework.test.web.servlet.ResultActions postReviewRows(String kind, Integer duration,
            ObjectNode... rows) throws Exception {
        return postRows("{\"kind\":\"" + kind + "\",\"scope\":\"SUBJECT\",\"subjectCode\":\"TOAN\","
                + "\"buildMode\":\"REVIEW\"", duration, rows);
    }

    private org.springframework.test.web.servlet.ResultActions postRows(String head, Integer duration,
            ObjectNode... rows) throws Exception {
        ObjectNode body = (ObjectNode) mapper.readTree(head + "}");
        body.put("name", "Rows").put("code", "R-" + UUID.randomUUID().toString().substring(0, 8));
        if (duration != null) {
            body.put("durationMinutes", duration);
        }
        body.putArray("overview").addAll(List.of(rows));
        return mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }

    // ------------------------------------------------------------------ the screen's two requests

    /** Collects one line per failing kind and count, so one run shows every kind. */
    private void verify(List<String> failures, String kind, int count, BigDecimal typedScale) throws Exception {
        try {
            verify(kind, count, typedScale);
        } catch (AssertionError | RuntimeException error) {
            failures.add(String.valueOf(error.getMessage()).replace('\n', ' '));
        }
    }

    private void verify(String kind, int count, BigDecimal typedScale) throws Exception {
        String label = kind + " N=" + count + " scale=" + typedScale;
        JsonNode created = createReview(kind, count, typedScale);
        String id = created.path("id").asText();
        assertThat(created.path("practiceScoring").asText()).as(label).isEqualTo("LINEAR");
        if (typedScale == null) {
            assertThat(created.path("maxScore").isNull()).as("%s maxScore %s", label, created.path("maxScore")).isTrue();
            assertThat(created.path("referenceMax").isNull()).as("%s referenceMax", label).isTrue();
        } else {
            assertThat(created.path("maxScore").decimalValue()).as(label).isEqualByComparingTo(typedScale);
            assertThat(created.path("referenceMax").decimalValue()).as(label).isEqualByComparingTo(typedScale);
        }
        saveBlock(id);
        JsonNode check = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/check", id)
                        .with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(check.path("status").asText()).as("%s check %s", label, check.path("groups").findValuesAsText("code"))
                .isEqualTo("VALID");
        assertThat(check.path("groups").findValuesAsText("code")).as(label)
                .doesNotContain("CELL_SHORTAGE", "POINTS_TOTAL_MISMATCH", "SHARED_PART_DURATION");
        JsonNode trial = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/preview", id)
                        .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":{\"kind\":\"EXAM\"},\"seed\":3}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(trial.path("status").asText()).as("%s trial %s", label, trial).isEqualTo("GENERATED");
        Set<String> drawn = new HashSet<>(trial.path("paper").findValuesAsText("questionId"));
        assertThat(drawn).as("%s drawn", label).hasSize(count);
    }

    /** Step 1: kind, subject, duration, count (overview row of the review template part) and, if typed, the scale. */
    private JsonNode createReview(String kind, int count, BigDecimal typedScale) throws Exception {
        String partId = jdbc.queryForObject(
                "SELECT structure->'parts'->0->>'id' FROM exam.exam_template WHERE code = ?", String.class,
                kind + "_REVIEW");
        ObjectNode body = mapper.createObjectNode().put("kind", kind).put("scope", "SUBJECT")
                .put("subjectCode", "TOAN").put("buildMode", "REVIEW").put("name", "Review " + kind)
                .put("code", "R-" + UUID.randomUUID().toString().substring(0, 8)).put("durationMinutes", 45);
        if (typedScale != null) {
            body.put("maxScore", typedScale);
        }
        body.putArray("overview").addObject().put("partId", partId).put("questionCount", count)
                .put("durationMinutes", 45);
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    /** Step 2: only the answer type on the template's single block; no requiredPointsPerQuestion. */
    private void saveBlock(String id) throws Exception {
        saveBlock(id, null);
    }

    /** Step 2 with an explicit points filter on the block, when {@code rate} is given. */
    private void saveBlock(String id, BigDecimal rate) throws Exception {
        JsonNode structure = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure", id)
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        ArrayNode parts = (ArrayNode) structure.path("parts").deepCopy();
        ObjectNode block = (ObjectNode) parts.get(0).path("sections").get(0).path("blocks").get(0);
        block.put("questionType", "SINGLE_CHOICE");
        if (rate != null) {
            block.put("requiredPointsPerQuestion", rate);
        }
        assertThat(block.has("requiredPointsPerQuestion") && !block.get("requiredPointsPerQuestion").isNull())
                .as("the screen writes no points per question unless the filter is asked for")
                .isEqualTo(rate != null);
        ObjectNode request = mapper.createObjectNode().put("expectedRevision", structure.path("revision").asLong());
        request.set("parts", parts);
        mockMvc.perform(put("/api/v1/admin/exams/{id}/structure", id).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isOk());
    }

    /** Questions worth 1, 2.5, 10, 15 and 0.25 points in turn: no single points value serves a whole review. */
    private void seedMixedPointsBank() {
        for (int i = 0; i < POOL; i++) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO questionbank.question(id,code,type,status,subject_item_id,current_version_no,points) "
                    + "VALUES (?,?,'SINGLE_CHOICE','READY',?,1,?)", id, "RV-" + id, TOAN,
                    new BigDecimal(POINTS[i % POINTS.length]));
            jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) VALUES (?,1,?::jsonb)", id,
                    "{\"content\":\"q\",\"points\":1,\"body\":{\"type\":\"SINGLE_CHOICE\",\"options\":["
                            + "{\"id\":\"a\",\"content\":\"A\",\"correct\":true},{\"id\":\"b\",\"content\":\"B\",\"correct\":false}]}}");
        }
    }

    /** One question worth {@code points}, with the same value in its snapshot. */
    private UUID seedQuestion(String points) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO questionbank.question(id,code,type,status,subject_item_id,current_version_no,points) "
                + "VALUES (?,?,'SINGLE_CHOICE','READY',?,1,?)", id, "RV-" + id, TOAN, new BigDecimal(points));
        jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) VALUES (?,1,?::jsonb)", id,
                "{\"content\":\"q\",\"points\":" + points + ",\"body\":{\"type\":\"SINGLE_CHOICE\",\"options\":["
                        + "{\"id\":\"a\",\"content\":\"A\",\"correct\":true},{\"id\":\"b\",\"content\":\"B\",\"correct\":false}]}}");
        return id;
    }
}
