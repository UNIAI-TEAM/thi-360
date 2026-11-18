package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/** BE-20 rows 11 and 12: preview attachments of single questions and set roots, and display segments of a part. */
@Transactional
class ExamPreviewAttachmentsIT extends AbstractIntegrationTest {
    private static final UUID SUBJECT_GROUP = UUID.fromString("00000000-0000-7000-8100-000000000002");
    private static final String CHOICE = "{\"type\":\"SINGLE_CHOICE\",\"options\":[{\"id\":\"a\",\"content\":\"A\",\"correct\":true},{\"id\":\"b\",\"content\":\"B\",\"correct\":false}]}";
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    private String subjectCode;
    private UUID subjectId;

    @Test
    void aSingleQuestionAndASetRootCarryTheirAttachmentsAsIdNameMime() throws Exception {
        subject();
        UUID audio = asset("cau-1.mp3", "audio/mpeg");
        UUID passage = asset("bai-nghe.mp3", "audio/mpeg");
        UUID pdf = asset("de.pdf", "application/pdf");
        single(List.of(audio));
        set(List.of(passage, pdf));
        JsonNode paper = preview(exam(part("[]")), 7).path("paper");
        List<JsonNode> items = new ArrayList<>();
        paper.path("parts").get(0).path("blocks").forEach(block -> block.path("items").forEach(items::add));
        JsonNode single = items.stream().filter(item -> "QUESTION".equals(item.path("kind").asText())).findFirst().orElseThrow();
        JsonNode set = items.stream().filter(item -> "SET".equals(item.path("kind").asText())).findFirst().orElseThrow();

        JsonNode attachments = single.path("question").path("attachments");
        assertThat(attachments).hasSize(1);
        assertThat(fieldNames(attachments.get(0))).containsExactlyInAnyOrder("id", "name", "mime");
        assertThat(attachments.get(0).path("id").asText()).isEqualTo(audio.toString());
        assertThat(attachments.get(0).path("name").asText()).isEqualTo("cau-1.mp3");
        assertThat(attachments.get(0).path("mime").asText()).isEqualTo("audio/mpeg");
        assertThat(single.path("attachments")).isEmpty();

        assertThat(set.path("attachments")).extracting(node -> node.path("id").asText())
                .containsExactly(passage.toString(), pdf.toString());
        assertThat(set.path("attachments").get(1).path("mime").asText()).isEqualTo("application/pdf");
        assertThat(set.path("children")).hasSize(2).allSatisfy(child -> assertThat(child.path("attachments")).isEmpty());
    }

    @Test
    void displaySegmentsAreReturnedAsDeclaredEvenWhenTheBoundaryCutsASet() throws Exception {
        subject();
        single(List.of());
        set(List.of());
        String segments = """
                [{"code":"P1","name":"Phần I","fromPosition":1,"toPosition":2,"commandCount":2,"maxPoints":2},
                 {"code":"P2","name":"Phần II","fromPosition":3,"toPosition":3,"commandCount":1,"maxPoints":1}]""";
        JsonNode paperPart = preview(exam(part(segments)), 7).path("paper").path("parts").get(0);
        JsonNode returned = paperPart.path("displaySegments");
        assertThat(returned).hasSize(2);
        assertThat(fieldNames(returned.get(0))).containsExactlyInAnyOrder("code", "name", "fromPosition", "toPosition",
                "commandCount", "maxPoints");
        assertThat(returned.get(0).path("code").asText()).isEqualTo("P1");
        assertThat(returned.get(0).path("toPosition").asInt()).isEqualTo(2);
        assertThat(returned.get(1).path("fromPosition").asInt()).isEqualTo(3);
        assertThat(returned.get(1).path("maxPoints").decimalValue()).isEqualByComparingTo("1");
        assertThat(paperPart.path("lastNumber").asInt()).isEqualTo(3);
    }

    @Test
    void aPartWithoutSegmentsReturnsAnEmptyList() throws Exception {
        subject();
        single(List.of());
        set(List.of());
        JsonNode paperPart = preview(exam(part("[]")), 7).path("paper").path("parts").get(0);
        assertThat(paperPart.path("displaySegments").isArray()).isTrue();
        assertThat(paperPart.path("displaySegments")).isEmpty();
    }

