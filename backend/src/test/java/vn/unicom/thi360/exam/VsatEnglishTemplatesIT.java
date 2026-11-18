package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * BE-21 rows 1, 2, 16 and 17 (addendum specification 5.2, H40, H41, H44) through
 * {@code GET /exam-templates}: the stored V-SAT English templates carry the specification's
 * structure, display segments and quota shape.
 */
@Transactional
class VsatEnglishTemplatesIT extends AbstractIntegrationTest {
    private static final String FULL = "VSAT_SUBJECT_ANH";
    private static final List<String> PARTS = List.of("VSAT_PART_ANH_P2", "VSAT_PART_ANH_P3",
            "VSAT_PART_ANH_P4");

    @Autowired private ObjectMapper mapper;

    private final Map<String, JsonNode> templates = new HashMap<>();

    @BeforeEach
    void loadTemplates() throws Exception {
        JsonNode all = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exam-templates")
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        all.forEach(template -> templates.put(template.path("code").asText(), template));
    }

    private JsonNode template(String code) {
        JsonNode template = templates.get(code);
        assertThat(template).as("template %s exists", code).isNotNull();
        return template;
    }

    private static List<JsonNode> blocks(JsonNode template) {
        List<JsonNode> blocks = new ArrayList<>();
        for (JsonNode part : template.path("structure").path("parts")) {
            for (JsonNode section : part.path("sections")) {
                section.path("blocks").forEach(blocks::add);
            }
        }
        return blocks;
    }

    private static List<JsonNode> sections(JsonNode template) {
        List<JsonNode> sections = new ArrayList<>();
        for (JsonNode part : template.path("structure").path("parts")) {
            part.path("sections").forEach(sections::add);
        }
        return sections;
    }

    /** Row 1: one part, four rows 7 + 8 + 5 + 5, 25 questions, 60 minutes, 150 points, locked. */
    @Test
    void fullTemplateTotals() {
        JsonNode template = template(FULL);
        JsonNode structure = template.path("structure");
        assertThat(structure.path("parts")).hasSize(1);
        JsonNode part = structure.path("parts").get(0);
        assertThat(part.path("questionCount").asInt()).isEqualTo(25);
        assertThat(part.path("maxPoints").decimalValue()).isEqualByComparingTo("150");
        assertThat(part.path("sections")).hasSize(1);
        assertThat(blocks(template).stream().map(b -> b.path("questionCount").asInt()).toList())
                .containsExactly(7, 8, 5, 5);
        assertThat(blocks(template).stream().map(b -> b.path("questionType").asText()).toList())
                .containsExactly("TRUE_FALSE", "QUESTION_SET", "MATCHING", "CLOZE");
        assertThat(template.path("timing").asText()).isEqualTo("SHARED");
        assertThat(template.path("durationMinutes").asInt()).isEqualTo(60);
        assertThat(template.path("maxScore").decimalValue()).isEqualByComparingTo("150");
        assertThat(template.path("fixedBlockCounts").asBoolean()).isTrue();
        assertThat(template.path("fixedCounts").asBoolean()).isFalse();
        assertThat(template.path("scope").asText()).isEqualTo("SUBJECT");
        assertThat(template.path("subjectCode").asText()).isEqualTo("ANH");
    }

    /** Row 1: the shared reading row declares its mixed positions (H39). */
    @Test
    void fullTemplateSharedReadingRow() {
        JsonNode set = blocks(template(FULL)).get(1);
        assertThat(set.path("name").asText()).isEqualTo("Bài đọc dùng chung – câu 8–15");
        assertThat(set.path("setCount").asInt()).isEqualTo(1);
        assertThat(set.path("setSize").asInt()).isEqualTo(8);
        assertThat(set.path("childAnswerType").isNull() || set.path("childAnswerType").isMissingNode())
                .as("childAnswerType left empty for the mixed set").isTrue();
        JsonNode conditions = set.path("childConditions");
        assertThat(conditions).hasSize(2);
        assertThat(conditions.get(0).path("from").asInt()).isEqualTo(1);
        assertThat(conditions.get(0).path("to").asInt()).isEqualTo(2);
        assertThat(conditions.get(0).path("answerType").asText()).isEqualTo("TRUE_FALSE");
        assertThat(conditions.get(0).path("itemShape").path("statements").asInt()).isEqualTo(4);
        assertThat(conditions.get(1).path("from").asInt()).isEqualTo(3);
        assertThat(conditions.get(1).path("to").asInt()).isEqualTo(8);
        assertThat(conditions.get(1).path("answerType").asText()).isEqualTo("SINGLE_CHOICE");
    }

