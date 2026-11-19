package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
        // 2B §10: English has its own part, leaving ten science triples here.
        assertThat(ExamStructureValidator.countPaths(structure)).isEqualTo(10);
        var science = structure.parts().getFirst().options().getFirst();
        assertThat(science.sections()).hasSize(5);
        assertThat(science.chooseSubjects()).isEqualTo(3);
    }

    @Test
    void qdaBcaUseSharedSubjectPoolsInsteadOfStoredCombinations() throws Exception {
        for (String kind : List.of("QDA", "BCA")) {
            var templates = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exam-templates")
                            .with(asUser(Permissions.EXAM_VIEW)).param("kind", kind))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            var full = java.util.stream.StreamSupport.stream(templates.spliterator(), false)
                    .filter(template -> (kind + "_FULL").equals(template.path("code").asText()))
                    .findFirst().orElseThrow();
            var parts = full.path("structure").path("parts");
            assertThat(java.util.stream.StreamSupport.stream(parts.spliterator(), false)
                    .anyMatch(part -> part.hasNonNull("codeSource") && !part.path("subjectPool").isEmpty())).isTrue();
            for (var part : parts) {
                for (var option : part.path("options")) {
                    assertThat(option.path("kind").asText()).isNotEqualTo("COMBINATION");
                }
            }
        }
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
    void templateDurationIsExposedOnlyForSharedTiming() throws Exception {
        var templates = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exam-templates")
                        .with(asUser(Permissions.EXAM_VIEW)).param("kind", "HSA"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var hsa = java.util.stream.StreamSupport.stream(templates.spliterator(), false)
                .filter(template -> "HSA_FULL".equals(template.path("code").asText()))
                .findFirst().orElseThrow();
        assertThat(hsa.path("durationMinutes").isNull()).as("per-part duration is not shared").isTrue();
        mockMvc.perform(get("/api/v1/admin/exam-templates")
                        .with(asUser(Permissions.EXAM_VIEW)).param("kind", "BCA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='BCA_FULL')].durationMinutes", Matchers.hasItem(180)));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void templateReturnsStoredVersionMetadata() throws Exception {
        jdbc.update("update exam.exam_template set label = ?, summary = ?, build_mode = ?, is_default = ? where code = ?",
                "Current", "Summary", "STANDARD", true, "HSA_FULL");
        var templates = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exam-templates")
                        .with(asUser(Permissions.EXAM_VIEW)).param("kind", "HSA"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var hsa = java.util.stream.StreamSupport.stream(templates.spliterator(), false)
                .filter(template -> "HSA_FULL".equals(template.path("code").asText()))
                .findFirst().orElseThrow();
        assertThat(hsa.path("label").asText()).isEqualTo("Current");
        assertThat(hsa.path("summary").asText()).isEqualTo("Summary");
        assertThat(hsa.path("buildMode").asText()).isEqualTo("STANDARD");
        assertThat(hsa.path("isDefault").asBoolean()).isTrue();
    }

    @Test
    void existingTemplatesExposeDefaultPublishAndCountFlags() throws Exception {
        var templates = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exam-templates")
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(templates).isNotEmpty();
        var codes = new java.util.HashSet<String>();
        for (var template : templates) {
            String code = template.path("code").asText();
            codes.add(code);
            assertThat(template.path("publishable").isBoolean()).as(code).isTrue();
            assertThat(template.path("fixedCounts").isBoolean()).as(code).isTrue();
            // The literature draft cannot be published yet, and only the full V-ACT exam fixes its counts.
            assertThat(template.path("publishable").asBoolean()).as(code + " publishable")
                    .isEqualTo(!code.equals("THPTQG_LITERATURE_2025"));
            assertThat(template.path("fixedCounts").asBoolean()).as(code + " fixedCounts")
                    .isEqualTo(code.equals("VACT_FULL"));
        }
        assertThat(codes).contains("THPTQG_LITERATURE_2025", "VACT_FULL", "VACT_PART_LOGIC", "HSA_FULL");
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
        var literature = java.util.stream.StreamSupport.stream(
                        hsa.at("/structure/parts/1/sections/0/blocks").spliterator(), false)
                .filter(block -> "HSA_VN_PASSAGE".equals(block.path("templateGroupCode").asText()))
                .findFirst().orElseThrow();
        assertThat(literature.path("setCount").asInt()).isEqualTo(5);
        assertThat(literature.path("setSize").asInt()).isEqualTo(5);
        // 2B §10: fixed position bands keep cloze and the two reading sets separate.
        for (String group : List.of("HSA_EN_CLOZE", "HSA_EN_READING", "HSA_EN_READING_2")) {
            var english = java.util.stream.StreamSupport.stream(
                            hsa.at("/structure/parts/2/options/1/sections/0/blocks").spliterator(), false)
                    .filter(block -> group.equals(block.path("templateGroupCode").asText()))
                    .findFirst().orElseThrow();
            assertThat(english.path("setCount").asInt()).as(group).isEqualTo(1);
            assertThat(english.path("setSize").asInt()).as(group).isEqualTo(5);
        }
    }
    @Test
    void hsaMathSetGroupsDeclareSingleChoiceChildren() {
        // BA 2026-11-17 (spec 12): chùm Toán HSA chỉ chứa câu Một đáp án; mẫu phải
        // khai childAnswerType để bộ lọc nhóm chỉ nhận câu con Một đáp án (UNI-2754: chỉ tiêu cấp môn đếm chúng ở dòng Bộ câu hỏi).
        for (String code : List.of("HSA_FULL", "HSA_PART_MATH")) {
            String json = jdbc.queryForObject(
                    "SELECT structure::text FROM exam.exam_template WHERE code = ?",
                    String.class, code);
            var doc = new ExamStructureCodec(mapper).read(json);
            var blocks = doc.parts().stream()
                    .flatMap(part -> part.sections().stream())
                    .flatMap(section -> section.blocks().stream())
                    .filter(block -> "HSA_MATH_CONTEXT".equals(block.templateGroupCode()))
                    .toList();
            assertThat(blocks).as("%s Math set groups", code).hasSize(1);
            assertThat(blocks.getFirst().childAnswerType())
                    .isEqualTo(vn.unicom.thi360.questionbank.spi.QuestionType.SINGLE_CHOICE);
        }
    }

}
