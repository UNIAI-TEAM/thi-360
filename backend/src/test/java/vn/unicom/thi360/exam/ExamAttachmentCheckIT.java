package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.api.ExternalServiceException;
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
    @MockitoSpyBean ObjectStorage storage;

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
        assertThat(healthy.path("sectionStates").get(0).path("state").asText()).isEqualTo("CHECKED");
        var before = unit(exam, "s");
        JsonNode unchanged = check(exam);
        assertThat(unchanged.path("sectionStates").get(0).path("state").asText()).isEqualTo("CHECKED");
        assertThat(unit(exam, "s")).isEqualTo(before);

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
        assertThat(result.path("paths").get(0).path("result").asText()).isEqualTo("INVALID");
        assertThat(result.path("sectionStates").get(0).path("state").asText()).isEqualTo("INCOMPLETE");
        var failed = unit(exam, "s");
        assertThat(failed.get("outcome")).isEqualTo("INVALID");
        for (String field : List.of("input_fingerprint", "source_revision", "witness", "checked_at", "duration_ms")) {
            assertThat(failed.get(field)).as(field).isEqualTo(before.get(field));
        }

        long revision = jdbc.queryForObject("SELECT revision FROM exam.exam WHERE id = ?", Long.class, exam);
        String publish = mockMvc.perform(post("/api/v1/admin/exams/" + exam + "/publish").with(asUser(Permissions.EXAM_PUBLISH))
                        .contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("{\"expectedRevision\":" + revision + ",\"pathIds\":[\"default\"]}"))
                .andExpect(status().is4xxClientError()).andReturn().getResponse().getContentAsString();
        assertThat(publish).contains("PATH_NOT_VALID");
        storage.put(lostKey, "mp3!".getBytes(StandardCharsets.UTF_8), "audio/mpeg");
        JsonNode restored = check(exam);
        assertThat(restored.path("status").asText()).isEqualTo("VALID");
        assertThat(restored.path("sectionStates").get(0).path("state").asText()).isEqualTo("CHECKED");
        assertThat(codes(restored)).doesNotContain("ATTACHMENT_MISSING");
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
        verify(storage, never()).exists(anyString());
    }

    @Test
    void storageOutageDoesNotPersistAResultOrMissingAttachmentIssue() throws Exception {
        UUID subject = subject();
        UUID file = asset(true);
        single(subject, file);
        UUID exam = exam(subject, 1, "{\"id\":\"b\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":1}");
        assertThat(check(exam).path("status").asText()).isEqualTo("VALID");
        var before = unit(exam, "s");
        String previous = jdbc.queryForObject("select result::text from exam.exam_check_run where exam_id=?", String.class, exam);
        String key = jdbc.queryForObject("select object_key from questionbank.question_asset where id=?", String.class, file);
        doThrow(new ExternalServiceException("INTERNAL_ERROR")).when(storage).exists(key);
        mockMvc.perform(post("/api/v1/admin/exams/" + exam + "/check").with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isBadGateway());
        assertThat(unit(exam, "s")).isEqualTo(before);
        assertThat(jdbc.queryForObject("select result::text from exam.exam_check_run where exam_id=?", String.class, exam))
                .isEqualTo(previous).doesNotContain("ATTACHMENT_MISSING");
    }

    @Test
    void scopedCheckOnlyUpdatesTheAffectedReusedUnit() throws Exception {
        UUID firstSubject = subject();
        UUID secondSubject = subject();
        UUID file = asset(true);
        single(firstSubject, file);
        single(secondSubject, null);
        UUID exam = exam(firstSubject, 1, "{\"id\":\"b\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":1}");
        String secondCode = jdbc.queryForObject("select code from catalog.catalog_item where id=?", String.class, secondSubject);
        String second = "{\"id\":\"s2\",\"subjectCode\":\"" + secondCode
                + "\",\"questionCount\":1,\"blocks\":[{\"id\":\"b2\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":1}]}";
        jdbc.update("update exam.exam_structure set parts=jsonb_set(parts,'{parts,0,sections}',"
                + "(parts #> '{parts,0,sections}') || ?::jsonb) where exam_id=?", "[" + second + "]", exam);
        jdbc.update("update exam.exam_structure set parts=jsonb_set(parts,'{parts,0,questionCount}','2'::jsonb) where exam_id=?", exam);
        jdbc.update("update exam.exam set question_count=2 where id=?", exam);
        jdbc.update("INSERT INTO exam.exam_block_ref(id,exam_id,block_id,part_id,section_id,subject_item_id) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), exam, "b2", "p", "s2", secondSubject);
        assertThat(check(exam).path("status").asText()).isEqualTo("VALID");
        var unaffected = unit(exam, "s2");
        String key = jdbc.queryForObject("select object_key from questionbank.question_asset where id=?", String.class, file);
        storage.delete(key);
        JsonNode scoped = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/" + exam + "/check")
                        .with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"SECTION\",\"sectionId\":\"s\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(scoped.path("status").asText()).isEqualTo("INVALID");
        assertThat(codes(scoped)).contains("ATTACHMENT_MISSING");
        assertThat(unit(exam, "s").get("outcome")).isEqualTo("INVALID");
        assertThat(scoped.path("sectionStates").get(0).path("state").asText()).isEqualTo("INCOMPLETE");
        assertThat(scoped.path("sectionStates").get(1).path("state").asText()).isEqualTo("CHECKED");
        assertThat(unit(exam, "s2")).isEqualTo(unaffected);
    }

    @Test
    void aWitnessedFailureIsSavedOnlyOnTheUnitWhoseOwnProofUsedTheLostFile() throws Exception {
        UUID subject = subject();
        String code = jdbc.queryForObject("select code from catalog.catalog_item where id=?", String.class, subject);
        // Candidates are taken in question-id order. Option A's own blocks take the two- and four-point questions, so
        // its unit proves the shared block with the lost one-point question (id order: four, lost, three); option B's
        // own blocks take the two- and three-point ones, so its unit proves the shared block with the four-point one.
        UUID lostFile = asset(true);
        String lostCode = single(subject, lostFile, 1, UUID.fromString("00000000-0000-7000-8000-000000000002"));
        single(subject, asset(true), 4, UUID.fromString("00000000-0000-7000-8000-000000000001"));
        single(subject, asset(true), 3, UUID.fromString("00000000-0000-7000-8000-000000000003"));
        single(subject, asset(true), 2, UUID.fromString("00000000-0000-7000-8000-000000000005"));
        UUID exam = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam(id,code,code_normalized,name,scope,timing,question_count) VALUES (?,?,?,'Attachment','FULL','SHARED',3)",
                exam, exam.toString(), exam.toString());
        String document = "{\"parts\":[{\"id\":\"p\",\"questionCount\":3,\"participation\":{\"mode\":\"CHOICE\",\"choose\":1},"
                + "\"sections\":[" + section("shared", code, 1, "bs", null) + "],\"options\":["
                + "{\"id\":\"A\",\"kind\":\"SUBJECT\",\"code\":\"" + code + "\",\"name\":\"A\",\"sections\":["
                + section("sa", code, 2, "ba2", 2, "ba4", 4) + "]},"
                + "{\"id\":\"B\",\"kind\":\"SUBJECT\",\"code\":\"" + code + "\",\"name\":\"B\",\"sections\":["
                + section("sb", code, 2, "bb2", 2, "bb3", 3) + "]}]}]}";
        jdbc.update("INSERT INTO exam.exam_structure(exam_id,parts,structure_hash) VALUES (?,?::jsonb,'attachment')", exam, document);
        for (String[] ref : new String[][] {{"bs", "shared"}, {"ba2", "sa"}, {"ba4", "sa"}, {"bb2", "sb"}, {"bb3", "sb"}}) {
            jdbc.update("INSERT INTO exam.exam_block_ref(id,exam_id,block_id,part_id,section_id,subject_item_id) VALUES (?,?,?,?,?,?)",
                    UUID.randomUUID(), exam, ref[0], "p", ref[1], subject);
        }
        JsonNode healthy = check(exam);
        assertThat(healthy.path("status").asText()).as(healthy.toString()).isEqualTo("VALID");
        assertThat(state(healthy, "sa")).isEqualTo("CHECKED");
        assertThat(state(healthy, "sb")).isEqualTo("CHECKED");
        var healthyBefore = unit(exam, "sb");

        String lostKey = jdbc.queryForObject("select object_key from questionbank.question_asset where id=?", String.class, lostFile);
        storage.delete(lostKey);
        JsonNode result = check(exam);
        List<JsonNode> missing = issues(result, "ATTACHMENT_MISSING");
        assertThat(missing).isNotEmpty().allSatisfy(issue -> {
            assertThat(issue.path("sectionId").asText()).isEqualTo("shared");
            assertThat(issue.path("questionCodes")).extracting(JsonNode::asText).containsExactly(lostCode);
        });
        assertThat(missing).extracting(issue -> issue.path("location").path("pathId").asText()).containsOnly("A");
        assertThat(state(result, "sa")).isEqualTo("INCOMPLETE");
        assertThat(state(result, "sb")).isEqualTo("CHECKED");
        assertThat(unit(exam, "sa").get("outcome")).isEqualTo("INVALID");
        // The unit whose proof never used the lost file is not saved: proof, fingerprint, time and duration unchanged.
        assertThat(unit(exam, "sb")).isEqualTo(healthyBefore);
        assertThat(unit(exam, "sb").get("outcome")).isEqualTo("VALID");
        assertThat(healthyBefore.get("witness").toString()).doesNotContain("00000000-0000-7000-8000-000000000002");
    }

    private static String state(JsonNode result, String sectionId) {
        for (JsonNode state : result.path("sectionStates")) {
            if (sectionId.equals(state.path("sectionId").asText())) return state.path("state").asText();
        }
        throw new AssertionError("no state for " + sectionId + " in " + result.path("sectionStates"));
    }

    /** One section of {@code count} questions, one block per (id, required points) pair; the first block may have none. */
    private static String section(String id, String subjectCode, int count, String block, Integer points) {
        return section(id, subjectCode, count, block, points, null, null);
    }

    private static String section(String id, String subjectCode, int count, String first, Integer firstPoints,
            String second, Integer secondPoints) {
        StringBuilder blocks = new StringBuilder(block(first, firstPoints));
        if (second != null) blocks.append(',').append(block(second, secondPoints));
        return "{\"id\":\"" + id + "\",\"subjectCode\":\"" + subjectCode + "\",\"questionCount\":" + count
                + ",\"blocks\":[" + blocks + "]}";
    }

    private static String block(String id, Integer points) {
        return "{\"id\":\"" + id + "\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":1"
                + (points == null ? "" : ",\"requiredPointsPerQuestion\":" + points) + "}";
    }

    private java.util.Map<String, Object> unit(UUID exam, String section) {
        return jdbc.queryForMap("select outcome,input_fingerprint,source_revision,witness::text as witness,checked_at,duration_ms "
                + "from exam.exam_check_unit where exam_id=? and ?=any(section_ids)", exam, section);
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
        return single(subject, asset, 1);
    }

    private String single(UUID subject, UUID asset, int points) {
        return single(subject, asset, points, UUID.randomUUID());
    }

    private String single(UUID subject, UUID asset, int points, UUID id) {
        jdbc.update("INSERT INTO questionbank.question(id,code,type,status,subject_item_id,current_version_no,points) VALUES (?,?,'SINGLE_CHOICE','READY',?,1,?)",
                id, "Q-" + id, subject, points);
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