    /** Row 2: display segments I–IV with the declared command and point totals (H40). */
    @Test
    void fullTemplateDisplaySegments() {
        JsonNode segments = template(FULL).path("structure").path("parts").get(0)
                .path("displaySegments");
        assertThat(segments).hasSize(4);
        int[][] expected = {{1, 9, 36, 54}, {10, 15, 6, 36}, {16, 20, 20, 30}, {21, 25, 5, 30}};
        for (int i = 0; i < expected.length; i++) {
            JsonNode segment = segments.get(i);
            assertThat(segment.path("fromPosition").asInt()).as("segment %d", i).isEqualTo(expected[i][0]);
            assertThat(segment.path("toPosition").asInt()).as("segment %d", i).isEqualTo(expected[i][1]);
            assertThat(segment.path("commandCount").asInt()).as("segment %d", i).isEqualTo(expected[i][2]);
            assertThat(segment.path("maxPoints").decimalValue()).as("segment %d", i)
                    .isEqualByComparingTo(BigDecimal.valueOf(expected[i][3]));
            assertThat(segment.path("name").asText()).startsWith("Phần ");
        }
    }

    /** Rows 7-9 template side: the three dedicated PART templates carry subject ANH. */
    @Test
    void partTemplatesExistWithSubjectCode() {
        for (String code : PARTS) {
            JsonNode template = template(code);
            assertThat(template.path("scope").asText()).as(code).isEqualTo("PART");
            assertThat(template.path("subjectCode").asText()).as(code).isEqualTo("ANH");
            assertThat(template.path("fixedBlockCounts").asBoolean()).as(code).isFalse();
            JsonNode part = template.path("structure").path("parts").get(0);
            assertThat(part.path("displaySegments").isEmpty()).as(code + " carries no segments")
                    .isTrue();
        }
        JsonNode p2 = blocks(template("VSAT_PART_ANH_P2")).get(0);
        assertThat(p2.path("questionType").asText()).isEqualTo("QUESTION_SET");
        assertThat(p2.path("setSize").asInt()).isEqualTo(6);
        assertThat(p2.path("childAnswerType").asText()).isEqualTo("SINGLE_CHOICE");
        assertThat(blocks(template("VSAT_PART_ANH_P3")).get(0).path("questionType").asText())
                .isEqualTo("MATCHING");
        assertThat(blocks(template("VSAT_PART_ANH_P4")).get(0).path("questionType").asText())
                .isEqualTo("CLOZE");
    }

    /** Row 16: no whole-subject PROMPT rows on the PART templates; the matching type reads "Ghép đôi". */
    @Test
    void partTemplatesHaveNoSubjectStrandsAndMatchingIsGhepDoi() {
        for (String code : PARTS) {
            JsonNode template = template(code);
            assertThat(template.path("structure").path("examQuotas").isEmpty())
                    .as(code + " exam quotas").isTrue();
            for (JsonNode section : sections(template)) {
                assertThat(section.path("topicQuotas").isEmpty())
                        .as(code + " topic quotas").isTrue();
            }
            List<String> names = blocks(template).stream().map(b -> b.path("name").asText()).toList();
            assertThat(names).noneMatch(name -> name.contains("Ghép hợp"));
        }
        JsonNode matching = blocks(template("VSAT_PART_ANH_P3")).get(0);
        assertThat(matching.path("name").asText()).contains("Ghép đôi");
        assertThat(blocks(template(FULL)).stream().map(b -> b.path("name").asText()).toList())
                .noneMatch(name -> name.contains("Ghép hợp"));
    }

    /**
     * Row 17: at most one PROMPT/REFERENCE quota per dimension, no audioRequirement anywhere and no
     * subject pool on the part that declares displaySegments.
     */
    @Test
    void quotaShapeAudioAndPoolRulesHoldOnEveryEnglishTemplate() {
        for (String code : List.of(FULL, "VSAT_PART_ANH_P2", "VSAT_PART_ANH_P3", "VSAT_PART_ANH_P4")) {
            JsonNode template = template(code);
            Set<String> promptDimensions = new HashSet<>();
            for (JsonNode quota : template.path("structure").path("examQuotas")) {
                if ("PROMPT".equals(quota.path("basis").asText())
                        && "REFERENCE".equals(quota.path("enforcement").asText())) {
                    assertThat(promptDimensions.add(quota.path("dimension").asText()))
                            .as(code + " duplicate PROMPT dimension").isTrue();
                }
            }
            for (JsonNode section : sections(template)) {
                assertThat(section.has("audioRequirement")).as(code + " audio").isFalse();
                for (JsonNode quota : section.path("topicQuotas")) {
                    if ("PROMPT".equals(quota.path("basis").asText())) {
                        assertThat(quota.path("enforcement").asText()).isEqualTo("REFERENCE");
                    }
                }
            }
            for (JsonNode part : template.path("structure").path("parts")) {
                if (!part.path("displaySegments").isEmpty()) {
                    assertThat(part.path("subjectPool").isEmpty())
                            .as(code + " subject pool on a segmented part").isTrue();
                }
            }
        }
    }
}
