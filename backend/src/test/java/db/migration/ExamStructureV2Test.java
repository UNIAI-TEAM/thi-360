package db.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Runs the structure-v2 converter on the real old-form sample structures extracted from
 * {@code db/devdata} at the wave base ({@code exam_structure.parts` of every seeded exam)
 * and compares them with the expected v2 snapshot, then checks the acceptance invariants:
 * full subjectPool for QDA/BCA, 17-question HSA science sections without remainder groups,
 * and no {@code match} / {@code COMBINATION} / {@code taskTypeCode} left.
 */
class ExamStructureV2Test {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void preservesLegacyMatrixEnforcementForSectionsAndBlocks() throws Exception {
        JsonNode input = JSON.readTree("""
                [{"sections":[{"matrix":{"enforcement":"REFERENCE"},
                  "blocks":[{"matrix":{"enforcement":"REFERENCE"}}]}]}]
                """);
        JsonNode result = V202611172353__ExamStructureV2.convert(input, "HSA");
        JsonNode section = result.get(0).path("sections").get(0);
        for (JsonNode matrix : List.of(section.path("matrix"), section.path("blocks").get(0).path("matrix"))) {
            assertEquals("REFERENCE", matrix.path("gradeEnforcement").asText());
            assertEquals("REFERENCE", matrix.path("difficultyEnforcement").asText());
            assertTrue(!matrix.has("enforcement"));
        }
    }

    @Test
    void subtractsFixedSetChildrenFromSubjectChoiceRemainder() throws Exception {
        for (String type : List.of("QUESTION_SET", "CLOZE")) {
            JsonNode input = JSON.readTree("""
                    [{"options":[{"kind":"SUBJECT_CHOICE","sections":[{"blocks":[
                      {"questionType":"%s","setCount":2,"setSize":3,"questionCount":null},
                      {"questionType":"SINGLE_CHOICE","templateGroupCode":"SCIENCE_REMAINDER"}
                    ]}]}]}]
                    """.formatted(type));
            JsonNode result = V202611172353__ExamStructureV2.convert(input, "HSA");
            JsonNode blocks = result.get(0).path("options").get(0).path("sections").get(0).path("blocks");
            int fixedCount = blocks.get(0).path("setCount").asInt() * blocks.get(0).path("setSize").asInt();
            assertEquals(11, blocks.get(1).path("questionCount").asInt(), type);
            assertEquals(17, fixedCount + blocks.get(1).path("questionCount").asInt(), type);
            assertEquals(result, V202611172353__ExamStructureV2.convert(result, "HSA"), type);
        }
    }

    @Test
    void convertsRealDevDataStructuresToExpectedV2() throws Exception {
        JsonNode input = read("/migration/exam-structure-v1.json");
        JsonNode expected = read("/migration/exam-structure-v2.json");
        assertEquals(input.size(), expected.size(), "fixture row count");

        for (int i = 0; i < input.size(); i++) {
            JsonNode row = input.get(i);
            JsonNode converted = V202611172353__ExamStructureV2.convert(row.get("parts"), row.get("kindCode").asText());
            JsonNode expectedRow = expected.get(i);
            assertEquals(expectedRow.get("examId"), row.get("examId"), "fixture order at " + i);
            assertEquals(expectedRow.get("parts"), converted,
                    "converted parts of " + row.get("templateCode").asText() + " (" + row.get("examId").asText() + ")");
        }
    }

    @Test
    void convertedStructuresMeetV2Acceptance() throws Exception {
        JsonNode input = read("/migration/exam-structure-v1.json");
        boolean sawQdaPool = false;
        boolean sawBcaPool = false;
        int scienceSections = 0;

        for (JsonNode row : input) {
            String kind = row.get("kindCode").asText();
            String template = row.get("templateCode").asText("");
            JsonNode parts = V202611172353__ExamStructureV2.convert(row.get("parts"), kind);

            String whole = parts.toString();
            if (whole.contains("COMBINATION")) {
                fail(template + " still has a COMBINATION option");
            }
            assertNoKey(parts, "match", template);
            assertNoKey(parts, "taskTypeCode", template);
            if (whole.contains("REMAINDER") || whole.contains("phần còn lại")) {
                fail(template + " still carries a remainder group");
            }

            for (JsonNode part : parts) {
                JsonNode pool = part.get("subjectPool");
                if (pool != null && pool.isArray() && !pool.isEmpty()) {
                    Set<String> subjects = new HashSet<>();
                    pool.forEach(s -> subjects.add(s.get("subjectCode").asText()));
                    if ("QDA".equals(kind) && template.equals("QDA_FULL")) {
                        sawQdaPool = true;
                        assertEquals(Set.of("LY", "HOA", "SINH", "SU", "DIA", "ANH"), subjects,
                                "QDA FULL subjectPool must cover every combination subject");
                        assertEquals("GLOBAL_LIST", part.path("codeSource").path("source").asText());
                    }
                    if ("BCA".equals(kind) && template.equals("BCA_FULL")) {
                        sawBcaPool = true;
                        assertEquals(Set.of("LY", "HOA", "SINH", "DIA"), subjects,
                                "BCA FULL subjectPool must cover every combination subject");
                    }
                }
                for (JsonNode option : part.path("options")) {
                    if ("SUBJECT_CHOICE".equals(option.path("kind").asText())) {
                        for (JsonNode section : option.path("sections")) {
                            scienceSections++;
                            assertEquals(17, section.path("questionCount").asInt(),
                                    template + " subject " + section.path("subjectCode").asText()
                                            + " must declare 17 questions (H3)");
                            int blockTotal = 0;
                            for (JsonNode block : section.path("blocks")) {
                                blockTotal += block.hasNonNull("setCount") && block.hasNonNull("setSize")
                                        ? block.get("setCount").asInt() * block.get("setSize").asInt()
                                        : block.path("questionCount").asInt();
                            }
                            assertEquals(17, blockTotal,
                                    template + " subject " + section.path("subjectCode").asText()
                                            + " blocks must cover all 17 questions");
                        }
                    }
                }
            }
        }
        assertTrue(sawQdaPool, "fixture must contain a QDA FULL combination part");
        assertTrue(sawBcaPool, "fixture must contain a BCA FULL combination part");
        assertTrue(scienceSections >= 5, "fixture must contain HSA science SUBJECT_CHOICE sections");
    }

    private static void assertNoKey(JsonNode node, String key, String template) {
        if (node.isObject()) {
            if (node.has(key)) {
                fail(template + " still has '" + key + "': " + node);
            }
            node.forEach(child -> assertNoKey(child, key, template));
        } else if (node.isArray()) {
            node.forEach(child -> assertNoKey(child, key, template));
        }
    }

    private static JsonNode read(String resource) throws Exception {
        try (InputStream stream = ExamStructureV2Test.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing test resource: " + resource);
            }
            return JSON.readTree(stream);
        }
    }
}
