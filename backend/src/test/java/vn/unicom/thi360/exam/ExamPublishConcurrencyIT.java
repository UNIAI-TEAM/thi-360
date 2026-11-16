package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.application.ExamCheckFingerprint;
import vn.unicom.thi360.exam.application.ExamCheckResultStore;
import vn.unicom.thi360.exam.infrastructure.ExamKindProfileRepository;
import vn.unicom.thi360.exam.infrastructure.ExamRepository;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;

class ExamPublishConcurrencyIT extends AbstractIntegrationTest {
    private static final UUID SEEDED_EXAM = UUID.fromString("00000000-0000-7000-8700-000000000001");
    private static final UUID SUBJECT = UUID.fromString("00000000-0000-7000-8200-000000000201");
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired ExamRepository exams;
    @Autowired ExamKindProfileRepository profiles;
    @Autowired ExamCheckResultStore results;
    @Autowired QuestionSourcePort source;
    @Autowired CatalogLookup catalog;

    @Test
    void simultaneousExamsSharingWitnessQuestionDoNotDeadlockOrDuplicateUsage() throws Exception {
        UUID question = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) VALUES (?, ?, 'SINGLE_CHOICE', 'READY', ?, 1, 1)", question, "CONC-" + question, SUBJECT);
            jdbc.update("INSERT INTO questionbank.question_version(question_id, version_no, snapshot) VALUES (?, 1, '{}'::jsonb)", question);
            setup(first, question);
            setup(second, question);
            CountDownLatch start = new CountDownLatch(1);
            CompletableFuture<Integer> a = CompletableFuture.supplyAsync(() -> publish(first, start));
            CompletableFuture<Integer> b = CompletableFuture.supplyAsync(() -> publish(second, start));
            start.countDown();
            assertThat(a.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(b.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM questionbank.question_usage_ledger WHERE question_id = ? AND kind = 'PUBLISH_WITNESS' AND exam_id IN (?, ?)", Long.class, question, first, second)).isEqualTo(2);
        } finally {
            jdbc.update("DELETE FROM questionbank.question_usage_ledger WHERE question_id = ? AND exam_id IN (?, ?)", question, first, second);
            jdbc.update("DELETE FROM exam.exam_block_ref WHERE exam_id IN (?, ?)", first, second);
            jdbc.update("DELETE FROM exam.exam_version WHERE exam_id IN (?, ?)", first, second);
            jdbc.update("DELETE FROM exam.exam_check_run WHERE exam_id IN (?, ?)", first, second);
            jdbc.update("DELETE FROM exam.exam_structure WHERE exam_id IN (?, ?)", first, second);
            jdbc.update("DELETE FROM exam.exam WHERE id IN (?, ?)", first, second);
            jdbc.update("DELETE FROM questionbank.question_version WHERE question_id = ?", question);
            jdbc.update("DELETE FROM questionbank.question WHERE id = ?", question);
        }
    }

    private void setup(UUID id, UUID question) {
        UUID kind = jdbc.queryForObject("SELECT kind_item_id FROM exam.exam WHERE id = ?", UUID.class, SEEDED_EXAM);
        jdbc.update("INSERT INTO exam.exam (id, code, code_normalized, name, kind_item_id, scope, timing, has_unpublished_changes) VALUES (?, ?, ?, 'Concurrent', ?, 'FULL', 'SHARED', true)", id, id.toString(), id.toString(), kind);
        jdbc.update("INSERT INTO exam.exam_structure (exam_id, parts, structure_hash) VALUES (?, '{\"parts\":[]}'::jsonb, 'concurrent')", id);
        var exam = exams.findById(id).orElseThrow();
        var profile = profiles.findById(kind).orElseThrow();
        var input = new ExamCheckResultStore.Input(id, exam.getRevision(), "concurrent", mapper.valueToTree(ExamCheckFingerprint.Config.from(exam)), profile.getRevision(), catalog.revision(), "test", mapper.valueToTree(source.sourceRevisions(List.of(SUBJECT))));
        var result = mapper.createObjectNode().put("status", "VALID");
        result.putArray("paths").addObject().put("pathId", "default").put("result", "VALID");
        var witness = mapper.createArrayNode();
        var path = witness.addObject();
        path.putObject("path").putArray("chosen");
        var picks = path.putArray("witness").addObject().putArray("groups").addObject().putArray("picks");
        picks.addObject().put("questionId", question.toString()).put("versionNo", 1);
        picks.addObject().put("questionId", question.toString()).put("versionNo", 1);
        assertThat(results.save(input, "VALID", 0, Instant.now(), 1, result, witness)).isTrue();
    }

    private int publish(UUID exam, CountDownLatch start) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start barrier timed out");
            return mockMvc.perform(post("/api/v1/admin/exams/" + exam + "/publish")
                    .with(asUser(Permissions.EXAM_PUBLISH)).header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"pathIds\":[\"default\"],\"expectedRevision\":0}"))
                    .andReturn().getResponse().getStatus();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