    @Test
    void realCommandsOrPointsThatDifferFromTheDeclaredNumbersFailThePreviewAtThePart() throws Exception {
        subject();
        single(List.of());
        set(List.of());
        String segments = """
                [{"code":"P1","name":"Phần I","fromPosition":1,"toPosition":2,"commandCount":3,"maxPoints":2},
                 {"code":"P2","name":"Phần II","fromPosition":3,"toPosition":3,"commandCount":1,"maxPoints":4}]""";
        UUID exam = exam(part(segments));
        JsonNode result = previewResult(exam, 7);
        assertThat(result.path("status").asText()).isEqualTo("FAILED");
        JsonNode issues = result.path("issues");
        assertThat(issues).hasSize(2);
        JsonNode commands = issues.get(0);
        assertThat(commands.path("code").asText()).isEqualTo("DISPLAY_SEGMENT_MISMATCH");
        assertThat(commands.path("severity").asText()).isEqualTo("ERROR");
        assertThat(commands.path("group").asText()).isEqualTo("DISPLAY");
        assertThat(commands.path("partId").asText()).isEqualTo("part");
        assertThat(commands.path("segmentCode").asText()).isEqualTo("P1");
        assertThat(commands.path("dimension").asText()).isEqualTo("COMMANDS");
        assertThat(commands.path("required").decimalValue()).isEqualByComparingTo("3");
        assertThat(commands.path("available").decimalValue()).isEqualByComparingTo("2");
        JsonNode points = issues.get(1);
        assertThat(points.path("segmentCode").asText()).isEqualTo("P2");
        assertThat(points.path("dimension").asText()).isEqualTo("POINTS");
        assertThat(points.path("required").decimalValue()).isEqualByComparingTo("4");
        assertThat(points.path("available").decimalValue()).isEqualByComparingTo("1");
        assertThat(points.path("location").path("partId").asText()).isEqualTo("part");
    }

    private static final String EXAM_BODY = "{\"target\":{\"kind\":\"EXAM\"},\"seed\":7}";
    private static final String LEGACY_BODY = "{\"pathId\":\"default\",\"seed\":7}";

    private static String sectionBody(String sectionId, int count) {
        return "{\"target\":{\"kind\":\"SECTION\",\"sectionId\":\"" + sectionId + "\",\"sectionQuestionCount\":" + count
                + "},\"seed\":7}";
    }

    @Test
    void aSegmentMismatchFailsEveryRequestShapeThatGeneratesTheWholePart() throws Exception {
        subject();
        single(List.of());
        set(List.of());
        String segments = """
                [{"code":"P1","name":"Phần I","fromPosition":1,"toPosition":2,"commandCount":3,"maxPoints":2},
                 {"code":"P2","name":"Phần II","fromPosition":3,"toPosition":3,"commandCount":1,"maxPoints":4}]""";
        UUID exam = exam(part(segments));
        // The same one-section paper, asked as an explicit EXAM, a legacy pathId/seed and a section that is the whole part.
        for (String body : List.of(EXAM_BODY, LEGACY_BODY, sectionBody("section", 3))) {
            JsonNode result = previewResult(exam, body);
            assertThat(result.path("status").asText()).as(body).isEqualTo("FAILED");
            assertThat(result.path("issues")).as(body).hasSize(2);
            JsonNode commands = result.path("issues").get(0);
            assertThat(commands.path("code").asText()).isEqualTo("DISPLAY_SEGMENT_MISMATCH");
            assertThat(commands.path("partId").asText()).isEqualTo("part");
            assertThat(commands.path("segmentCode").asText()).isEqualTo("P1");
            assertThat(commands.path("dimension").asText()).isEqualTo("COMMANDS");
            assertThat(commands.path("required").decimalValue()).isEqualByComparingTo("3");
            assertThat(commands.path("available").decimalValue()).isEqualByComparingTo("2");
            JsonNode points = result.path("issues").get(1);
            assertThat(points.path("segmentCode").asText()).isEqualTo("P2");
            assertThat(points.path("dimension").asText()).isEqualTo("POINTS");
            assertThat(points.path("required").decimalValue()).isEqualByComparingTo("4");
            assertThat(points.path("available").decimalValue()).isEqualByComparingTo("1");
        }
    }

