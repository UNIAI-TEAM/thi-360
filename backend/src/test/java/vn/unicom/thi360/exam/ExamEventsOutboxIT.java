package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.application.ExamCheckFingerprint;
import vn.unicom.thi360.exam.application.ExamCheckResultStore;
import vn.unicom.thi360.exam.infrastructure.ExamKindProfileRepository;
import vn.unicom.thi360.exam.infrastructure.ExamRepository;
import vn.unicom.thi360.exam.spi.ExamEventTopics;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;

/**
 * EX-1 AC-18 to AC-20. The events are written in the transaction of the command, so the test commits (no test
 * transaction) and removes its rows afterwards.
 */
class ExamEventsOutboxIT extends AbstractIntegrationTest {
    private static final UUID SUBJECT = UUID.fromString("00000000-0000-7000-8200-000000000201");
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired ExamRepository exams;
    @Autowired ExamKindProfileRepository profiles;
    @Autowired ExamCheckResultStore results;
    @Autowired QuestionSourcePort source;
    @Autowired CatalogLookup catalog;
    @MockitoSpyBean AuditRecorder audit;
    private UUID exam;
    private UUID question;

    @BeforeEach
    void createPublishableExam() {
        exam = UUID.randomUUID();
        question = UUID.randomUUID();
        UUID kind = jdbc.queryForObject("SELECT kind_item_id FROM exam.exam WHERE id = '00000000-0000-7000-8700-000000000001'", UUID.class);
        jdbc.update("INSERT INTO questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) "
                + "VALUES (?, ?, 'SINGLE_CHOICE', 'READY', ?, 1, 1)", question, "EVT-" + question, SUBJECT);
        jdbc.update("INSERT INTO questionbank.question_version(question_id, version_no, snapshot) VALUES (?, 1, '{}'::jsonb)", question);
        jdbc.update("INSERT INTO exam.exam (id, code, code_normalized, name, kind_item_id, scope, timing, has_unpublished_changes) "
                + "VALUES (?, ?, ?, 'Events', ?, 'FULL', 'SHARED', true)", exam, exam.toString(), exam.toString(), kind);
        jdbc.update("INSERT INTO exam.exam_structure (exam_id, parts, structure_hash) VALUES (?, '{\"parts\":[]}'::jsonb, 'events')", exam);
        ExamListingFixture.fill(jdbc, exam);
        var entity = exams.findById(exam).orElseThrow();
        var profile = profiles.findById(kind).orElseThrow();
        var input = new ExamCheckResultStore.Input(exam, entity.getRevision(), "events",
                mapper.valueToTree(ExamCheckFingerprint.Config.from(entity)), profile.getRevision(), catalog.revision(),
                "test", mapper.valueToTree(source.sourceRevisions(List.of(SUBJECT))));
        var result = mapper.createObjectNode().put("status", "VALID");
        result.putArray("paths").addObject().put("pathId", "default").put("result", "VALID");
        var witness = mapper.createArrayNode();
        var path = witness.addObject();
        path.putObject("path").putArray("chosen");
        path.putArray("witness").addObject().putArray("groups").addObject().putArray("picks")
                .addObject().put("questionId", question.toString()).put("versionNo", 1);
        assertThat(results.save(input, "VALID", 0, Instant.now(), 1, result, witness)).isTrue();
    }

    @AfterEach
    void cleanUp() {
        reset(audit);
        for (String table : List.of("questionbank.question_usage_ledger", "exam.exam_block_ref", "exam.exam_version",
                "exam.exam_check_run", "exam.exam_structure", "exam.exam_listing")) {
            jdbc.update("DELETE FROM " + table + " WHERE exam_id = ?", exam);
        }
        jdbc.update("DELETE FROM shared.outbox_event WHERE payload->>'examId' = ? OR payload->>'targetId' = ?", exam.toString(), exam.toString());
        jdbc.update("DELETE FROM exam.exam WHERE id = ?", exam);
        jdbc.update("DELETE FROM questionbank.question_version WHERE question_id = ?", question);
        jdbc.update("DELETE FROM questionbank.question WHERE id = ?", question);
    }

