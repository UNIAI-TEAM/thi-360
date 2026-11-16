package vn.unicom.thi360.questionbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.questionbank.spi.QuestionUsageKind;

@Transactional
class QuestionSourcePortIT extends AbstractIntegrationTest {
    @Autowired QuestionSourcePort source;
    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogLookup catalog;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager transactionManager;

    private QuestionSourcePort.Filter filter(UUID subject, List<List<UUID>> scope) {
        return new QuestionSourcePort.Filter(subject, scope, QuestionSourcePort.Match.ANY,
                List.of(QuestionType.SINGLE_CHOICE), null, null, null, List.of(), List.of(), null);
    }

    private UUID question(UUID subject, String status, int currentVersion, int workingVersion) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into questionbank.question(id,code,type,status,subject_item_id,current_version_no,working_version_no)
                values (?,?, 'SINGLE_CHOICE', ?,?,?,?)
                """, id, "TEST-" + id, status, subject, currentVersion, workingVersion);
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,cast(? as jsonb))",
                id, "{\"marker\":\"serving\"}");
        if (workingVersion > 1) jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,2,cast(? as jsonb))",
                id, "{\"marker\":\"draft\"}");
        return id;
    }

    @Test
    void inheritedScope() {
        UUID subject = UUID.randomUUID();
        UUID leaf = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID included = question(subject, "READY", 1, 2);
        UUID excluded = question(subject, "READY", 1, 1);
        jdbc.update("insert into questionbank.question_knowledge values (?,?,true)", included, leaf);
        jdbc.update("insert into questionbank.question_knowledge values (?,?,true)", excluded, other);
        var filter = filter(subject, List.of(List.of(UUID.randomUUID(), leaf)));
        assertThat(source.count(filter).total()).isEqualTo(1);
        assertThat(source.findCandidates(filter, 10)).extracting(QuestionSourcePort.Candidate::questionId).containsExactly(included);
        var snapshots = source.loadVersions(List.of(new QuestionSourcePort.VersionKey(included, 1)));
        assertThat(snapshots).singleElement().satisfies(v -> assertThat(v.snapshot().get("marker").asText()).isEqualTo("serving"));
    }

    @Test
    void subjectCountDedup() {
        UUID subject = UUID.randomUUID();
        UUID ready = question(subject, "READY", 1, 2);
        question(subject, "DRAFT", 1, 1);
        question(subject, "RETIRED", 1, 1);
        var filter = filter(subject, null);
        var counts = source.countUnion(List.of(filter, filter));
        assertThat(counts.total()).isEqualTo(1);
        assertThat(counts.byAnswerType()).containsExactly(new QuestionSourcePort.AnswerTypeCount(QuestionType.SINGLE_CHOICE, 1));
        assertThat(source.findCandidates(filter, 10)).singleElement().satisfies(c -> {
            assertThat(c.questionId()).isEqualTo(ready);
            assertThat(c.versionNo()).isEqualTo(1);
        });
    }

    @Test
    void recordUsageIsIdempotent() {
        UUID q = question(UUID.randomUUID(), "READY", 1, 1);
        UUID exam = UUID.randomUUID();
        var key = new QuestionSourcePort.VersionKey(q, 1);
        source.recordUsage(exam, QuestionUsageKind.PREVIEW, List.of(key, key));
        source.recordUsage(exam, QuestionUsageKind.PREVIEW, List.of(key));
        assertThat(jdbc.queryForObject("select count(*) from questionbank.question_usage_ledger where question_id=? and exam_id=?", Integer.class, q, exam)).isEqualTo(1);
    }

    @Test
    void countEndpointsRequireExamView() throws Exception {
        String query = "{\"subjectCode\":\"TOAN\",\"questionType\":null,\"answerTypes\":[\"SINGLE_CHOICE\"]}";
        for (String suffix : List.of("count", "count-subject")) {
            String body = suffix.equals("count") ? query : "{\"subjectCode\":\"TOAN\",\"groups\":[" + query + "]}";
            String path = "/api/v1/admin/exams/available-questions/" + suffix;
            mockMvc.perform(post(path).header("Origin", "http://localhost:3001")
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
            mockMvc.perform(post(path).with(asUser(Permissions.QUESTION_VIEW))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
            mockMvc.perform(post(path).with(asUser(Permissions.EXAM_VIEW))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").isNumber()).andExpect(jsonPath("$.byAnswerType").isArray());
        }
    }

    @Test
    void countsCompleteSetsAndClozeUnits() {
        UUID subject = UUID.randomUUID();
        UUID set = UUID.randomUUID();
        jdbc.update("insert into questionbank.question(id,code,type,status,subject_item_id,current_version_no) values (?,?, 'QUESTION_SET','READY',?,1)", set, "SET-" + set, subject);
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,'{}'::jsonb)", set);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        jdbc.update("insert into questionbank.question(id,code,type,status,parent_id,position,points) values (?,?, 'SINGLE_CHOICE','READY',?,0,1)", first, "C-" + first, set);
        jdbc.update("insert into questionbank.question(id,code,type,status,parent_id,position,points) values (?,?, 'SHORT_ANSWER','READY',?,1,2)", second, "C-" + second, set);
        var setFilter = new QuestionSourcePort.Filter(subject, null, QuestionSourcePort.Match.ANY, List.of(),
                QuestionType.QUESTION_SET, null, null, List.of(), List.of(), null);
        assertThat(source.count(setFilter).total()).isEqualTo(2);
        assertThat(source.count(setFilter).byAnswerType()).containsExactlyInAnyOrder(
                new QuestionSourcePort.AnswerTypeCount(QuestionType.SINGLE_CHOICE, 1),
                new QuestionSourcePort.AnswerTypeCount(QuestionType.SHORT_ANSWER, 1));
        assertThat(source.findCandidates(setFilter, 1)).singleElement().satisfies(c -> {
            assertThat(c.setId()).isEqualTo(set);
            assertThat(c.units()).extracting(QuestionSourcePort.Unit::questionId).containsExactly(first, second);
        });
        var pointsFilter = new QuestionSourcePort.Filter(subject, null, QuestionSourcePort.Match.ANY, List.of(),
                QuestionType.QUESTION_SET, null, null, List.of(), List.of(), BigDecimal.ONE);
        assertThat(source.count(pointsFilter).total()).isZero();

        UUID cloze = UUID.randomUUID();
        jdbc.update("insert into questionbank.question(id,code,type,status,subject_item_id,current_version_no) values (?,?, 'CLOZE','READY',?,1)", cloze, "CLOZE-" + cloze, subject);
        jdbc.update("insert into questionbank.question_version(question_id,version_no,snapshot) values (?,1,cast(? as jsonb))",
                cloze, "{\"body\":{\"blanks\":[{\"id\":\"a\",\"points\":1},{\"id\":\"b\",\"points\":2}]}}");
        var clozeFilter = new QuestionSourcePort.Filter(subject, null, QuestionSourcePort.Match.ANY, List.of(),
                QuestionType.CLOZE, null, null, List.of(), List.of(), null);
        assertThat(source.count(clozeFilter).total()).isEqualTo(2);
        assertThat(source.findCandidates(clozeFilter, 1)).singleElement().satisfies(c ->
                assertThat(c.units()).extracting(QuestionSourcePort.Unit::reference).containsExactly("a", "b"));
    }

    @Test
    void endpointExpandsDescendants() throws Exception {
        String body = "{\"subjectCode\":\"TOAN\",\"questionType\":\"SINGLE_CHOICE\",\"knowledgeScope\":{\"nodeIds\":[\""
                + QuestionTestSupport.KNOWLEDGE_CHAPTER_ID + "\"],\"match\":\"ANY\"}}";
        String path = "/api/v1/admin/exams/available-questions/count";
        int before = mapper.readTree(mockMvc.perform(post(path).with(asUser(Permissions.EXAM_VIEW))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("total").asInt();
        UUID id = question(QuestionTestSupport.SUBJECT_TOAN_ID, "READY", 1, 1);
        jdbc.update("insert into questionbank.question_knowledge values (?,?,true)", id, QuestionTestSupport.KNOWLEDGE_LEAF_ID);
        mockMvc.perform(post(path).with(asUser(Permissions.EXAM_VIEW)).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(before + 1));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void recordUsageWaitsForQuestionRowLock() throws Exception {
        UUID id = question(UUID.randomUUID(), "READY", 1, 1);
        UUID exam = UUID.randomUUID();
        var key = new QuestionSourcePort.VersionKey(id, 1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbc.queryForObject("select id from questionbank.question where id=? for update", UUID.class, id);
                var blocked = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(inner -> {
                    jdbc.execute("set local lock_timeout='250ms'");
                    source.recordUsage(exam, QuestionUsageKind.PREVIEW, List.of(key));
                }));
                assertThatThrownBy(() -> blocked.get(5, TimeUnit.SECONDS)).hasStackTraceContaining("lock timeout");
            });
            source.recordUsage(exam, QuestionUsageKind.PREVIEW, List.of(key));
            assertThat(jdbc.queryForObject("select count(*) from questionbank.question_usage_ledger where question_id=?", Integer.class, id)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            jdbc.update("delete from questionbank.question_usage_ledger where question_id=?", id);
            jdbc.update("delete from questionbank.question_version where question_id=?", id);
            jdbc.update("delete from questionbank.question where id=?", id);
        }
    }
}
