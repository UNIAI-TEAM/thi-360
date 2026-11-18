package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.storage.ObjectStorage;

/**
 * BE-20 rows 5 and 6 (H48) on the suite's in-memory object storage. The bean is shared by every test class of the
 * context, so each test puts only objects of its own and removes them again.
 */
@Transactional
class ExamAttachmentCheckIT extends AbstractIntegrationTest {
    private static final UUID SUBJECT_GROUP = UUID.fromString("00000000-0000-7000-8100-000000000002");
    private static final String SINGLE = "{\"type\":\"SINGLE_CHOICE\",\"options\":[{\"id\":\"a\",\"correct\":true},{\"id\":\"b\",\"correct\":false}]}";
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired ObjectStorage storage;

    private final List<String> stored = new ArrayList<>();

    @AfterEach
    void restoreStorage() {
        stored.forEach(storage::delete);
        stored.clear();
    }

    @Test
    void aFileMissingFromTheStorageFailsTheCheckAtItsQuestionAndBlocksPublishing() throws Exception {
        UUID subject = subject();
        String presentCode = single(subject, asset(true));
        String lostCode = single(subject, asset(true));
        UUID exam = exam(subject, 2, "{\"id\":\"b\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":2}");

        JsonNode healthy = check(exam);
        assertThat(codes(healthy)).doesNotContain("ATTACHMENT_MISSING");
        assertThat(healthy.path("status").asText()).isEqualTo("VALID");

        String lostKey = jdbc.queryForObject("SELECT a.object_key FROM questionbank.question q JOIN questionbank.question_version v "
                + "ON v.question_id = q.id JOIN questionbank.question_asset a ON a.id = (v.snapshot->'attachmentIds'->>0)::uuid "
                + "WHERE q.code = ?", String.class, lostCode);
        storage.delete(lostKey);
        JsonNode result = check(exam);
        List<JsonNode> missing = issues(result, "ATTACHMENT_MISSING");
        assertThat(missing).hasSize(1);
        JsonNode issue = missing.getFirst();
        assertThat(issue.path("severity").asText()).isEqualTo("ERROR");
        assertThat(issue.path("group").asText()).isEqualTo("SOURCE");
        assertThat(issue.path("blockId").asText()).isEqualTo("b");
        assertThat(issue.path("questionCodes")).extracting(JsonNode::asText).containsExactly(lostCode);
        assertThat(issue.path("questionCodes")).extracting(JsonNode::asText).doesNotContain(presentCode);
        assertThat(result.path("status").asText()).isEqualTo("INVALID");

        long revision = jdbc.queryForObject("SELECT revision FROM exam.exam WHERE id = ?", Long.class, exam);
        String publish = mockMvc.perform(post("/api/v1/admin/exams/" + exam + "/publish").with(asUser(Permissions.EXAM_PUBLISH))
                        .contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("{\"expectedRevision\":" + revision + ",\"pathIds\":[\"default\"]}"))
                .andExpect(status().is4xxClientError()).andReturn().getResponse().getContentAsString();
        assertThat(publish).contains("PATH_NOT_VALID");
    }

