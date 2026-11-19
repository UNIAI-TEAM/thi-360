package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.storage.ObjectStorage;

/**
 * BE-19 plan row 5 on the merged head: an exam whose rows require audio is solved over the seeded H-SCA
 * English listening questions (BE-19 sample data; V-SAT seeds no audio), and the real attachment check finds
 * every MP3 in the object storage that {@code DevQuestionAssetSeeder} filled. The test puts and deletes nothing.
 */
@Transactional
class SampleAudioAttachmentCheckIT extends AbstractIntegrationTest {
    private static final String LISTENING = "SEED-HSCA-ANH-LISTEN-";

    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @MockitoSpyBean ObjectStorage storage;

    @Test
    void examOverSeededSampleAudioQuestionsReportsNoMissingAttachment() throws Exception {
        UUID subject = jdbc.queryForObject("SELECT id FROM catalog.catalog_item WHERE group_id = "
                + "'00000000-0000-7000-8100-000000000002' AND code = 'ANH' AND status = 1", UUID.class);
        UUID exam = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam(id,code,code_normalized,name,scope,timing,question_count,duration_minutes) "
                + "VALUES (?,?,?,'Sample audio','FULL','SHARED',35,60)", exam, exam.toString(), exam.toString());
        String document = "{\"parts\":[{\"id\":\"p\",\"questionCount\":35,\"sections\":[{\"id\":\"s\",\"subjectCode\":\"ANH\","
                + "\"questionCount\":35,\"audioRequirement\":\"REQUIRED\",\"blocks\":["
                + "{\"id\":\"single\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":8},"
                + "{\"id\":\"sets4\",\"questionType\":\"QUESTION_SET\",\"questionCount\":12,\"setCount\":3,\"setSize\":4,"
                + "\"childAnswerType\":\"SINGLE_CHOICE\"},"
                + "{\"id\":\"sets5\",\"questionType\":\"QUESTION_SET\",\"questionCount\":15,\"setCount\":3,\"setSize\":5,"
                + "\"childAnswerType\":\"SINGLE_CHOICE\"}]}]}]}";
        jdbc.update("INSERT INTO exam.exam_structure(exam_id,parts,structure_hash) VALUES (?,?::jsonb,'sample-audio')",
                exam, document);
        for (String block : List.of("single", "sets4", "sets5")) {
            jdbc.update("INSERT INTO exam.exam_block_ref(id,exam_id,block_id,part_id,section_id,subject_item_id) "
                    + "VALUES (?,?,?,?,?,?)", UUID.randomUUID(), exam, block, "p", "s", subject);
        }

        // Startup (the asset seeder) already called exists(); only the calls of this check count.
        clearInvocations(storage);
        JsonNode result = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/" + exam + "/check")
                        .with(asUser(Permissions.EXAM_MODIFY))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(result.path("groups").findValuesAsText("code")).doesNotContain("ATTACHMENT_MISSING");
        assertThat(result.path("status").asText()).as(result.toString()).isEqualTo("VALID");

        Map<String, List<UUID>> picks = CheckWitnessPicks.byBlock(jdbc, mapper, exam);
        assertThat(picks.get("single")).hasSize(8).doesNotHaveDuplicates();
        assertThat(picks.get("sets4")).hasSize(3).doesNotHaveDuplicates();
        assertThat(picks.get("sets5")).hasSize(3).doesNotHaveDuplicates();

        List<String> keys = new ArrayList<>();
        for (var entry : picks.entrySet()) {
            for (UUID root : entry.getValue()) {
                var row = jdbc.queryForMap("SELECT q.code, q.type FROM questionbank.question q WHERE q.id = ?", root);
                assertThat((String) row.get("code")).as(entry.getKey()).startsWith(LISTENING);
                assertThat(row.get("type")).isEqualTo("single".equals(entry.getKey()) ? "SINGLE_CHOICE" : "QUESTION_SET");
                // The audio is on the root; for a set the children carry none, so the root's file is the one to find.
                List<Map<String, Object>> assets = jdbc.queryForList("""
                        SELECT a.object_key, a.mime FROM questionbank.question q
                        JOIN questionbank.question_version v ON v.question_id = q.id AND v.version_no = q.current_version_no
                        JOIN questionbank.question_asset a ON a.id::text IN (
                            SELECT jsonb_array_elements_text(v.snapshot -> 'attachmentIds'))
                        WHERE q.id = ?""", root);
                assertThat(assets).as(row.get("code") + " attachments").isNotEmpty()
                        .allSatisfy(asset -> assertThat((String) asset.get("mime")).startsWith("audio/"));
                assets.forEach(asset -> keys.add((String) asset.get("object_key")));
            }
        }
        assertThat(keys).hasSize(8 + 3 + 3).doesNotHaveDuplicates();
        for (String key : keys) {
            verify(storage, atLeastOnce()).exists(key);
            assertThat(storage.exists(key)).as(key).isTrue();
        }
    }
}
