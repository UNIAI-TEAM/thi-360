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

    private UUID exam(String parts) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into exam.exam (id, code, code_normalized, name, scope, timing, question_count) values (?, ?, ?, 'Preview', 'FULL', 'SHARED', 3)",
                id, id.toString(), id.toString());
        jdbc.update("insert into exam.exam_structure (exam_id, parts, structure_hash) values (?, cast(? as jsonb), 'test')",
                id, "{\"parts\":" + parts + "}");
        return id;
    }

    private JsonNode previewResult(UUID exam, long seed) throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/" + exam + "/preview")
                        .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":{\"kind\":\"EXAM\"},\"seed\":" + seed + "}"))
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
