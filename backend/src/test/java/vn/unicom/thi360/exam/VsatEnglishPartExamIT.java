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
import java.math.BigDecimal;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * BE-21 rows 7-12, 15 and 16 (addendum specification 5.2, H40 rule 4, H41): dedicated PART
 * templates of V-SAT English, the P1 rejection and the PART/displaySegments rule. The shared
 * helpers mirror {@code ExamCreateIT}; every exam is created through the API.
 */
@Transactional
class VsatEnglishPartExamIT extends AbstractIntegrationTest {
    @Autowired private ObjectMapper mapper;

    /** Row 7: PART P2 of V-SAT English is one six-question choice set with the passage. */
    @Test
    void partP2IsOneSixQuestionChoiceSet() throws Exception {
        JsonNode exam = created(create("PART", "P2", "ANH", 30));
        assertThat(exam.path("templateCode").asText()).isEqualTo("VSAT_PART_ANH_P2");
        assertThat(exam.path("maxScore").decimalValue()).isEqualByComparingTo("36");
        JsonNode structure = structure(exam.path("id").asText());
        JsonNode parts = structure.path("parts");
        assertThat(parts).hasSize(1);
        JsonNode part = parts.get(0);
        assertThat(part.path("questionCount").asInt()).isEqualTo(6);
        JsonNode block = part.path("sections").get(0).path("blocks").get(0);
        assertThat(block.path("questionType").asText()).isEqualTo("QUESTION_SET");
        assertThat(block.path("setCount").asInt()).isEqualTo(1);
        assertThat(block.path("setSize").asInt()).isEqualTo(6);
        assertThat(block.path("childAnswerType").asText()).isEqualTo("SINGLE_CHOICE");
        JsonNode total = summary(exam.path("id").asText()).path("parts").get(0);
        assertThat(total.path("allocated").asInt()).isEqualTo(6);
        assertThat(total.path("commandCount").asInt()).isEqualTo(6);
        assertThat(total.path("maxPoints").decimalValue()).isEqualByComparingTo("36");
        // The passage comes with the set: a preview picks one item whose passage is present.
        JsonNode paper = preview(exam.path("id").asText());
        JsonNode items = paper.path("parts").get(0).path("blocks").get(0).path("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).path("kind").asText()).isEqualTo("SET");
        assertThat(items.get(0).path("passage").asText()).isNotBlank();
        assertThat(items.get(0).path("children")).hasSize(6);
    }

    /** Row 8: PART P3 is five matching questions of four items, 20 commands, 30 points. */
    @Test
    void partP3IsFiveMatchingQuestions() throws Exception {
        JsonNode exam = created(create("PART", "P3", "ANH", 30));
        assertThat(exam.path("templateCode").asText()).isEqualTo("VSAT_PART_ANH_P3");
        JsonNode block = structure(exam.path("id").asText()).path("parts").get(0)
                .path("sections").get(0).path("blocks").get(0);
        assertThat(block.path("questionType").asText()).isEqualTo("MATCHING");
        assertThat(block.path("name").asText()).contains("Ghép đôi");
        JsonNode total = summary(exam.path("id").asText()).path("parts").get(0);
        assertThat(total.path("allocated").asInt()).isEqualTo(5);
        assertThat(total.path("commandCount").asInt()).isEqualTo(20);
        assertThat(total.path("maxPoints").decimalValue()).isEqualByComparingTo("30");
    }

    /** Row 9: PART P4 is one Cloze of five positions, 5 questions, 5 commands, 30 points. */
    @Test
    void partP4IsOneFivePositionCloze() throws Exception {
        JsonNode exam = created(create("PART", "P4", "ANH", 30));
        assertThat(exam.path("templateCode").asText()).isEqualTo("VSAT_PART_ANH_P4");
        JsonNode block = structure(exam.path("id").asText()).path("parts").get(0)
                .path("sections").get(0).path("blocks").get(0);
        assertThat(block.path("questionType").asText()).isEqualTo("CLOZE");
        assertThat(block.path("setCount").asInt()).isEqualTo(1);
        assertThat(block.path("setSize").asInt()).isEqualTo(5);
        JsonNode total = summary(exam.path("id").asText()).path("parts").get(0);
        assertThat(total.path("allocated").asInt()).isEqualTo(5);
        assertThat(total.path("commandCount").asInt()).isEqualTo(5);
        assertThat(total.path("maxPoints").decimalValue()).isEqualByComparingTo("30");
    }

    /**
     * Row 10: PART P1 of V-SAT English is rejected on create and on a scope PATCH; the subject
     * keeps dedicated part templates so no 25-question cut exists.
     */
    @Test
    void partP1IsRejectedOnCreateAndOnPatch() throws Exception {
        create("PART", "P1", "ANH", 30)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(result -> assertThat(mapper.readTree(result.getResponse().getContentAsString())
                        .path("code").asText()).isEqualTo("EXAM_PART_NOT_FOUND"));
        JsonNode exam = created(create("SUBJECT", null, "ANH", 60));
        String id = exam.path("id").asText();
        long revision = exam.path("revision").asLong();
        mockMvc.perform(patch("/api/v1/admin/exams/{id}", id).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"PART\",\"partCode\":\"P1\",\"durationMinutes\":30,"
                                + "\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(result -> assertThat(mapper.readTree(result.getResponse().getContentAsString())
                        .path("code").asText()).isEqualTo("EXAM_PART_NOT_FOUND"));
        // The scope was rejected: the exam is still the 25-question subject paper.
        assertThat(structure(id).path("parts").get(0).path("questionCount").asInt()).isEqualTo(25);
        // The same PATCH to an existing part code succeeds through the dedicated template.
        JsonNode patched = mapper.readTree(mockMvc.perform(patch("/api/v1/admin/exams/{id}", id)
                        .with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"PART\",\"partCode\":\"P2\",\"durationMinutes\":30,"
                                + "\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(patched.path("templateCode").asText()).isEqualTo("VSAT_PART_ANH_P2");
        assertThat(patched.path("maxScore").decimalValue()).isEqualByComparingTo("36");
        JsonNode changed = structure(id);
        assertThat(changed.path("parts")).hasSize(1);
        assertThat(changed.path("parts").get(0).path("questionCount").asInt()).isEqualTo(6);
    }

    /** H41: the subject template can no longer be reloaded onto a V-SAT English part exam. */
    @Test
    void subjectTemplateLoadOntoEnglishPartExamIsRejected() throws Exception {
        JsonNode exam = created(create("PART", "P2", "ANH", 30));
        String id = exam.path("id").asText();
        long revision = structure(id).path("revision").asLong();
        mockMvc.perform(post("/api/v1/admin/exams/{id}/structure/template", id)
                        .with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateCode\":\"VSAT_SUBJECT_ANH\",\"confirmReplace\":true,"
                                + "\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(result -> assertThat(mapper.readTree(result.getResponse().getContentAsString())
                        .path("code").asText()).isEqualTo("EXAM_PART_NOT_FOUND"));
        // Reloading its own dedicated template still works.
        mockMvc.perform(post("/api/v1/admin/exams/{id}/structure/template", id)
                        .with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateCode\":\"VSAT_PART_ANH_P2\",\"confirmReplace\":true,"
                                + "\"expectedRevision\":" + revision + "}"))
                .andExpect(status().isOk());
        // A part exam of another V-SAT subject still reloads the subject template (row 12 spirit).
        JsonNode toan = created(create("PART", "P2", "TOAN", 30));
        String toanId = toan.path("id").asText();
        long toanRevision = structure(toanId).path("revision").asLong();
        mockMvc.perform(post("/api/v1/admin/exams/{id}/structure/template", toanId)
                        .with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateCode\":\"VSAT_SUBJECT_TOAN\",\"confirmReplace\":true,"
                                + "\"expectedRevision\":" + toanRevision + "}"))
                .andExpect(status().isOk());
    }

    /**
     * R2-1/R2-3: reloading a dedicated PART template never takes the exam clock from the overview
     * part row — the template declares no exam-level duration, so the entered 45-minute clock
     * survives while the edited part row (a distinct 20 minutes) produces the expected
     * SHARED_PART_DURATION finding. Only the RAW scale derives from the final part row.
     */
    @Test
    void dedicatedReloadKeepsTheExamClockAndDerivesTheRawScale() throws Exception {
        JsonNode exam = created(create("PART", "P3", "ANH", 45));
        String id = exam.path("id").asText();
        patchMaxScore(id, 25);
        reload(id, "USE_TEMPLATE");
        assertThat(structure(id).path("parts").get(0).path("maxPoints").decimalValue())
                .isEqualByComparingTo("30");
        JsonNode detail = detail(id);
        assertThat(detail.path("maxScore").decimalValue()).isEqualByComparingTo("30");
        assertThat(detail.path("durationMinutes").asInt()).isEqualTo(45);
        assertThat(check(id).path("status").asText()).isEqualTo("VALID");

        editOverview(id, 20, 30);
        patchMaxScore(id, 25);
        reload(id, "KEEP_EDITED");
        JsonNode part = structure(id).path("parts").get(0);
        assertThat(part.path("maxPoints").decimalValue()).isEqualByComparingTo("30");
        assertThat(part.path("durationMinutes").asInt()).isEqualTo(20);
        detail = detail(id);
        assertThat(detail.path("maxScore").decimalValue()).isEqualByComparingTo("30");
        assertThat(detail.path("durationMinutes").asInt()).isEqualTo(45);
        JsonNode result = check(id);
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
        assertThat(result.path("groups").findValuesAsText("code")).contains("SHARED_PART_DURATION");
    }

    /**
     * R2-1 + ADV:BE-21:1-A: the create rule still requires a shared duration (400
     * EXAM_DURATION_REQUIRED), and a zero part-row duration can never reach the exam clock — the
     * reloaded exam keeps its entered 45 minutes and the published serving version serves 45, not 0.
     */
    @Test
    void reloadedPartExamNeverLosesItsSharedClock() throws Exception {
        create("PART", "P3", "ANH", null)
                .andExpect(status().isBadRequest())
                .andExpect(result -> {
                    JsonNode error = mapper.readTree(result.getResponse().getContentAsString());
                    assertThat(error.path("fieldErrors").findValuesAsText("code"))
                            .contains("EXAM_DURATION_REQUIRED");
                });
        JsonNode exam = created(create("PART", "P3", "ANH", 45));
        String id = exam.path("id").asText();
        editOverview(id, 0, null);
        reload(id, "KEEP_EDITED");
        JsonNode detail = detail(id);
        assertThat(detail.path("durationMinutes").asInt()).isEqualTo(45);
        JsonNode result = check(id);
        assertThat(result.path("status").asText()).isEqualTo("VALID");

        var publish = mapper.createObjectNode()
                .put("expectedRevision", detail.path("revision").asLong());
        var pathIds = publish.putArray("pathIds");
        result.path("paths").forEach(path -> pathIds.add(path.path("pathId").asText()));
        mockMvc.perform(post("/api/v1/admin/exams/{id}/publish", id)
                        .with(asUser(Permissions.EXAM_PUBLISH))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(publish.toString()))
                .andExpect(status().isOk());
        JsonNode version = mapper.readTree(mockMvc.perform(
                        get("/api/v1/admin/exams/{id}/versions/{version}", id, 1)
                                .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(version.path("config").path("durationMinutes").asInt()).isEqualTo(45);
    }

    /**
     * R2-2: a LINEAR exam (scale equals its reference, confirmed override) keeps maxScore and
     * referenceMax when a dedicated template is reloaded — the edited part points are only reported
     * as the PART_POINTS_MISMATCH finding against the reloaded 30-point blocks.
     */
    @Test
    void dedicatedReloadKeepsTheLinearScale() throws Exception {
        JsonNode exam = created(create("PART", "P3", "ANH", 45));
        String id = exam.path("id").asText();
        mockMvc.perform(patch("/api/v1/admin/exams/{id}", id).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + detail(id).path("revision").asLong()
                                + ",\"practiceScoring\":\"LINEAR\",\"confirmScoring\":true,"
                                + "\"maxScore\":30,\"referenceMax\":30}"))
                .andExpect(status().isOk());
        editOverview(id, null, 24);
        reload(id, "KEEP_EDITED");
        JsonNode part = structure(id).path("parts").get(0);
        assertThat(part.path("maxPoints").decimalValue()).isEqualByComparingTo("24");
        JsonNode detail = detail(id);
        assertThat(detail.path("practiceScoring").asText()).isEqualTo("LINEAR");
        assertThat(detail.path("maxScore").decimalValue()).isEqualByComparingTo("30");
        assertThat(detail.path("referenceMax").decimalValue()).isEqualByComparingTo("30");
        JsonNode result = check(id);
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
        assertThat(result.path("groups").findValuesAsText("code")).contains("PART_POINTS_MISMATCH");
    }

    /**
     * R2-3: a RAW exam takes its scale from the edited part points — KEEP_EDITED with part points
     * 24 leaves the exam maxScore at 24 while the reloaded 30-point blocks produce the expected
     * PART_POINTS_MISMATCH finding.
     */
    @Test
    void dedicatedReloadDerivesTheRawScaleFromEditedPartPoints() throws Exception {
        JsonNode exam = created(create("PART", "P3", "ANH", 45));
        String id = exam.path("id").asText();
        editOverview(id, null, 24);
        reload(id, "KEEP_EDITED");
        JsonNode part = structure(id).path("parts").get(0);
        assertThat(part.path("maxPoints").decimalValue()).isEqualByComparingTo("24");
        JsonNode detail = detail(id);
        assertThat(detail.path("practiceScoring").asText()).isEqualTo("RAW");
        assertThat(detail.path("maxScore").decimalValue()).isEqualByComparingTo("24");
        JsonNode result = check(id);
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
        assertThat(result.path("groups").findValuesAsText("code")).contains("PART_POINTS_MISMATCH");
    }

    /**
     * R3-5 regression: generic PART templates (no subject tag) keep the original reload
     * behavior — the exam clock and declared scale come from the template, not the edited
     * overview part row. HSA_PART_MATH (RAW) reloads to the declared 50 even after the
     * part row was edited to 40.
     */
    @Test
    void nativePartReloadTakesTheDeclaredScale() throws Exception {
        JsonNode exam = created(createNative("HSA", "TOAN", 75));
        assertThat(exam.path("templateCode").asText()).isEqualTo("HSA_PART_MATH");
        String id = exam.path("id").asText();
        editOverview(id, null, 40);
        reload(id, "HSA_PART_MATH", "KEEP_EDITED");
        JsonNode part = structure(id).path("parts").get(0);
        assertThat(part.path("maxPoints").decimalValue()).isEqualByComparingTo("40");
        JsonNode detail = detail(id);
        assertThat(detail.path("practiceScoring").asText()).isEqualTo("RAW");
        assertThat(detail.path("maxScore").decimalValue()).isEqualByComparingTo("50");
    }

    /**
     * R3-5 regression: VACT_PART_LOGIC keeps its original clock behavior — its template
     * declares no exam-level duration, so the entered clock survives a reload even when
     * the edited part row carries 0. (The LINEAR max policy is owned by FIX-RELOAD/UNI-2699
     * and is intentionally not asserted here.)
     */
    @Test
    void nativePartReloadKeepsTheClock() throws Exception {
        JsonNode exam = created(createNative("VACT", "LOGIC", 45));
        assertThat(exam.path("templateCode").asText()).isEqualTo("VACT_PART_LOGIC");
        String id = exam.path("id").asText();
        editOverview(id, 0, null);
        reload(id, "VACT_PART_LOGIC", "KEEP_EDITED");
        assertThat(structure(id).path("parts").get(0).path("durationMinutes").asInt()).isEqualTo(0);
        assertThat(detail(id).path("durationMinutes").asInt()).isEqualTo(45);
    }

    /** H41 scope check: only the V-SAT English P2/P3/P4 PART templates carry a subject code. */
    @Test
    void onlyVsatEnglishPartTemplatesCarrySubjectCodes() throws Exception {
        JsonNode list = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exam-templates")
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        Set<String> tagged = new TreeSet<>();
        for (JsonNode t : list) {
            if ("PART".equals(t.path("scope").asText()) && !t.path("subjectCode").asText("").isEmpty()) {
                tagged.add(t.path("code").asText());
            }
        }
        assertThat(tagged).containsExactlyInAnyOrder("VSAT_PART_ANH_P2", "VSAT_PART_ANH_P3", "VSAT_PART_ANH_P4");
    }


    /** Row 12: a part exam of another V-SAT subject is still cut from the subject template. */
    @Test
    void partOfAnotherVsatSubjectIsStillCut() throws Exception {
        JsonNode exam = created(create("PART", "P2", "TOAN", 30));
        assertThat(exam.path("templateCode").asText()).isEqualTo("VSAT_SUBJECT_TOAN");
        assertThat(exam.path("maxScore").decimalValue()).isEqualByComparingTo("36");
        JsonNode part = structure(exam.path("id").asText()).path("parts").get(0);
        assertThat(part.path("name").asText()).isEqualTo("Phần II · Trắc nghiệm chọn một");
        JsonNode block = part.path("sections").get(0).path("blocks").get(0);
        assertThat(block.path("questionType").asText()).isEqualTo("QUESTION_SET");
        assertThat(block.path("setSize").asInt()).isEqualTo(3);
    }

    /**
     * Row 15 (H40 rule 4): nonempty displaySegments on a PART exam is 422 EXAM_STRUCTURE_INVALID
     * with fieldErrors at the segment; the SUBJECT exam keeps its segments on save.
     */
    @Test
    void displaySegmentsAreRejectedOnPartExamsAndKeptOnSubject() throws Exception {
        JsonNode partExam = created(create("PART", "P2", "ANH", 30));
        String partId = partExam.path("id").asText();
        JsonNode partStored = structure(partId);
        ObjectNode segment = mapper.createObjectNode()
                .put("code", "PX").put("name", "Phần tự chế")
                .put("fromPosition", 1).put("toPosition", 6)
                .put("commandCount", 6).put("maxPoints", 36);
        var parts = partStored.path("parts").deepCopy();
        ((ObjectNode) parts.get(0)).putArray("displaySegments").add(segment);
        save(partId, partStored, parts)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(result -> {
                    JsonNode error = mapper.readTree(result.getResponse().getContentAsString());
                    assertThat(error.path("code").asText()).isEqualTo("EXAM_STRUCTURE_INVALID");
                    assertThat(error.path("fieldErrors").findValuesAsText("field"))
                            .contains("parts[0].displaySegments[0]");
                });

        JsonNode subject = created(create("SUBJECT", null, "ANH", 60));
        String subjectId = subject.path("id").asText();
        JsonNode stored = structure(subjectId);
        assertThat(stored.path("parts").get(0).path("displaySegments")).hasSize(4);
        save(subjectId, stored, stored.path("parts").deepCopy())
                .andExpect(status().isOk());
        assertThat(structure(subjectId).path("parts").get(0).path("displaySegments")).hasSize(4);
    }

    /** Row 16: no PROMPT rows land on a part exam's stored structure. */
    @Test
    void partExamStructureCarriesNoPromptQuotas() throws Exception {
        JsonNode exam = created(create("PART", "P3", "ANH", 30));
        JsonNode structure = structure(exam.path("id").asText());
        assertThat(structure.path("examQuotas").isEmpty()).isTrue();
        for (JsonNode part : structure.path("parts")) {
            for (JsonNode section : part.path("sections")) {
                assertThat(section.path("topicQuotas").isEmpty()).isTrue();
            }
        }
        // And the full exam's own structure keeps its English strands until a part exam is cut.
        JsonNode subject = created(create("SUBJECT", null, "ANH", 60));
        assertThat(structure(subject.path("id").asText()).path("parts").get(0)
                .path("sections").get(0).path("topicQuotas")).hasSize(3);
    }

    private void editOverview(String id, Integer duration, Integer points) throws Exception {
        JsonNode current = structure(id);
        String partId = current.path("parts").get(0).path("id").asText();
        ObjectNode row = mapper.createObjectNode().put("partId", partId);
        if (duration != null) row.put("durationMinutes", duration);
        if (points != null) row.put("maxPoints", points);
        mockMvc.perform(put("/api/v1/admin/exams/{id}/overview", id).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + current.path("revision").asLong()
                                + ",\"overview\":[" + row + "]}"))
                .andExpect(status().isOk());
    }


    private void patchMaxScore(String id, int maxScore) throws Exception {
        mockMvc.perform(patch("/api/v1/admin/exams/{id}", id).with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":" + detail(id).path("revision").asLong()
                                + ",\"maxScore\":" + maxScore + "}"))
                .andExpect(status().isOk());
    }

    private void reload(String id, String policy) throws Exception {
        reload(id, "VSAT_PART_ANH_P3", policy);
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

    private JsonNode check(String id) throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/check", id)
                        .with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private ResultActions create(String scope, String partCode, String subject, Integer duration)
            throws Exception {
        ObjectNode body = mapper.createObjectNode().put("kind", "VSAT").put("scope", scope)
                .put("name", "BE21 " + scope + " " + partCode)
                .put("code", "B21-" + UUID.randomUUID().toString().substring(0, 8))
                .put("subjectCode", subject);
        if (partCode != null) body.put("partCode", partCode);
        if (duration != null) body.put("durationMinutes", duration);
        return mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }

    /** Create a PART exam on a generic (untagged) native template: HSA_PART_MATH, VACT_PART_LOGIC, ... */
    private ResultActions createNative(String kind, String partCode, Integer duration) throws Exception {
        ObjectNode body = mapper.createObjectNode().put("kind", kind).put("scope", "PART")
                .put("name", "BE21 " + kind + " " + partCode)
                .put("code", "B21-" + UUID.randomUUID().toString().substring(0, 8))
                .put("partCode", partCode);
        if (duration != null) body.put("durationMinutes", duration);
        return mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }

    private JsonNode created(ResultActions result) throws Exception {
        return mapper.readTree(result.andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode structure(String id) throws Exception {
        return mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure", id)
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode summary(String id) throws Exception {
        return mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/{id}/structure/summary", id)
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode preview(String id) throws Exception {
        JsonNode result = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/preview", id)
                        .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":{\"kind\":\"EXAM\"},\"seed\":20261119}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(result.path("status").asText()).as(result.path("issues").toString())
                .isEqualTo("GENERATED");
        return result.path("paper");
    }

    private ResultActions save(String id, JsonNode current, JsonNode parts) {
        ObjectNode request = mapper.createObjectNode()
                .put("expectedRevision", current.path("revision").asLong());
        request.set("parts", parts);
        request.set("examQuotas", current.path("examQuotas"));
        try {
            return mockMvc.perform(put("/api/v1/admin/exams/{id}/structure", id)
                    .with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                    .content(request.toString()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
