package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * UNI-2699 (FIX-RELOAD): the legacy subject-cut reload keeps the exam clock of a SHARED
 * exam, takes a positive part-row duration only for PER_PART, and takes a scale only for
 * RAW scoring; a stored SHARED clock that is null or not positive blocks publish.
 */
@Transactional
class ExamTemplateReloadIT extends AbstractIntegrationTest {
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private Integer storedClock(String id) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT duration_minutes FROM exam.exam WHERE id = ?",
                Integer.class, UUID.fromString(id));
    }

    /**
     * Row 1: V-SAT Toán P2 (SHARED subject cut) with a 45-minute exam clock and a part row
     * edited to 0 keeps 45 under both overview policies. The same branch covers the H-SCA
     * Toán P1 and THPTQG Địa lí P3 probes.
     */
    @ParameterizedTest(name = "SHARED subject cut keeps the clock under {0}")
    @ValueSource(strings = {"KEEP_EDITED", "USE_TEMPLATE"})
    void sharedSubjectCutKeepsTheExamClock(String policy) throws Exception {
        JsonNode exam = created(create("VSAT", "PART", "P2", "TOAN", 45));
        assertThat(exam.path("templateCode").asText()).isEqualTo("VSAT_SUBJECT_TOAN");
        String id = exam.path("id").asText();
        editOverview(id, 0, null);
        assertThat(structure(id).path("parts").get(0).path("durationMinutes").asInt()).isEqualTo(0);
        reload(id, "VSAT_SUBJECT_TOAN", policy);
        assertThat(detail(id).path("durationMinutes").asInt()).isEqualTo(45);
    }

    @Test
    void hscaSubjectCutKeepsTheExamClock() throws Exception {
        JsonNode exam = created(create("HSCA", "PART", "P1", "TOAN", 45));
        assertThat(exam.path("templateCode").asText()).isEqualTo("HSCA_SUBJECT_TOAN");
        String id = exam.path("id").asText();
        editOverview(id, 0, null);
        reload(id, "HSCA_SUBJECT_TOAN", "KEEP_EDITED");
        assertThat(detail(id).path("durationMinutes").asInt()).isEqualTo(45);
    }

    @Test
    void thptqgSubjectCutKeepsTheExamClock() throws Exception {
        JsonNode exam = created(create("THPTQG", "PART", "P3", "DIA", 45));
        assertThat(exam.path("templateCode").asText()).isEqualTo("THPTQG_GEOGRAPHY_2025");
        String id = exam.path("id").asText();
        editOverview(id, 0, null);
        reload(id, "THPTQG_GEOGRAPHY_2025", "KEEP_EDITED");
        assertThat(detail(id).path("durationMinutes").asInt()).isEqualTo(45);
    }

    /**
     * Row 2: a PER_PART subject cut still takes a positive part-row duration (THPTQG
     * English, 50), while a part row of 0 (edited) or null (SPT seed) keeps the stored
     * clock.
     */
    @Test
    void perPartSubjectCutTakesAPositivePartDuration() throws Exception {
        JsonNode exam = created(create("THPTQG", "PART", "P1", "ANH", 30));
        assertThat(exam.path("templateCode").asText()).isEqualTo("THPTQG_ENGLISH_2025");
        String id = exam.path("id").asText();
        // The detail response hides durationMinutes on PER_PART exams; read the stored clock back.
        assertThat(storedClock(id)).isEqualTo(30);
        editOverview(id, 60, null);
        reload(id, "THPTQG_ENGLISH_2025", "KEEP_EDITED");
        assertThat(storedClock(id)).isEqualTo(60);
        editOverview(id, 0, null);
        reload(id, "THPTQG_ENGLISH_2025", "KEEP_EDITED");
        assertThat(storedClock(id)).isEqualTo(60);
    }

    @Test
    void perPartSubjectCutKeepsTheClockOnANullPartRow() throws Exception {
        JsonNode exam = created(create("SPT", "PART", "P1", "TOAN", 60));
        assertThat(exam.path("templateCode").asText()).isEqualTo("SPT_MATH_2025");
        String id = exam.path("id").asText();
        JsonNode partDuration = structure(id).path("parts").get(0).path("durationMinutes");
        assertThat(partDuration.isNull() || partDuration.isMissingNode()).isTrue();
        reload(id, "SPT_MATH_2025", "USE_TEMPLATE");
        assertThat(storedClock(id)).isEqualTo(60);
    }

    /**
     * Row 3: a SHARED exam whose stored clock is not positive reports the blocking
     * SHARED_PART_DURATION finding and publish is refused without writing a version. The
     * bad clock is written directly — create and reload cannot produce one.
     */
    @Test
    void badSharedClockBlocksPublish() throws Exception {
        JsonNode exam = created(create("VSAT", "PART", "P2", "TOAN", 45));
        String id = exam.path("id").asText();
        entityManager.flush();
        jdbc.update("UPDATE exam.exam SET duration_minutes = 0 WHERE id = ?", UUID.fromString(id));
        entityManager.clear();
        JsonNode result = check(id);
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
        assertThat(result.path("groups").findValuesAsText("code")).contains("SHARED_PART_DURATION");

        ObjectNode publish = mapper.createObjectNode()
                .put("expectedRevision", detail(id).path("revision").asLong());
        result.path("paths").forEach(path -> publish.putArray("pathIds").add(path.path("pathId").asText()));
        mockMvc.perform(post("/api/v1/admin/exams/{id}/publish", id)
                        .with(asUser(Permissions.EXAM_PUBLISH))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(publish.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(r -> assertThat(mapper.readTree(r.getResponse().getContentAsString())
                        .path("code").asText()).isEqualTo("PATH_NOT_VALID"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM exam.exam_version WHERE exam_id = ?",
                Integer.class, UUID.fromString(id))).isZero();
    }

    /**
     * Row 4: a RAW subject-cut part exam takes its scale from the final part points —
     * 24 under KEEP_EDITED, the restored 36 under USE_TEMPLATE.
     */
    @ParameterizedTest(name = "RAW subject cut follows the part points under {0}")
    @ValueSource(strings = {"KEEP_EDITED", "USE_TEMPLATE"})
    void rawSubjectCutFollowsThePartPoints(String policy) throws Exception {
        JsonNode exam = created(create("VSAT", "PART", "P2", "TOAN", 45));
        String id = exam.path("id").asText();
        editOverview(id, null, 24);
        reload(id, "VSAT_SUBJECT_TOAN", policy);
        JsonNode detail = detail(id);
        assertThat(detail.path("practiceScoring").asText()).isEqualTo("RAW");
        assertThat(detail.path("maxScore").decimalValue())
                .isEqualByComparingTo("KEEP_EDITED".equals(policy) ? "24" : "36");
    }

    /**
     * Row 5: a LINEAR override on a subject-cut part exam survives both policies even
     * though the part row points were edited.
     */
    @ParameterizedTest(name = "LINEAR subject cut keeps the scale under {0}")
    @ValueSource(strings = {"KEEP_EDITED", "USE_TEMPLATE"})
    void linearSubjectCutKeepsTheScale(String policy) throws Exception {
        JsonNode exam = created(create("VSAT", "PART", "P2", "TOAN", 45));
        String id = exam.path("id").asText();
        patchLinear(id, 36);
        editOverview(id, null, 24);
        reload(id, "VSAT_SUBJECT_TOAN", policy);
        JsonNode detail = detail(id);
        assertThat(detail.path("practiceScoring").asText()).isEqualTo("LINEAR");
        assertThat(detail.path("maxScore").decimalValue()).isEqualByComparingTo("36");
        assertThat(detail.path("referenceMax").decimalValue()).isEqualByComparingTo("36");
    }

    /**
     * Row 6: a LINEAR SUBJECT override stays under both policies while a RAW SUBJECT
     * exam still takes the template maximum.
     */
    @Test
    void linearSubjectReloadKeepsTheOverride() throws Exception {
        JsonNode exam = created(create("VSAT", "SUBJECT", null, "ANH", 60));
        assertThat(exam.path("templateCode").asText()).isEqualTo("VSAT_SUBJECT_ANH");
        String id = exam.path("id").asText();
        patchLinear(id, 160);
        for (String policy : new String[] {"KEEP_EDITED", "USE_TEMPLATE"}) {
            reload(id, "VSAT_SUBJECT_ANH", policy);
            JsonNode detail = detail(id);
            assertThat(detail.path("practiceScoring").asText()).isEqualTo("LINEAR");
            assertThat(detail.path("maxScore").decimalValue()).isEqualByComparingTo("160");
            assertThat(detail.path("referenceMax").decimalValue()).isEqualByComparingTo("160");
        }
        JsonNode raw = created(create("VSAT", "SUBJECT", null, "ANH", 60));
        reload(raw.path("id").asText(), "VSAT_SUBJECT_ANH", "USE_TEMPLATE");
        JsonNode rawDetail = detail(raw.path("id").asText());
        assertThat(rawDetail.path("practiceScoring").asText()).isEqualTo("RAW");
        assertThat(rawDetail.path("maxScore").decimalValue()).isEqualByComparingTo("150");
    }

    /**
     * Row 7: a LINEAR FULL override (V-ACT 1300/1300) stays under both policies; the
     * default exam keeps the declared 1200/1200.
     */
    @Test
    void linearFullReloadKeepsTheOverride() throws Exception {
        JsonNode exam = created(create("VACT", "FULL", null, null, 150));
        assertThat(exam.path("templateCode").asText()).isEqualTo("VACT_FULL");
        String id = exam.path("id").asText();
        patchLinear(id, 1300);
        for (String policy : new String[] {"KEEP_EDITED", "USE_TEMPLATE"}) {
            reload(id, "VACT_FULL", policy);
            JsonNode detail = detail(id);
            assertThat(detail.path("practiceScoring").asText()).isEqualTo("LINEAR");
            assertThat(detail.path("maxScore").decimalValue()).isEqualByComparingTo("1300");
            assertThat(detail.path("referenceMax").decimalValue()).isEqualByComparingTo("1300");
        }
        JsonNode plain = created(create("VACT", "FULL", null, null, 150));
        reload(plain.path("id").asText(), "VACT_FULL", "USE_TEMPLATE");
        JsonNode plainDetail = detail(plain.path("id").asText());
        assertThat(plainDetail.path("maxScore").decimalValue()).isEqualByComparingTo("1200");
        assertThat(plainDetail.path("referenceMax").decimalValue()).isEqualByComparingTo("1200");
    }

    /**
     * Row 8: a native PART reload takes the declared scale for RAW (HSA_PART_MATH) but
     * keeps a stored LINEAR override (VACT_PART_LOGIC 130/130 against the declared 120)
     * and never the clock from the part row.
     */
    @Test
    void nativePartReloadKeepsTheLinearScale() throws Exception {
        JsonNode exam = created(createNative("VACT", "LOGIC", 45));
        assertThat(exam.path("templateCode").asText()).isEqualTo("VACT_PART_LOGIC");
        String id = exam.path("id").asText();
        patchLinear(id, 130);
        editOverview(id, 0, null);
        reload(id, "VACT_PART_LOGIC", "KEEP_EDITED");
        JsonNode detail = detail(id);
        assertThat(detail.path("practiceScoring").asText()).isEqualTo("LINEAR");
        assertThat(detail.path("maxScore").decimalValue()).isEqualByComparingTo("130");
        assertThat(detail.path("referenceMax").decimalValue()).isEqualByComparingTo("130");
        assertThat(detail.path("durationMinutes").asInt()).isEqualTo(45);
    }

    @Test
    void nativeRawPartReloadTakesTheDeclaredScale() throws Exception {
        JsonNode exam = created(createNative("HSA", "TOAN", 75));
        assertThat(exam.path("templateCode").asText()).isEqualTo("HSA_PART_MATH");
        String id = exam.path("id").asText();
        editOverview(id, null, 40);
        reload(id, "HSA_PART_MATH", "KEEP_EDITED");
        JsonNode detail = detail(id);
        assertThat(detail.path("practiceScoring").asText()).isEqualTo("RAW");
        assertThat(detail.path("maxScore").decimalValue()).isEqualByComparingTo("50");
    }

    /** Row 11: a SHARED exam created without a duration is still refused. */
    @Test
    void sharedExamWithoutADurationIsRefusedOnCreate() throws Exception {
        create("VSAT", "PART", "P2", "TOAN", null)
                .andExpect(status().isBadRequest())
                .andExpect(r -> assertThat(mapper.readTree(r.getResponse().getContentAsString())
                        .path("fieldErrors").findValuesAsText("code"))
                        .contains("EXAM_DURATION_REQUIRED"));
    }

    private void editOverview(String id, Integer duration, Integer points) throws Exception {
        JsonNode current = structure(id);
        ObjectNode row = mapper.createObjectNode()
                .put("partId", current.path("parts").get(0).path("id").asText());
        if (duration != null) row.put("durationMinutes", duration);
        if (points != null) row.put("maxPoints", points);
        mockMvc.perform(put("/api/v1/admin/exams/{id}/overview", id).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + current.path("revision").asLong()
                                + ",\"overview\":[" + row + "]}"))
                .andExpect(status().isOk());
    }

    /** Confirms a LINEAR override with the display scale equal to its reference (spec D11). */
    private void patchLinear(String id, int scale) throws Exception {
        mockMvc.perform(patch("/api/v1/admin/exams/{id}", id).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + detail(id).path("revision").asLong()
                                + ",\"practiceScoring\":\"LINEAR\",\"confirmScoring\":true,"
                                + "\"maxScore\":" + scale + ",\"referenceMax\":" + scale + "}"))
                .andExpect(status().isOk());
    }

    private void reload(String id, String templateCode, String policy) throws Exception {
        mockMvc.perform(post("/api/v1/admin/exams/{id}/structure/template", id)
                        .with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateCode\":\"" + templateCode + "\",\"confirmReplace\":true,"
                                + "\"overviewPolicy\":\"" + policy + "\",\"expectedRevision\":"
                                + structure(id).path("revision").asLong() + "}"))
                .andExpect(status().isOk());
    }

    private JsonNode detail(String id) throws Exception {
        return mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}", id)
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode structure(String id) throws Exception {
        return mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure", id)
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode check(String id) throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/check", id)
                        .with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private ResultActions create(String kind, String scope, String partCode, String subject,
                                 Integer duration) throws Exception {
        ObjectNode body = mapper.createObjectNode().put("kind", kind).put("scope", scope)
                .put("name", "R2699 " + kind + " " + scope + " " + partCode)
                .put("code", "R-" + UUID.randomUUID().toString().substring(0, 8));
        if (partCode != null) body.put("partCode", partCode);
        if (subject != null) body.put("subjectCode", subject);
        if (duration != null) body.put("durationMinutes", duration);
        return mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }

    /** Create a PART exam on a generic (untagged) native template: HSA_PART_MATH, VACT_PART_LOGIC. */
    private ResultActions createNative(String kind, String partCode, Integer duration) throws Exception {
        return create(kind, "PART", partCode, null, duration);
    }

    private JsonNode created(ResultActions result) throws Exception {
        return mapper.readTree(result.andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }
}