    @Test
    void correctSegmentsAreReturnedByEveryRequestShapeThatGeneratesTheWholePart() throws Exception {
        subject();
        single(List.of());
        set(List.of());
        String segments = """
                [{"code":"P1","name":"Phần I","fromPosition":1,"toPosition":2,"commandCount":2,"maxPoints":2},
                 {"code":"P2","name":"Phần II","fromPosition":3,"toPosition":3,"commandCount":1,"maxPoints":1}]""";
        UUID exam = exam(part(segments));
        for (String body : List.of(EXAM_BODY, LEGACY_BODY, sectionBody("section", 3))) {
            JsonNode result = previewResult(exam, body);
            assertThat(result.path("status").asText()).as(result.toString()).isEqualTo("GENERATED");
            JsonNode returned = result.path("paper").path("parts").get(0).path("displaySegments");
            assertThat(returned).as(body).extracting(node -> node.path("code").asText()).containsExactly("P1", "P2");
        }
    }

    @Test
    void aSectionOfAMultiSectionPartIsAPartialProjectionThatNeitherReturnsNorValidatesTheFullPartSegments() throws Exception {
        subject();
        single(List.of());
        set(List.of());
        String segments = """
                [{"code":"P1","name":"Phần I","fromPosition":1,"toPosition":1,"commandCount":1,"maxPoints":1},
                 {"code":"P2","name":"Phần II","fromPosition":2,"toPosition":3,"commandCount":2,"maxPoints":2}]""";
        UUID exam = exam(twoSectionPart(segments));
        JsonNode whole = previewResult(exam, EXAM_BODY);
        assertThat(whole.path("status").asText()).as(whole.toString()).isEqualTo("GENERATED");
        assertThat(whole.path("paper").path("parts").get(0).path("displaySegments")).hasSize(2);
        // Alone, the second section holds numbers 1 and 2, so P2 (positions 2 to 3) could never match: no validation.
        JsonNode partial = previewResult(exam, sectionBody("second", 2));
        assertThat(partial.path("status").asText()).as(partial.toString()).isEqualTo("GENERATED");
        JsonNode returned = partial.path("paper").path("parts").get(0);
        assertThat(returned.path("lastNumber").asInt()).isEqualTo(2);
        assertThat(returned.path("displaySegments").isArray()).isTrue();
        assertThat(returned.path("displaySegments")).isEmpty();
    }

    @Test
    void segmentTotalsCountMixedSetCommandsAndDecimalPointsAcrossTheBoundary() throws Exception {
        subject();
        single(List.of());
        set(List.of());
        UUID singleId = jdbc.queryForObject("select id from questionbank.question where subject_item_id=? and type='SINGLE_CHOICE'", UUID.class, subjectId);
        UUID root = jdbc.queryForObject("select id from questionbank.question where subject_item_id=? and type='QUESTION_SET'", UUID.class, subjectId);
        UUID first = jdbc.queryForObject("select id from questionbank.question where parent_id=? and position=0", UUID.class, root);
        UUID second = jdbc.queryForObject("select id from questionbank.question where parent_id=? and position=1", UUID.class, root);
        jdbc.update("update questionbank.question set points=0.5 where id=?", singleId);
        jdbc.update("update questionbank.question set type='TRUE_FALSE', points=1.25 where id=?", first);
        jdbc.update("update questionbank.question set points=0.75 where id=?", second);
        jdbc.update("update questionbank.question_version set snapshot=?::jsonb where question_id=?", """
                {"content":"true false","points":1.25,"body":{"type":"TRUE_FALSE","statements":[
                {"id":"a","content":"a","answer":true},{"id":"b","content":"b","answer":false},
                {"id":"c","content":"c","answer":true},{"id":"d","content":"d","answer":false}]}}
                """, first);
        String correct = """
                [{"code":"P1","name":"First","fromPosition":1,"toPosition":2,"commandCount":5,"maxPoints":1.75},
                 {"code":"P2","name":"Second","fromPosition":3,"toPosition":3,"commandCount":1,"maxPoints":0.75}]""";
        String moved = """
                [{"code":"P1","name":"First","fromPosition":1,"toPosition":2,"commandCount":4,"maxPoints":1.50},
                 {"code":"P2","name":"Second","fromPosition":3,"toPosition":3,"commandCount":2,"maxPoints":1.00}]""";
        String structure = part(correct).replace("\"questionCount\":3,\"displaySegments\"",
                "\"questionCount\":3,\"maxPoints\":2.5,\"displaySegments\"")
                .replace(",\"childAnswerType\":\"SINGLE_CHOICE\"", "");
        JsonNode generated = previewResult(exam(structure), EXAM_BODY);
        assertThat(generated.path("status").asText()).as(generated.toString()).isEqualTo("GENERATED");
        JsonNode generatedPart = generated.path("paper").path("parts").get(0);
        assertThat(generatedPart.path("points").decimalValue()).isEqualByComparingTo("2.5");
        assertThat(generatedPart.path("displaySegments").get(0).path("commandCount").asInt()).isEqualTo(5);
        assertThat(generatedPart.path("displaySegments").get(0).path("maxPoints").decimalValue()).isEqualByComparingTo("1.75");
        assertThat(generatedPart.path("displaySegments").get(1).path("maxPoints").decimalValue()).isEqualByComparingTo("0.75");
        JsonNode result = previewResult(exam(structure.replace(correct, moved)), EXAM_BODY);
        assertThat(result.path("status").asText()).as(result.toString()).isEqualTo("FAILED");
        assertThat(result.path("issues")).hasSize(4);
        String[] dimensions = {"COMMANDS", "POINTS", "COMMANDS", "POINTS"};
        String[] required = {"4", "1.5", "2", "1"};
        String[] actual = {"5", "1.75", "1", "0.75"};
        for (int i = 0; i < 4; i++) {
            JsonNode issue = result.path("issues").get(i);
            assertThat(issue.path("code").asText()).isEqualTo("DISPLAY_SEGMENT_MISMATCH");
            assertThat(issue.path("segmentCode").asText()).isEqualTo(i < 2 ? "P1" : "P2");
            assertThat(issue.path("dimension").asText()).isEqualTo(dimensions[i]);
            assertThat(issue.path("required").decimalValue()).isEqualByComparingTo(required[i]);
            assertThat(issue.path("available").decimalValue()).isEqualByComparingTo(actual[i]);
            assertThat(issue.path("location").path("partId").asText()).isEqualTo("part");
        }
    }

