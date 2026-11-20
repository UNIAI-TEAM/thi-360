package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * R6-1 (ADV:CR-4:21): a stored node id equal to the "{option}:blocks" holder name of an option makes the draft
 * uncertifiable, so check evidence that was saved before the exam-level rule (here inserted directly, otherwise
 * current: same structure hash, config, sources and, for HSA, the rules stamp) can no longer authorize a publish of that
 * draft. A scoped check (PART/SECTION) reports the collision but never replaces the whole-exam result, so it does not
 * lift the evidence either. A draft without the collision, the frozen versions and the serving state are untouched.
 */
class ExamPublishStoredIdCollisionIT extends AbstractExamPublishIT {
    private static final String SECTION_ID_PLACEHOLDER = "@SECTION@";
    private static final String PARTS = """
            [{"id":"p3","participation":{"mode":"CHOICE","choose":1},"options":[
              {"id":"science","kind":"SUBJECT","code":"LY","sections":[
                {"id":"@SECTION@","subjectCode":"LY","blocks":[]}]},
              {"id":"english","kind":"SUBJECT","code":"ANH","sections":[]}]}]
            """;
    private static final String COLLIDING = "science:blocks";
    private static final String CLEAN = "s-ly";

    private static String parts(String sectionId) {
        return PARTS.replace(SECTION_ID_PLACEHOLDER, sectionId);
    }

    /** HSA (the R-12 population, stamp present) and BCA (outside it): the guard does not depend on the stamp. */
    private static final String[] KINDS = {"HSA", "BCA"};

    @Test
    void directPublishOfAnOldVerdictForACollidingDraftIsRefused() throws Exception {
        for (String kind : KINDS) {
            UUID colliding = newExam(kind, parts(COLLIDING));
            saveCheck(colliding, paths("science=VALID", "english=VALID"));
            assertThat(evidenceStatus(colliding)).as(kind + ": the evidence is VALID and current").isEqualTo("VALID");
            publishRefused(colliding);
            assertThat(versions(colliding)).as(kind).isZero();

            // Control: the same exam and evidence without the collision publishes.
            UUID clean = newExam(kind, parts(CLEAN));
            saveCheck(clean, paths("science=VALID", "english=VALID"));
            publish(clean, "science", "english").andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        }
    }

    @Test
    void publishAfterAScopedCollisionCheckIsRefusedAndTheOldEvidenceStays() throws Exception {
        for (String kind : KINDS) {
            for (String scope : new String[] {"{\"scope\":\"PART\",\"partId\":\"p3\"}",
                    "{\"scope\":\"SECTION\",\"sectionId\":\"" + COLLIDING + "\"}"}) {
                UUID exam = newExam(kind, parts(COLLIDING));
                saveCheck(exam, paths("science=VALID", "english=VALID"));
                UUID run = jdbc.queryForObject("SELECT id FROM exam.exam_check_run WHERE exam_id = ?", UUID.class, exam);

                JsonNode scoped = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/" + exam + "/check")
                                .with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON).content(scope))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
                assertThat(scoped.findValuesAsText("code")).as(kind + " " + scope + ": the new blocker is shown")
                        .contains("STORED_ID_COLLISION");
                // The scoped run leaves the whole-exam proof in place: that is what publish must not trust.
                assertThat(jdbc.queryForObject("SELECT id FROM exam.exam_check_run WHERE exam_id = ?", UUID.class, exam))
                        .isEqualTo(run);
                assertThat(evidenceStatus(exam)).isEqualTo("VALID");

                publishRefused(exam);
                assertThat(versions(exam)).as(kind + " " + scope).isZero();
            }
        }
    }

    @Test
    void theServingVersionOfADraftThatCollidesLaterIsNotReplaced() throws Exception {
        for (String kind : KINDS) {
            UUID exam = newExam(kind, parts(CLEAN));
            saveCheck(exam, paths("science=VALID", "english=VALID"));
            publish(exam, "science", "english").andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
            String served = servedState(exam);
            long revision = revision(exam);
            assertThat(served).isEqualTo("1|SERVING|science,english");

            // The draft now holds the colliding id; the structure hash and the evidence stay current (old code wrote it).
            assertThat(jdbc.update("UPDATE exam.exam_structure SET parts = replace(parts::text, ?, ?)::jsonb WHERE exam_id = ?",
                    "\"" + CLEAN + "\"", "\"" + COLLIDING + "\"", exam)).isEqualTo(1);
            entityManager.clear();
            assertThat(evidenceStatus(exam)).isEqualTo("VALID");

            publishRefused(exam);
            assertThat(servedState(exam)).as(kind).isEqualTo(served);
            assertThat(versions(exam)).as(kind).isEqualTo(1);
            assertThat(revision(exam)).as(kind + ": a refused publish changes nothing").isEqualTo(revision);
        }
    }

    private void publishRefused(UUID exam) throws Exception {
        publish(exam, "science", "english").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHECK_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("RULES_CHANGED"));
    }

    private String evidenceStatus(UUID exam) {
        return jdbc.queryForObject("SELECT result->>'status' FROM exam.exam_check_run WHERE exam_id = ?", String.class, exam);
    }

    private long versions(UUID exam) {
        return jdbc.queryForObject("SELECT count(*) FROM exam.exam_version WHERE exam_id = ?", Long.class, exam);
    }

    private String servedState(UUID exam) {
        return jdbc.queryForObject("SELECT string_agg(version_no || '|' || state || '|' || array_to_string(serving_path_ids, ','), ';' "
                + "ORDER BY version_no) FROM exam.exam_version WHERE exam_id = ?", String.class, exam);
    }
}
