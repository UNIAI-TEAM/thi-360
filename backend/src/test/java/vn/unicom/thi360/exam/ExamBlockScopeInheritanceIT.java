package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * FIX-QA F3: a block scope without MAPIDs (the block editor writes {@code nodeIds: []} next to its
 * competency codes) keeps inheriting the section's MAPIDs in the check, the usage lookup and the draw.
 */
@Transactional
class ExamBlockScopeInheritanceIT extends AbstractIntegrationTest {
    private static final UUID SUBJECT_GROUP = UUID.fromString("00000000-0000-7000-8100-000000000002");
    private static final UUID COMPETENCY = UUID.fromString("00000000-0000-7000-8400-000000000903");
    private static final String COMPETENCY_CODE = "VACT-TOAN";
    private static final int INSIDE = 3;
    private static final int OUTSIDE = 5;

    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired CatalogLookup catalog;

    private record Subject(UUID id, String code) { }

    private record Pool(Subject subject, UUID topic, Set<String> inside, Set<String> outside) { }

    @Test
    void competencyOnlyBlockKeepsTheSectionTopicInTheCheck() throws Exception {
        Pool pool = pool(true);
        var result = check(pool, "{\"nodeIds\":[],\"competencyCodes\":[\"" + COMPETENCY_CODE + "\"]}", INSIDE + 1);
        assertThat(availableOfShortage(result))
                .as("only the %d questions of the section topic are available, not all %d", INSIDE, INSIDE + OUTSIDE)
                .isEqualTo(INSIDE);
    }

    @Test
    void competencyOnlyBlockDrawsOnlyQuestionsOfTheSectionTopic() throws Exception {
        Pool pool = pool(true);
        UUID exam = exam(pool, "{\"nodeIds\":[],\"competencyCodes\":[\"" + COMPETENCY_CODE + "\"]}", INSIDE);
        assertThat(checkResult(exam).path("status").asText()).isEqualTo("VALID");
        for (int seed = 1; seed <= 8; seed++) {
            Set<String> drawn = drawn(exam, seed);
            assertThat(drawn).as("seed %d", seed).hasSize(INSIDE).isSubsetOf(pool.inside());
        }
    }

    @Test
    void emptyScopeOfBothKindsKeepsTheSectionTopic() throws Exception {
        Pool pool = pool(false);
        var result = check(pool, "{\"nodeIds\":[],\"competencyCodes\":[]}", INSIDE + 1);
        assertThat(availableOfShortage(result)).isEqualTo(INSIDE);
        UUID exam = exam(pool, "{\"nodeIds\":[],\"competencyCodes\":[]}", INSIDE);
        assertThat(checkResult(exam).path("status").asText()).isEqualTo("VALID");
        for (int seed = 1; seed <= 8; seed++) {
            assertThat(drawn(exam, seed)).as("seed %d", seed).hasSize(INSIDE).isSubsetOf(pool.inside());
        }
    }

    // ------------------------------------------------------------------ fixtures

    /** Questions of one subject: INSIDE under the topic node, OUTSIDE without any MAPID; all share the competency. */
    private Pool pool(boolean withCompetency) {
        Subject subject = subject();
        List<CatalogItemView> knowledge = catalog.listActiveGroupItems("KNOWLEDGE");
        UUID topic = knowledge.stream().filter(a -> knowledge.stream().anyMatch(b ->
                !b.path().startsWith(a.path()) && !a.path().startsWith(b.path()))).findFirst().orElseThrow().id();
        Set<String> inside = new HashSet<>();
        Set<String> outside = new HashSet<>();
        for (int i = 0; i < INSIDE; i++) {
            UUID id = question(subject.id(), withCompetency);
            jdbc.update("INSERT INTO questionbank.question_knowledge(question_id,knowledge_item_id,is_primary) VALUES (?,?,true)",
                    id, topic);
            inside.add(id.toString());
        }
        for (int i = 0; i < OUTSIDE; i++) {
            outside.add(question(subject.id(), withCompetency).toString());
        }
        return new Pool(subject, topic, inside, outside);
    }

    private Subject subject() {
        UUID id = UUID.randomUUID();
        String code = "SCOPE_" + id.toString().replace("-", "");
        jdbc.update("INSERT INTO catalog.catalog_item(id,group_id,code,code_normalized,value,name_vi,name_en,display_order,status,is_system,depth,path) "
                        + "VALUES (?,?,?,?,(SELECT coalesce(max(value),0)+1 FROM catalog.catalog_item WHERE group_id=?),'Scope','Scope',0,1,false,1,?)",
                id, SUBJECT_GROUP, code, code.toLowerCase(Locale.ROOT), SUBJECT_GROUP, "/" + id + "/");
        return new Subject(id, code);
    }

    private UUID question(UUID subject, boolean withCompetency) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO questionbank.question(id,code,type,status,subject_item_id,competency_item_id,current_version_no,points) "
                        + "VALUES (?,?,'SINGLE_CHOICE','READY',?,?,1,1)",
                id, "Q-" + id, subject, withCompetency ? COMPETENCY : null);
        jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) VALUES (?,1,?::jsonb)", id,
                "{\"content\":\"q\",\"points\":1,\"body\":{\"type\":\"SINGLE_CHOICE\","
                        + "\"options\":[{\"id\":\"a\",\"content\":\"A\",\"correct\":true},{\"id\":\"b\",\"content\":\"B\",\"correct\":false}]}}");
        return id;
    }

    private UUID exam(Pool pool, String blockScope, int count) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam(id,code,code_normalized,name,scope,timing,question_count,duration_minutes) VALUES (?,?,?,'Scope','FULL','SHARED',?,60)",
                id, id.toString(), id.toString(), count);
        String document = """
                {"parts":[{"id":"p","questionCount":%d,"sections":[
                  {"id":"s","subjectCode":"%s","questionCount":%d,"knowledgeScope":{"nodeIds":["%s"],"competencyCodes":[]},
                   "blocks":[{"id":"b","questionType":"SINGLE_CHOICE","questionCount":%d,"knowledgeScope":%s}]}]}]}
                """.formatted(count, pool.subject().code(), count, pool.topic(), count, blockScope);
        jdbc.update("INSERT INTO exam.exam_structure(exam_id,parts,structure_hash) VALUES (?,?::jsonb,'scope')", id, document);
        jdbc.update("INSERT INTO exam.exam_block_ref(id,exam_id,block_id,part_id,section_id,subject_item_id) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), id, "b", "p", "s", pool.subject().id());
        return id;
    }

    private JsonNode check(Pool pool, String blockScope, int count) throws Exception {
        return checkResult(exam(pool, blockScope, count));
    }

    private JsonNode checkResult(UUID exam) throws Exception {
        return mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/" + exam + "/check")
                        .with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static long availableOfShortage(JsonNode result) {
        List<JsonNode> issues = new ArrayList<>();
        result.path("groups").findValues("issues").forEach(rows -> rows.forEach(issues::add));
        return issues.stream()
                .filter(issue -> Set.of("CELL_SHORTAGE", "SOURCE_SHORTAGE").contains(issue.path("code").asText()))
                .findFirst().orElseThrow(() -> new AssertionError("no shortage issue in " + issues))
                .path("available").asLong();
    }

    private Set<String> drawn(UUID exam, int seed) throws Exception {
        var paper = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/" + exam + "/preview")
                        .with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":{\"kind\":\"EXAM\"},\"seed\":" + seed + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(paper.path("status").asText()).as("preview %s", paper).isEqualTo("GENERATED");
        return new HashSet<>(paper.path("paper").findValuesAsText("questionId"));
    }
}