    private static final String PATH_BODY = "{\"target\":{\"kind\":\"PATH\",\"pathId\":\"opt\"},\"seed\":7}";

    @Test
    void aSectionThatIsTheWholeRealizablePartOfAnOptionValidatesAndReturnsSegmentsLikeItsPath() throws Exception {
        subject();
        single(List.of());
        String correct = """
                [{"code":"P1","name":"Phần I","fromPosition":1,"toPosition":1,"commandCount":1,"maxPoints":1}]""";
        UUID matching = exam(optionPart(correct, optionSection("s", 1, SINGLE_BLOCK)));
        for (String body : List.of(PATH_BODY, sectionBody("s", 1))) {
            JsonNode result = previewResult(matching, body);
            assertThat(result.path("status").asText()).as(result.toString()).isEqualTo("GENERATED");
            assertThat(result.path("paper").path("parts").get(0).path("displaySegments")).as(body)
                    .extracting(node -> node.path("code").asText()).containsExactly("P1");
        }
        String wrong = correct.replace("\"commandCount\":1", "\"commandCount\":2");
        UUID mismatching = exam(optionPart(wrong, optionSection("s", 1, SINGLE_BLOCK)));
        for (String body : List.of(PATH_BODY, sectionBody("s", 1))) {
            JsonNode result = previewResult(mismatching, body);
            assertThat(result.path("status").asText()).as(body).isEqualTo("FAILED");
            assertThat(result.path("issues")).as(body).hasSize(1);
            JsonNode issue = result.path("issues").get(0);
            assertThat(issue.path("code").asText()).isEqualTo("DISPLAY_SEGMENT_MISMATCH");
            assertThat(issue.path("segmentCode").asText()).isEqualTo("P1");
            assertThat(issue.path("dimension").asText()).isEqualTo("COMMANDS");
            assertThat(issue.path("required").decimalValue()).isEqualByComparingTo("2");
            assertThat(issue.path("available").decimalValue()).isEqualByComparingTo("1");
        }
    }