    /** The idempotency scope includes the user, so every call of one test is made by the same publisher. */
    private final UUID publisher = UUID.randomUUID();

    private ResultActions publish(String key, long revision) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/exams/" + exam + "/publish").with(asUser(publisher, "publisher", Permissions.EXAM_PUBLISH))
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"pathIds\":[\"default\"],\"expectedRevision\":" + revision + "}"));
    }

    private ResultActions discontinue(String key, long revision) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/exams/" + exam + "/discontinue").with(asUser(publisher, "publisher", Permissions.EXAM_PUBLISH))
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":" + revision + "}"));
    }

    private List<JsonNode> events(String topic) {
        List<JsonNode> rows = new ArrayList<>();
        for (String payload : jdbc.queryForList("SELECT payload::text FROM shared.outbox_event WHERE topic = ? AND payload->>'examId' = ?",
                String.class, topic, exam.toString())) {
            try {
                rows.add(mapper.readTree(payload));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return rows;
    }

    private long revision() {
        return jdbc.queryForObject("SELECT revision FROM exam.exam WHERE id = ?", Long.class, exam);
    }

    @Test
    void publishEmitsOneEvent() throws Exception {
        publish(UUID.randomUUID().toString(), revision()).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        List<JsonNode> published = events(ExamEventTopics.PUBLISHED);
        assertThat(published).hasSize(1);
        var body = published.getFirst();
        assertThat(body.size()).as("the body is the ids only: %s", body).isEqualTo(2);
        assertThat(body.path("examId").asText()).isEqualTo(exam.toString());
        assertThat(body.path("versionNo").asInt()).isEqualTo(1);
        assertThat(events(ExamEventTopics.DISCONTINUED)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shared.outbox_event WHERE topic = ? AND processed_at IS NULL "
                + "AND payload->>'examId' = ?", Long.class, ExamEventTopics.PUBLISHED, exam.toString())).as("left for the relay").isEqualTo(1);
    }

    @Test
    void replayEmitsNoEvent() throws Exception {
        String key = UUID.randomUUID().toString();
        long revision = revision();
        publish(key, revision).andExpect(status().isOk());
        publish(key, revision).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        assertThat(events(ExamEventTopics.PUBLISHED)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_version WHERE exam_id = ?", Long.class, exam)).isEqualTo(1);
    }

    @Test
    void rollbackEmitsNoEvent() throws Exception {
        doAnswer(invocation -> {
            AuditEvent event = invocation.getArgument(0);
            if ("EXAM.PUBLISH".equals(event.action())) throw new IllegalStateException("audit store is down");
            return invocation.callRealMethod();
        }).when(audit).record(any(AuditEvent.class));
        publish(UUID.randomUUID().toString(), revision()).andExpect(status().isInternalServerError());
        assertThat(events(ExamEventTopics.PUBLISHED)).as("the failed command wrote no event").isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_version WHERE exam_id = ?", Long.class, exam)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM exam.exam WHERE id = ?", String.class, exam)).isEqualTo("DRAFT");
    }

    @Test
    void discontinueEmitsOneEvent() throws Exception {
        publish(UUID.randomUUID().toString(), revision()).andExpect(status().isOk());
        String key = UUID.randomUUID().toString();
        long revision = revision();
        discontinue(key, revision).andExpect(status().isOk());
        discontinue(key, revision).andExpect(status().isOk());
        List<JsonNode> discontinued = events(ExamEventTopics.DISCONTINUED);
        assertThat(discontinued).hasSize(1);
        assertThat(discontinued.getFirst().size()).isEqualTo(2);
        assertThat(discontinued.getFirst().path("examId").asText()).isEqualTo(exam.toString());
        assertThat(discontinued.getFirst().path("versionNo").asInt()).as("the last serving version").isEqualTo(1);
        assertThat(events(ExamEventTopics.PUBLISHED)).as("publish wrote its own event once").hasSize(1);
        // A second discontinue is refused and writes nothing.
        discontinue(UUID.randomUUID().toString(), revision()).andExpect(status().isConflict());
        assertThat(events(ExamEventTopics.DISCONTINUED)).hasSize(1);
    }
}