    @Test
    void aSetReportsItsRootQuestionOnceWhenOneOfItsFilesIsMissing() throws Exception {
        UUID subject = subject();
        UUID first = asset(true);
        UUID second = asset(false);
        UUID root = UUID.randomUUID();
        jdbc.update("INSERT INTO questionbank.question(id,code,type,status,subject_item_id,current_version_no,points) VALUES (?,?,'QUESTION_SET','READY',?,1,1)",
                root, "S-" + root, subject);
        StringBuilder children = new StringBuilder();
        for (int position = 0; position < 2; position++) {
            UUID child = UUID.randomUUID();
            jdbc.update("INSERT INTO questionbank.question(id,code,type,status,parent_id,position,points) VALUES (?,?,'SINGLE_CHOICE','READY',?,?,1)",
                    child, "C-" + child, root, position);
            jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) VALUES (?,1,?::jsonb)", child, "{\"body\":" + SINGLE + "}");
            children.append(position > 0 ? "," : "").append("{\"id\":\"").append(child).append("\",\"body\":").append(SINGLE).append("}");
        }
        jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) VALUES (?,1,?::jsonb)", root,
                "{\"attachmentIds\":[\"" + first + "\",\"" + second + "\"],\"body\":{\"type\":\"QUESTION_SET\",\"children\":[" + children + "]}}");
        UUID exam = exam(subject, 2, "{\"id\":\"b\",\"questionType\":\"QUESTION_SET\",\"questionCount\":2,\"setCount\":1,\"setSize\":2,"
                + "\"childAnswerType\":\"SINGLE_CHOICE\"}");
        List<JsonNode> missing = issues(check(exam), "ATTACHMENT_MISSING");
        assertThat(missing).hasSize(1);
        assertThat(missing.getFirst().path("questionCodes")).extracting(JsonNode::asText).containsExactly("S-" + root);
        assertThat(missing.getFirst().path("location").has("childPosition")).isFalse();
    }

    @Test
    void anExamWithoutAnyAttachmentRaisesNothingNew() throws Exception {
        UUID subject = subject();
        single(subject, null);
        single(subject, null);
        JsonNode result = check(exam(subject, 2, "{\"id\":\"b\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":2}"));
        assertThat(codes(result)).doesNotContain("ATTACHMENT_MISSING");
        assertThat(result.path("status").asText()).isEqualTo("VALID");
    }

    // ---- fixtures ------------------------------------------------------------------------------------------

    private UUID asset(boolean inStorage) {
        UUID id = UUID.randomUUID();
        String key = "questions/be20-" + id;
        jdbc.update("INSERT INTO questionbank.question_asset(id,kind,object_key,file_name,mime,size,sha256) VALUES (?,'ATTACHMENT',?,?,?,?,decode('00','hex'))",
                id, key, "audio-" + id + ".mp3", "audio/mpeg", 4);
        if (inStorage) {
            storage.put(key, "mp3!".getBytes(StandardCharsets.UTF_8), "audio/mpeg");
            stored.add(key);
        }
        return id;
    }

    /** One READY single-choice question; with an asset its snapshot lists it as an attachment. Returns the code. */
    private String single(UUID subject, UUID asset) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO questionbank.question(id,code,type,status,subject_item_id,current_version_no,points) VALUES (?,?,'SINGLE_CHOICE','READY',?,1,1)",
                id, "Q-" + id, subject);
        String attachments = asset == null ? "" : "\"attachmentIds\":[\"" + asset + "\"],";
        jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) VALUES (?,1,?::jsonb)", id,
                "{" + attachments + "\"body\":" + SINGLE + "}");
        return "Q-" + id;
    }

    private UUID subject() {
        UUID id = UUID.randomUUID();
        String code = "ATT_" + id.toString().replace("-", "");
        jdbc.update("INSERT INTO catalog.catalog_item(id,group_id,code,code_normalized,value,name_vi,path) VALUES (?,?,?,?,(SELECT coalesce(max(value),0)+1 FROM catalog.catalog_item WHERE group_id=?),'Attachment',?)",
                id, SUBJECT_GROUP, code, code.toLowerCase(Locale.ROOT), SUBJECT_GROUP, "/" + id + "/");
        return id;
    }

    private UUID exam(UUID subject, int count, String block) {
        String code = jdbc.queryForObject("SELECT code FROM catalog.catalog_item WHERE id = ?", String.class, subject);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam(id,code,code_normalized,name,scope,timing,question_count) VALUES (?,?,?,'Attachment','FULL','SHARED',?)",
                id, id.toString(), id.toString(), count);
        String document = "{\"parts\":[{\"id\":\"p\",\"questionCount\":" + count + ",\"sections\":[{\"id\":\"s\",\"subjectCode\":\""
                + code + "\",\"questionCount\":" + count + ",\"blocks\":[" + block + "]}]}]}";
        jdbc.update("INSERT INTO exam.exam_structure(exam_id,parts,structure_hash) VALUES (?,?::jsonb,'attachment')", id, document);
        jdbc.update("INSERT INTO exam.exam_block_ref(id,exam_id,block_id,part_id,section_id,subject_item_id) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), id, "b", "p", "s", subject);
        return id;
    }

    private JsonNode check(UUID exam) throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/" + exam + "/check").with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static List<String> codes(JsonNode result) {
        return result.path("groups").findValuesAsText("code");
    }

    private static List<JsonNode> issues(JsonNode result, String code) {
        return result.path("groups").findValues("issues").stream()
                .flatMap(rows -> StreamSupport.stream(rows.spliterator(), false))
                .filter(issue -> code.equals(issue.path("code").asText())).toList();
    }
}
