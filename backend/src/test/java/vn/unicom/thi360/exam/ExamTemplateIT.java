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
    void qdaBcaCombinationsKept() throws Exception {
        mockMvc.perform(get("/api/v1/admin/exam-templates")
                        .with(asUser(Permissions.EXAM_VIEW)).param("kind", "QDA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='QDA_FULL')].structure.parts[*].options[?(@.kind=='COMBINATION')].code",
                        Matchers.hasItems("QD01", "QD02", "QD03", "QD04")));
        mockMvc.perform(get("/api/v1/admin/exam-templates")
                        .with(asUser(Permissions.EXAM_VIEW)).param("kind", "BCA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='BCA_FULL')].structure.parts[*].options[?(@.kind=='COMBINATION')].code",
                        Matchers.hasItems("CA1", "CA2", "CA3", "CA4")));
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

    @Test
    void hsaTemplateExposesChoiceGroupsAndPassageSets() throws Exception {
        var response = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exam-templates")
                        .with(asUser(Permissions.EXAM_VIEW)).param("kind", "HSA"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        com.fasterxml.jackson.databind.JsonNode hsa = null;
        for (var template : response) {
            if ("HSA_FULL".equals(template.path("code").asText())) {
                hsa = template;
                break;
            }
        }
        assertThat(hsa).isNotNull();
        var science = hsa.at("/structure/parts/2/options/0/sections");
        java.util.Map<String, String> groups = new java.util.HashMap<>();
        science.forEach(section -> groups.put(section.path("subjectCode").asText(),
                section.path("choiceGroup").asText()));
        assertThat(groups).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                "LY", "NATURAL", "HOA", "NATURAL", "SINH", "NATURAL",
                "SU", "SOCIAL", "DIA", "SOCIAL"));
        var literature = hsa.at("/structure/parts/1/sections/0/blocks/1");
        assertThat(literature.path("setCount").asInt()).isEqualTo(5);
        assertThat(literature.path("setSize").asInt()).isEqualTo(5);
        var english = hsa.at("/structure/parts/2/options/1/sections/0/blocks/3");
        assertThat(english.path("setCount").asInt()).isEqualTo(3);
        assertThat(english.path("setSize").asInt()).isEqualTo(5);
    }
}
