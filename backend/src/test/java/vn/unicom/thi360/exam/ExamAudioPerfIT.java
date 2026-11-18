package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/** BE-17 row 13: a 75-question exam with audio filters checks within the 30 second solver limit. */
@Tag("perf")
@Transactional
class ExamAudioPerfIT extends AbstractIntegrationTest {
    private static final UUID SUBJECT_GROUP = UUID.fromString("00000000-0000-7000-8100-000000000002");
    private static final String SC4 = "{\"type\":\"SINGLE_CHOICE\",\"options\":[{},{},{},{}]}";

    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    void seventyFiveQuestionExamWithAudioFilterChecksWithinThirtySeconds() throws Exception {
        UUID subjectId = UUID.randomUUID();
        String code = "PERF_" + subjectId.toString().replace("-", "");
        jdbc.update("INSERT INTO catalog.catalog_item(id,group_id,code,code_normalized,value,name_vi,path) VALUES "
                + "(?,?,?,?,(SELECT coalesce(max(value),0)+1 FROM catalog.catalog_item WHERE group_id=?),'Perf',?)",
                subjectId, SUBJECT_GROUP, code, code.toLowerCase(Locale.ROOT), SUBJECT_GROUP, "/" + subjectId + "/");
        UUID audio = UUID.randomUUID();
        jdbc.update("INSERT INTO questionbank.question_asset(id,kind,object_key,file_name,mime,size,sha256) "
                + "VALUES (?,'ATTACHMENT',?,'a.mp3','audio/mpeg',1,?)", audio, "k/" + audio, new byte[32]);
        // Pool with decoys on both sides of the audio filter.
        for (int i = 0; i < 300; i++) single(subjectId, i % 2 == 0 ? audio : null);
        for (int i = 0; i < 30; i++) set(subjectId, 4, audio);
        for (int i = 0; i < 30; i++) set(subjectId, 5, audio);
        for (int i = 0; i < 30; i++) set(subjectId, 10, null);
        for (int i = 0; i < 30; i++) set(subjectId, 10, audio);

        UUID exam = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam(id,code,code_normalized,name,scope,timing,question_count) "
                + "VALUES (?,?,?,'Perf','FULL','SHARED',75)", exam, exam.toString(), exam.toString());
        String listening = "{\"id\":\"p1\",\"questionCount\":35,\"sections\":[{\"id\":\"s1\",\"subjectCode\":\"" + code
                + "\",\"questionCount\":35,\"audioRequirement\":\"REQUIRED\",\"blocks\":["
                + "{\"id\":\"single\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":8},"
                + "{\"id\":\"sets4\",\"questionType\":\"QUESTION_SET\",\"questionCount\":12,\"setCount\":3,\"setSize\":4,"
                + "\"childAnswerType\":\"SINGLE_CHOICE\"},"
                + "{\"id\":\"sets5\",\"questionType\":\"QUESTION_SET\",\"questionCount\":15,\"setCount\":3,\"setSize\":5,"
                + "\"childAnswerType\":\"SINGLE_CHOICE\"}]}]}";
        String reading = "{\"id\":\"p2\",\"questionCount\":40,\"sections\":[{\"id\":\"s2\",\"subjectCode\":\"" + code
                + "\",\"questionCount\":40,\"audioRequirement\":\"FORBIDDEN\",\"blocks\":["
                + "{\"id\":\"sets10\",\"questionType\":\"QUESTION_SET\",\"questionCount\":40,\"setCount\":4,\"setSize\":10,"
                + "\"childAnswerType\":\"SINGLE_CHOICE\"}]}]}";
        jdbc.update("INSERT INTO exam.exam_structure(exam_id,parts,structure_hash) VALUES (?,?::jsonb,'perf')",
                exam, "{\"parts\":[" + listening + "," + reading + "]}");
        for (String[] ref : new String[][] {{"single", "p1", "s1"}, {"sets4", "p1", "s1"}, {"sets5", "p1", "s1"},
                {"sets10", "p2", "s2"}}) {
            jdbc.update("INSERT INTO exam.exam_block_ref(id,exam_id,block_id,part_id,section_id,subject_item_id) "
                    + "VALUES (?,?,?,?,?,?)", UUID.randomUUID(), exam, ref[0], ref[1], ref[2], subjectId);
        }

        long start = System.nanoTime();
        JsonNode result = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/" + exam + "/check")
                        .with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        System.out.println("PERF BE-17 audio-filtered 75-question check ms=" + elapsed
                + " status=" + result.path("status").asText() + " codes=" + result.path("groups").findValuesAsText("code"));
        assertThat(result.path("groups").findValuesAsText("code")).doesNotContain("AUDIO_SHORTAGE");
        assertThat(elapsed).isLessThan(30_000);
    }

    private void single(UUID subject, UUID attachment) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO questionbank.question(id,code,type,status,subject_item_id,current_version_no,points) "
                + "VALUES (?,?,'SINGLE_CHOICE','READY',?,1,1)", id, "Q-" + id, subject);
        jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) VALUES (?,1,?::jsonb)",
                id, snapshot(SC4, attachment));
    }

    private void set(UUID subject, int size, UUID attachment) {
        UUID root = UUID.randomUUID();
        jdbc.update("INSERT INTO questionbank.question(id,code,type,status,subject_item_id,current_version_no,points) "
                + "VALUES (?,?,'QUESTION_SET','READY',?,1,1)", root, "S-" + root, subject);
        jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) VALUES (?,1,?::jsonb)",
                root, snapshot("{\"type\":\"QUESTION_SET\",\"children\":[]}", attachment));
        for (int position = 0; position < size; position++) {
            UUID child = UUID.randomUUID();
            jdbc.update("INSERT INTO questionbank.question(id,code,type,status,parent_id,position,points) "
                    + "VALUES (?,?,'SINGLE_CHOICE','READY',?,?,1)", child, "C-" + child, root, position);
            jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) "
                    + "VALUES (?,1,?::jsonb)", child, "{\"body\":" + SC4 + "}");
        }
    }

    private static String snapshot(String body, UUID attachment) {
        return "{\"body\":" + body + ",\"attachmentIds\":" + (attachment == null ? "[]" : "[\"" + attachment + "\"]") + "}";
    }
}
