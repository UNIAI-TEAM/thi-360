package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** EX-1 AC-39 (BS 8): the rule covers the first publish; a path that is not valid may stay out. */
class ExamPublishE24IT extends AbstractExamPublishIT {

    @Test
    void firstPublishNeedsEveryValidPath() throws Exception {
        UUID exam = newExam("HSA", ExamPublishAllPathsIT.HSA_PART3);
        saveCheck(exam, paths("science=VALID", "english=VALID"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_version WHERE exam_id = ?", Long.class, exam)).isZero();
        publish(exam, "science").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PUBLISH_ALL_PATHS_REQUIRED"));
        // Both valid paths together: the first publish succeeds.
        publish(exam, "science", "english").andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void ineligiblePathMayStayOut() throws Exception {
        UUID exam = newExam("HSA", ExamPublishAllPathsIT.HSA_PART3);
        saveCheck(exam, paths("science=VALID", "english=INVALID"));
        publish(exam, "science").andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.keptPaths").isEmpty());
        assertThat(jdbc.queryForObject("SELECT array_to_string(serving_path_ids, ',') FROM exam.exam_version WHERE exam_id = ?",
                String.class, exam)).isEqualTo("science");
        // The invalid path itself is still refused with the code it always had.
        entityManager.clear();
        saveCheck(exam, paths("science=VALID", "english=INVALID"));
        publish(exam, "science", "english").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PATH_NOT_VALID"));
    }

    @Test
    void everyServingPathIsRequiredAgain() throws Exception {
        UUID exam = newExam("QDA", ExamPublishAllPathsIT.CODE_PART);
        saveCheck(exam, paths("codes:QD01=VALID", "codes:QD02=VALID"));
        publish(exam, "codes:QD01", "codes:QD02").andExpect(status().isOk());
        // A new code appears: the next version carries the serving codes too, so every path shares one version.
        jdbc.update("UPDATE exam.exam SET description = 'Changed description' WHERE id = ?", exam);
        saveCheck(exam, paths("codes:QD01=VALID", "codes:QD02=VALID", "codes:QD03=VALID"));
        publish(exam, "codes:QD03").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PUBLISH_ALL_PATHS_REQUIRED"));
        publish(exam, "codes:QD01", "codes:QD02", "codes:QD03").andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        assertThat(jdbc.queryForList("SELECT DISTINCT version_no FROM exam.exam_version WHERE exam_id = ? AND state = 'SERVING'",
                Integer.class, exam)).containsExactly(2);
    }

    /** AD-60, R1-14: a serving path that turned INVALID blocks the publish and is named as serving-but-INVALID. */
    @Test
    void servingInvalidPathIsListedAsBlocking() throws Exception {
        UUID exam = newExam("HSA", ExamPublishAllPathsIT.HSA_PART3);
        saveCheck(exam, paths("science=VALID", "english=VALID"));
        publish(exam, "science", "english").andExpect(status().isOk());
        jdbc.update("UPDATE exam.exam SET description = 'Changed description' WHERE id = ?", exam);
        saveCheck(exam, paths("science=VALID", "english=INVALID"));
        publish(exam, "science").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PUBLISH_ALL_PATHS_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("pathIds"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("PATH_SERVING_INVALID"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("english"));
        // Selecting it does not help: it is not VALID.
        publish(exam, "science", "english").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PATH_NOT_VALID"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_version WHERE exam_id = ?", Long.class, exam)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT array_to_string(serving_path_ids, ',') FROM exam.exam_version WHERE exam_id = ? AND state = 'SERVING'",
                String.class, exam)).isEqualTo("science,english");
    }

    /** R1-1: a path that vanished from the fresh check (structure edit) is still serving, so it blocks and is named. */
    @Test
    void servingPathVanishedFromCheckBlocksPublish() throws Exception {
        UUID exam = newExam("HSA", ExamPublishAllPathsIT.HSA_PART3);
        saveCheck(exam, paths("science=VALID", "english=VALID"));
        publish(exam, "science", "english").andExpect(status().isOk());
        jdbc.update("UPDATE exam.exam SET description = 'Changed description' WHERE id = ?", exam);
        // english was replaced by english2 (a new option id) and one more path is valid and not selected.
        saveCheck(exam, paths("science=VALID", "english2=VALID", "extra=VALID"));
        publish(exam, "science", "english2").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PUBLISH_ALL_PATHS_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2))
                .andExpect(jsonPath("$.fieldErrors[?(@.message=='english')].code").value("PATH_SERVING_MISSING_FROM_CHECK"))
                .andExpect(jsonPath("$.fieldErrors[?(@.message=='extra')].code").value("PATH_VALID_NOT_SELECTED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_version WHERE exam_id = ?", Long.class, exam)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT DISTINCT version_no FROM exam.exam_version WHERE exam_id = ? AND state = 'SERVING'",
                Integer.class, exam)).containsExactly(1);
    }

    /** R2-3: nothing changed in the configuration, a fresh check finds a serving path INVALID: named, not NOTHING_TO_PUBLISH. */
    @Test
    void unchangedHsaWithServingPathNowInvalidIsNamed() throws Exception {
        UUID exam = newExam("HSA", ExamPublishAllPathsIT.HSA_PART3);
        saveCheck(exam, paths("science=VALID", "english=VALID"));
        publish(exam, "science", "english").andExpect(status().isOk());
        entityManager.clear();
        saveCheck(exam, paths("science=VALID", "english=INVALID"));
        publish(exam, "science").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PUBLISH_ALL_PATHS_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("PATH_SERVING_INVALID"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("english"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_version WHERE exam_id = ?", Long.class, exam)).isEqualTo(1);
    }

    @Test
    void unchangedQdaWithServingPathNowInvalidIsNamed() throws Exception {
        UUID exam = newExam("QDA", ExamPublishAllPathsIT.CODE_PART);
        saveCheck(exam, paths("codes:QD01=VALID", "codes:QD02=VALID"));
        publish(exam, "codes:QD01", "codes:QD02").andExpect(status().isOk());
        entityManager.clear();
        saveCheck(exam, paths("codes:QD01=VALID", "codes:QD02=INVALID"));
        publish(exam, "codes:QD01").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PUBLISH_ALL_PATHS_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("PATH_SERVING_INVALID"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("codes:QD02"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_version WHERE exam_id = ?", Long.class, exam)).isEqualTo(1);
    }
}
