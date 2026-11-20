package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * Review R2-1 (UNI-2758): an HSA Science exam WITHOUT {@code mapidMode} and with a grade-filtered legacy topic quota
 * keeps generating when the subject that carries the quota is the one that gets 16 questions. The trim may drop one
 * contributor of a legacy quota when nothing else can go (spec H3); only compiled MAPID rules are hard minimums.
 *
 * <p>The expected dropped question per seed is what origin/develop 12ec91a8 generates for this very stock and seed
 * (recorded by running this class on that base).
 */
@Transactional
class ExamLegacyScienceTrimIT extends AbstractIntegrationTest {
    private static final UUID SUBJECT_GROUP = UUID.fromString("00000000-0000-7000-8100-000000000002");
    private static final UUID LEAF = UUID.fromString("00000000-0000-7000-8200-000000001201");
    private static final String G12 = "00000000-0000-7000-8200-000000000303";
    private static final int[] SEEDS = lowSubjectSeeds(3);
    /** seed index to the label of the question that the base drops from the first subject (see class comment). */
    private static final String[] BASE_DROPPED = {"ly-6", "ly-16", "ly-11"};

    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    void theLegacyGradeFilteredQuotaStillGeneratesWhenItsSubjectGetsSixteen() throws Exception {
        // Fixed codes and ids: the candidate order, hence the dropped question per seed, must not vary between runs.
        String first = subject("LEGSCI_FIRST");
        String second = subject("LEGSCI_SECOND");
        String third = subject("LEGSCI_THIRD");
        Map<String, String> labels = new HashMap<>();
        for (int i = 0; i < 17; i++) {
            labels.put(question(first, "ly-" + i, true).toString(), "ly-" + i);
            question(second, "hoa-" + i, false);
            question(third, "sinh-" + i, false);
        }
        UUID exam = exam(first, second, third);
        String codes = "[\"" + first + "\",\"" + second + "\",\"" + third + "\"]";
        List<String> droppedBySeed = new ArrayList<>();
        for (int i = 0; i < SEEDS.length; i++) {
            JsonNode response = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/{id}/preview", exam)
                    .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"target\":{\"kind\":\"PATH\",\"pathId\":\"science\",\"subjectCodes\":" + codes
                            + "},\"seed\":" + SEEDS[i] + "}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(response.path("status").asText()).as("seed %d: %s", SEEDS[i], response.path("issues"))
                    .isEqualTo("GENERATED");
            droppedBySeed.add(labels.get(response.path("droppedQuestions").get(0).path("questionId").asText()));
        }
        System.out.println("PICKED seeds=" + java.util.Arrays.toString(SEEDS) + " dropped=" + droppedBySeed);
        assertThat(droppedBySeed).isEqualTo(List.of(BASE_DROPPED));
    }

    /** The first {@code n} seeds for which the allocator gives the first picked subject the 16-question paper. */
    private static int[] lowSubjectSeeds(int n) {
        int[] seeds = new int[n];
        int found = 0;
        for (int seed = 1; found < n; seed++) {
            if (new Random(seed).nextInt(3) == 0) seeds[found++] = seed;
        }
        return seeds;
    }

    private String subject(String code) {
        UUID id = UUID.nameUUIDFromBytes(code.getBytes());
        jdbc.update("""
                insert into catalog.catalog_item (id, group_id, code, code_normalized, value,
                    name_vi, name_en, display_order, status, is_system, depth, path)
                values (?, ?, ?, ?, (select coalesce(max(value),0)+1 from catalog.catalog_item where group_id=?),
                    'Legacy subject', 'Legacy subject', 0, 1, false, 1, ?)
                """, id, SUBJECT_GROUP, code, code.toLowerCase(Locale.ROOT), SUBJECT_GROUP, "/" + id + "/");
        return code;
    }

    private UUID question(String subjectCode, String label, boolean legacyTopic) {
        UUID id = UUID.nameUUIDFromBytes((subjectCode + label).getBytes());
        UUID subject = jdbc.queryForObject("select id from catalog.catalog_item where group_id=? and code=?",
                UUID.class, SUBJECT_GROUP, subjectCode);
        jdbc.update("insert into questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) "
                + "values (?,?,'SINGLE_CHOICE','READY',?,1,1)", id, "L-" + id, subject);
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,cast(? as jsonb))",
                id, "{\"content\":\"" + label + "\",\"points\":1,\"body\":{\"type\":\"SINGLE_CHOICE\","
                        + "\"options\":[{\"id\":\"a\",\"content\":\"A\",\"correct\":true},{\"id\":\"b\",\"content\":\"B\"}]}}");
        if (legacyTopic) {
            jdbc.update("update questionbank.question set grade_item_id=cast(? as uuid) where id=?", G12, id);
            jdbc.update("insert into questionbank.question_knowledge(question_id, knowledge_item_id, is_primary) "
                    + "values (?,?,true)", id, LEAF);
        }
        return id;
    }

    private UUID exam(String first, String second, String third) {
        UUID id = UUID.randomUUID();
        String parts = """
                [{"id":"science-part","order":1,"name":"Science","questionCount":50,
                  "participation":{"mode":"CHOICE","choose":1},"options":[
                    {"id":"science","kind":"SUBJECT_CHOICE","chooseSubjects":3,"countsByPick":[17,17,16],"sections":[
                      {"id":"first","subjectCode":"%s","choiceGroup":"NATURAL",
                       "topicQuotas":[{"id":"legacy-a","nodeIds":["%s"],"count":17,"gradeCodes":["G12"]}],
                       "blocks":[{"id":"first-g","questionType":"SINGLE_CHOICE","questionCount":17}]},
                      {"id":"second","subjectCode":"%s","choiceGroup":"NATURAL",
                       "blocks":[{"id":"second-g","questionType":"SINGLE_CHOICE","questionCount":17}]},
                      {"id":"third","subjectCode":"%s","choiceGroup":"NATURAL",
                       "blocks":[{"id":"third-g","questionType":"SINGLE_CHOICE","questionCount":17}]}]}]}]
                """.formatted(first, LEAF, second, third);
        jdbc.update("insert into exam.exam (id, code, code_normalized, name, scope, timing, duration_minutes, kind_item_id) "
                + "values (?, ?, ?, 'Legacy science', 'FULL', 'SHARED', 60, (select id from catalog.catalog_item "
                + "where group_id = '00000000-0000-7000-8100-000000000001' and code_normalized = 'hsa'))",
                id, id.toString(), id.toString());
        jdbc.update("insert into exam.exam_structure (exam_id, parts, structure_hash) values (?, cast(? as jsonb), 'legacy')",
                id, "{\"parts\":" + parts + "}");
        return id;
    }
}
