package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** EX-1 AC-14 to AC-17 (E24, TM): an exam with a choice made after the attempt starts publishes every path together. */
class ExamPublishAllPathsIT extends AbstractExamPublishIT {
    /** Part 3 of HSA: Science (choose 3 of 5 subjects) or English, chosen while the attempt runs. */
    static final String HSA_PART3 = """
            [{"id":"p3","participation":{"mode":"CHOICE","choose":1},"options":[
              {"id":"science","kind":"SUBJECT_CHOICE","countsByPick":[17,17,16],"sections":[]},
              {"id":"english","kind":"SUBJECT","code":"ANH","sections":[]}]}]
            """;
    /** QDA: the combination code is chosen while the attempt runs, from the catalog. */
    static final String CODE_PART = "[{\"id\":\"codes\",\"codeSource\":{\"source\":\"GLOBAL_LIST\"},\"subjectPool\":[]}]";
    static final List<String> QDA_CODES = List.of("QD01", "QD02", "QD03", "QD04", "QD05");

    private String[] qdaPaths(int count) {
        return QDA_CODES.stream().limit(count).map(code -> "codes:" + code + "=VALID").toArray(String[]::new);
    }

    private long versions(UUID exam) {
        return jdbc.queryForObject("SELECT count(*) FROM exam.exam_version WHERE exam_id = ?", Long.class, exam);
    }

    @Test
    void hsaFullSinglePathIsRejected() throws Exception {
        UUID exam = newExam("HSA", HSA_PART3);
        saveCheck(exam, paths("science=VALID", "english=VALID"));
        publish(exam, "science").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PUBLISH_ALL_PATHS_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("english"));
        assertThat(versions(exam)).isZero();
        publish(exam, "english").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PUBLISH_ALL_PATHS_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("science"));
        assertThat(versions(exam)).isZero();
    }

    @Test
    void qdaFullMissingCodeIsRejected() throws Exception {
        UUID exam = newExam("QDA", CODE_PART);
        saveCheck(exam, paths(qdaPaths(5)));
        publish(exam, "codes:QD01", "codes:QD02", "codes:QD03", "codes:QD05")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PUBLISH_ALL_PATHS_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("codes:QD04"));
        assertThat(versions(exam)).isZero();
    }

    @Test
    void allPathsPublishTogether() throws Exception {
        UUID hsa = newExam("HSA", HSA_PART3);
        saveCheck(hsa, paths("science=VALID", "english=VALID"));
        publish(hsa, "science", "english").andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.keptPaths").isEmpty());
        assertThat(jdbc.queryForObject("SELECT array_to_string(serving_path_ids, ',') FROM exam.exam_version WHERE exam_id = ? AND version_no = 1",
                String.class, hsa)).isEqualTo("science,english");

        UUID qda = newExam("QDA", CODE_PART);
        saveCheck(qda, paths(qdaPaths(5)));
        publish(qda, "codes:QD05", "codes:QD04", "codes:QD03", "codes:QD02", "codes:QD01").andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.keptPaths").isEmpty());
        assertThat(jdbc.queryForList("SELECT DISTINCT version_no FROM exam.exam_version WHERE exam_id = ? AND state = 'SERVING'",
                Integer.class, qda)).containsExactly(1);
        assertThat(jdbc.queryForObject("SELECT cardinality(serving_path_ids) FROM exam.exam_version WHERE exam_id = ?", Integer.class, qda))
                .isEqualTo(5);
    }

    @Test
    void preStartChoicesMayPublishOnePath() throws Exception {
        // BCA: the code (CA1 to CA4) is chosen on the prepare screen, before the clock starts.
        UUID bca = newExam("BCA", CODE_PART);
        saveCheck(bca, paths("codes:CA1=VALID", "codes:CA2=VALID", "codes:CA3=VALID", "codes:CA4=VALID"));
        publish(bca, "codes:CA2").andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        // Informatics: the orientation (BRANCH) is chosen before the attempt starts (2B K2).
        UUID spt = newExam("SPT", "[{\"id\":\"p2\",\"participation\":{\"mode\":\"CHOICE\",\"choose\":1},\"options\":["
                + "{\"id\":\"cs\",\"kind\":\"BRANCH\",\"sections\":[]},{\"id\":\"ict\",\"kind\":\"BRANCH\",\"sections\":[]}]}]");
        saveCheck(spt, paths("cs=VALID", "ict=VALID"));
        publish(spt, "cs").andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        assertThat(jdbc.queryForObject("SELECT array_to_string(serving_path_ids, ',') FROM exam.exam_version WHERE exam_id = ?",
                String.class, spt)).isEqualTo("cs");
    }
}
