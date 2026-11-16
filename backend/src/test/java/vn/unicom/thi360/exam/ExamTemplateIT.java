package vn.unicom.thi360.exam;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

class ExamTemplateIT extends AbstractIntegrationTest {
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
