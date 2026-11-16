package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.exam.domain.ExamStructureCodec;
import vn.unicom.thi360.exam.domain.ExamStructureOps;
import vn.unicom.thi360.exam.domain.ExamStructureValidator;
import vn.unicom.thi360.identity.spi.Permissions;

class ExamTemplateIT extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    @Test
    void b10ScienceTriplesCountAsTenPaths() {
        String json = jdbc.queryForObject(
                "SELECT structure::text FROM exam.exam_template WHERE code = 'HSA_PART_SCIENCE'",
                String.class);
        var structure = ExamStructureOps.assignMissingIds(new ExamStructureCodec(mapper).read(json));
        // Ten science triples plus the English option; 12 scoring cases do not change path count.
        assertThat(ExamStructureValidator.countPaths(structure)).isEqualTo(11);
        var science = structure.parts().getFirst().options().getFirst();
        assertThat(science.sections()).hasSize(5);
        assertThat(science.chooseSubjects()).isEqualTo(3);
    }
    @Test
    void hsaContainsFiveSubjectChoiceWithoutScienceCombinations() throws Exception {
        mockMvc.perform(get("/api/v1/admin/exam-templates")
                        .with(asUser(Permissions.EXAM_VIEW))
                        .param("kind", "HSA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='HSA_FULL')].structure.parts[*].options[?(@.kind=='SUBJECT_CHOICE')].chooseSubjects", Matchers.hasItem(3)))
                .andExpect(jsonPath("$[?(@.code=='HSA_FULL')].structure.parts[*].options[?(@.kind=='SUBJECT_CHOICE')].sections[*].subjectCode", Matchers.hasItems("LY", "HOA", "SINH", "SU", "DIA")))
                .andExpect(jsonPath("$[?(@.code=='HSA_FULL')].structure.parts[*].options[?(@.kind=='COMBINATION')]").isEmpty());
    }

    @Test
    void templatesRequireExamView() throws Exception {
        mockMvc.perform(get("/api/v1/admin/exam-templates")
                        .with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/exam-templates"))
                .andExpect(status().isUnauthorized());
    }
}