    @Test
    void oneSectionOfAnOptionWithSeveralSectionsIsAPartialProjectionWithoutSegments() throws Exception {
        subject();
        single(List.of());
        set(List.of());
        String segments = """
                [{"code":"P1","name":"Phần I","fromPosition":1,"toPosition":1,"commandCount":1,"maxPoints":1},
                 {"code":"P2","name":"Phần II","fromPosition":2,"toPosition":3,"commandCount":2,"maxPoints":2}]""";
        UUID exam = exam(optionPart(segments, optionSection("first", 1, SINGLE_BLOCK) + "," + optionSection("second", 2, SET_BLOCK)));
        JsonNode whole = previewResult(exam, PATH_BODY);
        assertThat(whole.path("status").asText()).as(whole.toString()).isEqualTo("GENERATED");
        assertThat(whole.path("paper").path("parts").get(0).path("displaySegments")).hasSize(2);
        // Alone, the second section holds numbers 1 and 2, so P2 (positions 2 to 3) could not match: no validation.
        JsonNode partial = previewResult(exam, sectionBody("second", 2));
        assertThat(partial.path("status").asText()).as(partial.toString()).isEqualTo("GENERATED");
        assertThat(partial.path("paper").path("parts").get(0).path("displaySegments")).isEmpty();
    }

    @Test
    void aSectionNextToACommonSectionOfAChoicePartIsAPartialProjectionWithoutSegments() throws Exception {
        subject();
        single(List.of());
        set(List.of());
        String segments = """
                [{"code":"P1","name":"Phần I","fromPosition":1,"toPosition":1,"commandCount":1,"maxPoints":1},
                 {"code":"P2","name":"Phần II","fromPosition":2,"toPosition":3,"commandCount":2,"maxPoints":2}]""";
        String part = optionPart(segments, optionSection("second", 2, SET_BLOCK)).replace("\"options\":",
                "\"sections\":[" + optionSection("common", 1, SINGLE_BLOCK) + "],\"options\":");
        UUID exam = exam(part);
        JsonNode whole = previewResult(exam, PATH_BODY);
        assertThat(whole.path("status").asText()).as(whole.toString()).isEqualTo("GENERATED");
        JsonNode partial = previewResult(exam, sectionBody("second", 2));
        assertThat(partial.path("status").asText()).as(partial.toString()).isEqualTo("GENERATED");
        assertThat(partial.path("paper").path("parts").get(0).path("displaySegments")).isEmpty();
    }

    // ---- fixtures ------------------------------------------------------------------------------------------

    private void subject() {
        subjectId = UUID.randomUUID();
        subjectCode = "PVA_" + subjectId.toString().replace("-", "");
        jdbc.update("""
                insert into catalog.catalog_item (id, group_id, code, code_normalized, value,
                    name_vi, name_en, display_order, status, is_system, depth, path)
                values (?, ?, ?, ?, (select coalesce(max(value),0)+1 from catalog.catalog_item where group_id=?),
                    'Preview attachments', 'Preview attachments', 0, 1, false, 1, ?)
                """, subjectId, SUBJECT_GROUP, subjectCode, subjectCode.toLowerCase(Locale.ROOT), SUBJECT_GROUP, "/" + subjectId + "/");
    }

