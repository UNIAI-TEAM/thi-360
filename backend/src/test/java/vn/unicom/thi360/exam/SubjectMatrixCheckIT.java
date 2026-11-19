package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.identity.spi.Permissions;

@Transactional
class SubjectMatrixCheckIT extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalog;

    @Test
    void scienceShortfallNamesTheSubjectAndSeventeenWithoutInvalidatingEnglish() throws Exception {
        UUID physics = subject("LY");
        jdbc.update("""
                UPDATE questionbank.question SET status='RETIRED'
                WHERE subject_item_id=? AND parent_id IS NULL AND type='SINGLE_CHOICE' AND status='READY'
                  AND id NOT IN (SELECT id FROM questionbank.question WHERE subject_item_id=? AND parent_id IS NULL
                    AND type='SINGLE_CHOICE' AND status='READY' AND current_version_no IS NOT NULL ORDER BY id LIMIT 16)
                """, physics, physics);
        String sections = science("LY", "NATURAL") + "," + science("HOA", "NATURAL") + ","
                + science("SINH", "NATURAL") + "," + science("SU", "SOCIAL") + "," + science("DIA", "SOCIAL");
        String document = """
                {"parts":[{"id":"p","order":1,"name":"Subject choice","questionCount":50,
                "participation":{"mode":"CHOICE","choose":1},"options":[
                {"id":"english","kind":"SUBJECT","code":"ANH","sections":[{"id":"english-section","subjectCode":"ANH",
                "questionCount":50,"blocks":[{"id":"english-block","questionType":"SINGLE_CHOICE","questionCount":50}]}]},
                {"id":"science","kind":"SUBJECT_CHOICE","code":"SCIENCE","chooseSubjects":3,"countsByPick":[17,17,16],"sections":[%s]}]}]}
                """.formatted(sections);
        UUID exam = exam(document, 50);
        for (String code : List.of("ANH", "LY", "HOA", "SINH", "SU", "DIA")) {
            jdbc.update("INSERT INTO exam.exam_block_ref (id,exam_id,block_id,part_id,section_id,subject_item_id,question_type) "
                    + "VALUES (?,?,?,?,?,?,?)", UUID.randomUUID(), exam,
                    code.equals("ANH") ? "english-block" : code + "-block", "p",
                    code.equals("ANH") ? "english-section" : code, subject(code), "SINGLE_CHOICE");
        }
        JsonNode result = check(exam);
        JsonNode english = result.path("paths").get(0);
        JsonNode science = result.path("paths").get(1);
        assertThat(english.path("result").asText()).isEqualTo("VALID");
        assertThat(science.path("result").asText()).isEqualTo("INVALID");
        assertThat(science.path("selections")).hasSize(10);
        assertThat(science.path("issues").findValuesAsText("code"))
                .contains("CELL_SHORTAGE").doesNotContain("PART_TOTAL_MISMATCH", "PATH_TOTAL_MISMATCH");
        assertThat(science.path("issues").findValuesAsText("subjectCode")).contains("LY");
        assertThat(science.path("issues").findValues("subjectCount").stream().anyMatch(value -> value.asInt() == 17)).isTrue();
    }

    @Test
    void answerQuotaTotalFortyNineIsReported() throws Exception {
        String document = """
                {"parts":[{"id":"p","name":"Math","questionCount":50,"sections":[{"id":"s","subjectCode":"TOAN",
                "questionCount":50,"answerQuotas":{"unit":"COUNT","items":[{"answerType":"SINGLE_CHOICE","value":34},
                {"answerType":"SHORT_ANSWER","value":15}]},"blocks":[{"id":"b","questionCount":50}]}]}]}
                """;
        JsonNode result = check(exam(document, 50));
        assertThat(result.path("groups").findValuesAsText("code")).contains("ANSWER_QUOTA_TOTAL_MISMATCH");
    }

    private UUID exam(String document, int count) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam (id,code,code_normalized,name,scope,timing,question_count,duration_minutes) "
                + "VALUES (?,?,?,'Subject checks','FULL','SHARED',?,60)", id, id.toString(), id.toString(), count);
        jdbc.update("INSERT INTO exam.exam_structure (exam_id,parts,structure_hash) VALUES (?,?::jsonb,?)", id, document, id.toString());
        return id;
    }

    private JsonNode check(UUID examId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/exams/" + examId + "/check")
                .with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }

    private UUID subject(String code) { return catalog.findActiveItem("SUBJECT", code).orElseThrow().id(); }

    private static String science(String code, String group) {
        return "{\"id\":\"" + code + "\",\"subjectCode\":\"" + code + "\",\"choiceGroup\":\"" + group
                + "\",\"blocks\":[{\"id\":\"" + code + "-block\",\"questionType\":\"SINGLE_CHOICE\"}]}";
    }
}
