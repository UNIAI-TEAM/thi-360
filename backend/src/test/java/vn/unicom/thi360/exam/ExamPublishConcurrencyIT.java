package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
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
    @MockitoSpyBean QuestionSourcePort sourceSpy;
    @Autowired TransactionTemplate transactions;
    @Autowired CatalogLookup catalog;
    @MockitoSpyBean CatalogLookup catalogSpy;

    @Test
    void simultaneousExamsSharingWitnessQuestionsDoNotDeadlockOrDuplicateUsage() throws Exception {
        UUID question = UUID.randomUUID();
        UUID otherQuestion = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        try {
            for (UUID id : List.of(question, otherQuestion)) {
                jdbc.update("INSERT INTO questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) VALUES (?, ?, 'SINGLE_CHOICE', 'READY', ?, 1, 1)", id, "CONC-" + id, SUBJECT);
                jdbc.update("INSERT INTO questionbank.question_version(question_id, version_no, snapshot) VALUES (?, 1, '{}'::jsonb)", id);
            }
            setup(first, question, otherQuestion);
            setup(second, otherQuestion, question);
            CountDownLatch start = new CountDownLatch(1);
            CompletableFuture<Integer> a = CompletableFuture.supplyAsync(() -> publish(first, start));
            CompletableFuture<Integer> b = CompletableFuture.supplyAsync(() -> publish(second, start));
            start.countDown();
            assertThat(a.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(b.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            for (UUID id : List.of(question, otherQuestion)) {
                for (UUID exam : List.of(first, second)) {
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM questionbank.question_usage_ledger WHERE question_id = ? AND version_no = 1 AND kind = 'PUBLISH_WITNESS' AND exam_id = ?", Long.class, id, exam)).isEqualTo(1);
                }
            }
        } finally {
            jdbc.update("DELETE FROM questionbank.question_usage_ledger WHERE question_id IN (?, ?) AND exam_id IN (?, ?)", question, otherQuestion, first, second);
            jdbc.update("DELETE FROM exam.exam_block_ref WHERE exam_id IN (?, ?)", first, second);
            jdbc.update("DELETE FROM exam.exam_version WHERE exam_id IN (?, ?)", first, second);
            jdbc.update("DELETE FROM exam.exam_check_run WHERE exam_id IN (?, ?)", first, second);
            jdbc.update("DELETE FROM exam.exam_structure WHERE exam_id IN (?, ?)", first, second);
            jdbc.update("DELETE FROM exam.exam WHERE id IN (?, ?)", first, second);
            jdbc.update("DELETE FROM questionbank.question_version WHERE question_id IN (?, ?)", question, otherQuestion);
            jdbc.update("DELETE FROM questionbank.question WHERE id IN (?, ?)", question, otherQuestion);
        }
    }

    @Test
    void deletedWitnessAfterAssessmentReturnsSourceChangedAndRollsBack() throws Exception {
        UUID question = UUID.randomUUID();
        UUID exam = UUID.randomUUID();
        CountDownLatch beforeLock = new CountDownLatch(1);
        CountDownLatch deleted = new CountDownLatch(1);
        try {
            jdbc.update("INSERT INTO questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) VALUES (?, ?, 'SINGLE_CHOICE', 'READY', ?, 1, 1)", question, "CONC-" + question, SUBJECT);
            jdbc.update("INSERT INTO questionbank.question_version(question_id, version_no, snapshot) VALUES (?, 1, '{}'::jsonb)", question);
            setup(exam, question, question);
            doAnswer(invocation -> {
                beforeLock.countDown();
                if (!deleted.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Deletion barrier timed out");
                return invocation.callRealMethod();
            }).when(sourceSpy).recordUsage(any(), any(), any());
            CompletableFuture<Void> request = CompletableFuture.runAsync(() -> {
                try {
                    mockMvc.perform(post("/api/v1/admin/exams/" + exam + "/publish")
                            .with(asUser(Permissions.EXAM_PUBLISH)).header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"pathIds\":[\"default\"],\"expectedRevision\":0}"))
                            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CHECK_REQUIRED"))
                            .andExpect(jsonPath("$.fieldErrors[0].code").value("SOURCE_CHANGED"));
                } catch (Exception e) { throw new IllegalStateException(e); }
            });
            assertThat(beforeLock.await(10, TimeUnit.SECONDS)).isTrue();
            transactions.executeWithoutResult(tx -> {
                jdbc.update("UPDATE questionbank.question SET deleted_at = now() WHERE id = ?", question);
                jdbc.update("INSERT INTO questionbank.question_source_revision(subject_item_id, revision) VALUES (?, 1) ON CONFLICT (subject_item_id) DO UPDATE SET revision = questionbank.question_source_revision.revision + 1", SUBJECT);
            });
            deleted.countDown();
            request.get(30, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_version WHERE exam_id = ?", Long.class, exam)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM questionbank.question_usage_ledger WHERE exam_id = ?", Long.class, exam)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM shared.outbox_event WHERE topic = 'audit' AND payload->>'action' = 'EXAM.PUBLISH' AND payload->>'targetId' = ?", Long.class, exam.toString())).isZero();
        } finally {
            deleted.countDown();
            reset(sourceSpy);
            jdbc.update("DELETE FROM exam.exam_check_run WHERE exam_id = ?", exam);
            jdbc.update("DELETE FROM exam.exam_structure WHERE exam_id = ?", exam);
            jdbc.update("DELETE FROM exam.exam WHERE id = ?", exam);
            jdbc.update("DELETE FROM questionbank.question_version WHERE question_id = ?", question);
            jdbc.update("DELETE FROM questionbank.question WHERE id = ?", question);
        }
    }

    @Test
    void catalogEditDuringMaterializationRejectsAndRollsBack() throws Exception {
        UUID question = UUID.randomUUID(), exam = UUID.randomUUID(), item = UUID.randomUUID(), version = UUID.randomUUID();
        UUID kind = jdbc.queryForObject("SELECT kind_item_id FROM exam.exam WHERE id = ?", UUID.class, SEEDED_EXAM);
        String code = "RACE" + item.toString().replace("-", "");
        CountDownLatch reading = new CountDownLatch(1), edited = new CountDownLatch(1);
        try {
            jdbc.update("INSERT INTO catalog.catalog_item(id,group_id,code,code_normalized,value,name_vi,path) SELECT ?,id,?,?,(SELECT max(value)+1 FROM catalog.catalog_item WHERE group_id=catalog_group.id),'Race',? FROM catalog.catalog_group WHERE code='SUBJECT_COMBINATION'",
                    item, code, code.toLowerCase(java.util.Locale.ROOT), "/" + item + "/");
            jdbc.update("INSERT INTO catalog.combination_version(id,item_id,version_no,exam_item_id) VALUES (?,?,1,?)", version, item, kind);
            jdbc.update("INSERT INTO catalog.combination_subject(combination_version_id,subject_item_id,position) VALUES (?,?,0)", version, SUBJECT);
            jdbc.update("INSERT INTO questionbank.question(id,code,type,status,subject_item_id,current_version_no,points) VALUES (?,?,'SINGLE_CHOICE','READY',?,1,1)", question, "RACE-" + question, SUBJECT);
            jdbc.update("INSERT INTO questionbank.question_version(question_id,version_no,snapshot) VALUES (?,1,'{}'::jsonb)", question);
            setup(exam, question, question);
            String path = "p:" + code;
            jdbc.update("UPDATE exam.exam_structure SET parts=?::jsonb WHERE exam_id=?", "{\"parts\":[{\"id\":\"p\",\"codeSource\":{}}]}", exam);
            jdbc.update("UPDATE exam.exam_check_run SET result=jsonb_set(result,'{paths,0,pathId}',to_jsonb(?::text)), witness=jsonb_set(witness,'{0,path,chosen}',jsonb_build_array(jsonb_build_object('optionId',?::text))) WHERE exam_id=?", path, path, exam);
            doAnswer(invocation -> {
                reading.countDown();
                if (!edited.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Catalog barrier timed out");
                return invocation.callRealMethod();
            }).when(catalogSpy).listCombinations(any());
            CompletableFuture<Void> request = CompletableFuture.runAsync(() -> {
                try {
                    mockMvc.perform(post("/api/v1/admin/exams/" + exam + "/publish")
                            .with(asUser(Permissions.EXAM_PUBLISH)).header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"pathIds\":[\"" + path + "\"],\"expectedRevision\":0}"))
                            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CHECK_REQUIRED"))
                            .andExpect(jsonPath("$.fieldErrors[0].code").value("CONFIG_CHANGED"));
                } catch (Exception e) { throw new IllegalStateException(e); }
            });
            assertThat(reading.await(10, TimeUnit.SECONDS)).isTrue();
            transactions.executeWithoutResult(tx -> jdbc.update("UPDATE catalog.combination_subject SET subject_item_id=? WHERE combination_version_id=? AND position=0",
                    UUID.fromString("00000000-0000-7000-8200-000000000208"), version));
            edited.countDown();
            request.get(30, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_version WHERE exam_id=?", Long.class, exam)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM questionbank.question_usage_ledger WHERE exam_id=?", Long.class, exam)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM shared.outbox_event WHERE topic='audit' AND payload->>'action'='EXAM.PUBLISH' AND payload->>'targetId'=?", Long.class, exam.toString())).isZero();
        } finally {
            edited.countDown();
            reset(catalogSpy);
            jdbc.update("DELETE FROM exam.exam_check_run WHERE exam_id=?", exam);
            jdbc.update("DELETE FROM exam.exam_structure WHERE exam_id=?", exam);
            jdbc.update("DELETE FROM exam.exam WHERE id=?", exam);
            jdbc.update("DELETE FROM questionbank.question_version WHERE question_id=?", question);
            jdbc.update("DELETE FROM questionbank.question WHERE id=?", question);
            jdbc.update("DELETE FROM catalog.combination_subject WHERE combination_version_id=?", version);
            jdbc.update("DELETE FROM catalog.combination_version WHERE id=?", version);
            jdbc.update("DELETE FROM catalog.catalog_item WHERE id=?", item);
        }
    }

    private void setup(UUID id, UUID question, UUID otherQuestion) {
        UUID kind = jdbc.queryForObject("SELECT kind_item_id FROM exam.exam WHERE id = ?", UUID.class, SEEDED_EXAM);
        jdbc.update("INSERT INTO exam.exam (id, code, code_normalized, name, kind_item_id, scope, timing, has_unpublished_changes) VALUES (?, ?, ?, 'Concurrent', ?, 'FULL', 'SHARED', true)", id, id.toString(), id.toString(), kind);
        jdbc.update("INSERT INTO exam.exam_structure (exam_id, parts, structure_hash) VALUES (?, '{\"parts\":[]}'::jsonb, 'concurrent')", id);
        ExamListingFixture.fill(jdbc, id);
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
        picks.addObject().put("questionId", otherQuestion.toString()).put("versionNo", 1);
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