    private UUID asset(String name, String mime) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO questionbank.question_asset(id,kind,object_key,file_name,mime,size,sha256) VALUES (?,'ATTACHMENT',?,?,?,4,decode('00','hex'))",
                id, "questions/be20-preview-" + id, name, mime);
        return id;
    }

    private static String attachmentIds(List<UUID> assets) {
        return assets.isEmpty() ? "" : "\"attachmentIds\":[" + String.join(",", assets.stream().map(id -> "\"" + id + "\"").toList()) + "],";
    }

    private void single(List<UUID> assets) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) values (?,?,'SINGLE_CHOICE','READY',?,1,1)",
                id, "S-" + id, subjectId);
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,cast(? as jsonb))",
                id, "{" + attachmentIds(assets) + "\"content\":\"single\",\"points\":1,\"body\":" + CHOICE + "}");
    }

    private void set(List<UUID> assets) {
        UUID root = UUID.randomUUID();
        jdbc.update("insert into questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) values (?,?,'QUESTION_SET','READY',?,1,2)",
                root, "SET-" + root, subjectId);
        StringBuilder children = new StringBuilder();
        for (int position = 0; position < 2; position++) {
            UUID child = UUID.randomUUID();
            jdbc.update("insert into questionbank.question(id, code, type, status, parent_id, position, points) values (?,?,'SINGLE_CHOICE','READY',?,?,1)",
                    child, "C-" + child, root, position);
            jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,cast(? as jsonb))",
                    child, "{\"content\":\"child\",\"points\":1,\"body\":" + CHOICE + "}");
            children.append(position > 0 ? "," : "").append("{\"id\":\"").append(child).append("\",\"content\":\"child\",\"body\":")
                    .append(CHOICE).append("}");
        }
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,cast(? as jsonb))",
                root, "{" + attachmentIds(assets) + "\"content\":\"set\",\"points\":2,\"body\":{\"type\":\"QUESTION_SET\",\"passage\":\"passage\",\"children\":["
                        + children + "]}}");
    }

    /** One part of three numbers: a single question, then a set of two. */
    private String part(String displaySegments) {
        return """
                [{"id":"part","order":1,"name":"Part","questionCount":3,"displaySegments":%s,
                  "participation":{"mode":"ALL"},"sections":[{"id":"section","subjectCode":"%s","questionCount":3,
                  "blocks":[{"id":"single","name":"Single","questionType":"SINGLE_CHOICE","questionCount":1},
                  {"id":"set","name":"Set","questionType":"QUESTION_SET","questionCount":2,"setCount":1,"setSize":2,"childAnswerType":"SINGLE_CHOICE"}]}],
                  "options":[]}]
                """.formatted(displaySegments, subjectCode);
    }

    /** One part of two sections: a single question, then a set of two. */
    private String twoSectionPart(String displaySegments) {
        return """
                [{"id":"part","order":1,"name":"Part","questionCount":3,"displaySegments":%s,
                  "participation":{"mode":"ALL"},"sections":[
                  {"id":"first","subjectCode":"%s","questionCount":1,
                   "blocks":[{"id":"single","name":"Single","questionType":"SINGLE_CHOICE","questionCount":1}]},
                  {"id":"second","subjectCode":"%s","questionCount":2,
                   "blocks":[{"id":"set","name":"Set","questionType":"QUESTION_SET","questionCount":2,"setCount":1,"setSize":2,"childAnswerType":"SINGLE_CHOICE"}]}],
                  "options":[]}]
                """.formatted(displaySegments, subjectCode, subjectCode);
    }

    private static final String SINGLE_BLOCK = "{\"id\":\"single\",\"name\":\"Single\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":1}";
    private static final String SET_BLOCK = "{\"id\":\"set\",\"name\":\"Set\",\"questionType\":\"QUESTION_SET\",\"questionCount\":2,\"setCount\":1,\"setSize\":2,\"childAnswerType\":\"SINGLE_CHOICE\"}";

    private String optionSection(String id, int count, String block) {
        return "{\"id\":\"" + id + "\",\"subjectCode\":\"" + subjectCode + "\",\"questionCount\":" + count
                + ",\"blocks\":[" + block + "]}";
    }

    /** A CHOICE part with one SUBJECT option "opt" holding the given sections; its sections hold every number. */
    private String optionPart(String displaySegments, String sections) {
        int count = sections.contains("\"id\":\"second\"") ? 3 : sections.contains("\"id\":\"common\"") ? 3 : 1;
        return """
                [{"id":"part","order":1,"name":"Part","questionCount":%d,"maxPoints":%d,"displaySegments":%s,
                  "participation":{"mode":"CHOICE","choose":1},
                  "options":[{"id":"opt","kind":"SUBJECT","code":"%s","name":"Option","sections":[%s]}]}]
                """.formatted(count, count, displaySegments, subjectCode, sections);
    }

    private UUID exam(String parts) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into exam.exam (id, code, code_normalized, name, scope, timing, question_count) values (?, ?, ?, 'Preview', 'FULL', 'SHARED', 3)",
                id, id.toString(), id.toString());
        jdbc.update("insert into exam.exam_structure (exam_id, parts, structure_hash) values (?, cast(? as jsonb), 'test')",
                id, "{\"parts\":" + parts + "}");
        return id;
    }

    private JsonNode previewResult(UUID exam, long seed) throws Exception {
        return previewResult(exam, "{\"target\":{\"kind\":\"EXAM\"},\"seed\":" + seed + "}");
    }

    private JsonNode previewResult(UUID exam, String body) throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/" + exam + "/preview")
                        .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode preview(UUID exam, long seed) throws Exception {
        JsonNode result = previewResult(exam, seed);
        assertThat(result.path("status").asText()).as(result.toString()).isEqualTo("GENERATED");
        return result;
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
