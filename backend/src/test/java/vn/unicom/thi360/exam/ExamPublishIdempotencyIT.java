package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.application.ExamCheckFingerprint;
import vn.unicom.thi360.exam.application.ExamCheckResultStore;
import vn.unicom.thi360.exam.infrastructure.ExamKindProfileRepository;
import vn.unicom.thi360.exam.infrastructure.ExamRepository;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;

@Transactional
class ExamPublishIdempotencyIT extends AbstractIntegrationTest {
    private static final UUID EXAM = UUID.fromString("00000000-0000-7000-8700-000000000001");
    private static final UUID SUBJECT = UUID.fromString("00000000-0000-7000-8200-000000000201");
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired ExamRepository exams;
    @Autowired ExamKindProfileRepository profiles;
    @Autowired ExamCheckResultStore results;
    @Autowired QuestionSourcePort source;
    @Autowired CatalogLookup catalog;
    @Autowired EntityManager entityManager;

    private String url() { return "/api/v1/admin/exams/" + EXAM + "/publish"; }

    private void saveCheck(UUID examId, UUID question, String result) {
        var exam = exams.findById(examId).orElseThrow();
        var profile = profiles.findById(exam.getKindItemId()).orElseThrow();
        jdbc.update("UPDATE exam.exam_structure SET structure_hash = 'publish-test' WHERE exam_id = ?", examId);
        var input = new ExamCheckResultStore.Input(examId, exam.getRevision(), "publish-test",
                mapper.valueToTree(ExamCheckFingerprint.Config.from(exam)), profile.getRevision(), catalog.revision(),
                "test", mapper.valueToTree(source.sourceRevisions(List.of(SUBJECT))));
        var outcome = mapper.createObjectNode().put("status", result);
        outcome.putArray("paths").addObject().put("pathId", "default").put("result", result);
        var witnesses = mapper.createArrayNode();
        var path = witnesses.addObject();
        path.putObject("path").putArray("chosen");
        path.putArray("witness").addObject().putArray("groups").addObject().putArray("picks")
                .addObject().put("questionId", question.toString()).put("versionNo", 1);
        assertThat(results.save(input, result, 0, Instant.now(), 1, outcome, witnesses)).isTrue();
    }

    @Test
    void changingChoiceCountsDoesNotAlterThePublishedSnapshot() throws Exception {
        UUID question = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        jdbc.update("INSERT INTO questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) VALUES (?, ?, 'SINGLE_CHOICE', 'READY', ?, 1, 1)", question, "PUB-" + question, SUBJECT);
        jdbc.update("INSERT INTO questionbank.question_version(question_id, version_no, snapshot) VALUES (?, 1, '{}'::jsonb)", question);
        String parts = "{\"parts\":[{\"id\":\"part\",\"options\":[{\"id\":\"choice\",\"countsByPick\":[17,17,16],\"sections\":[]}]}]}";
        jdbc.update("UPDATE exam.exam_structure SET parts = ?::jsonb WHERE exam_id = ?", parts, EXAM);
        jdbc.update("UPDATE exam.exam SET has_unpublished_changes = true WHERE id = ?", EXAM);
        long revision = jdbc.queryForObject("SELECT revision FROM exam.exam WHERE id = ?", Long.class, EXAM);
        saveCheck(EXAM, question, "VALID");
        String body = "{\"pathIds\":[\"default\"],\"expectedRevision\":" + revision + "}";
        mockMvc.perform(post(url()).with(asUser(actor, "editor", Permissions.EXAM_PUBLISH))
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        jdbc.update("UPDATE exam.exam_structure SET parts = jsonb_set(parts, '{parts,0,options,0,countsByPick}', '[18,16,16]'::jsonb) WHERE exam_id = ?", EXAM);
        assertThat(jdbc.queryForObject("SELECT parts #>> '{parts,0,options,0,countsByPick}' FROM exam.exam_version WHERE exam_id=? AND version_no=1", String.class, EXAM))
                .isEqualTo("[17, 17, 16]");
    }

    @Test
    void createdExamAndDurationOnlyEditCanBothBePublished() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID question = UUID.randomUUID();
        jdbc.update("INSERT INTO questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) VALUES (?, ?, 'SINGLE_CHOICE', 'READY', ?, 1, 1)", question, "PUB-" + question, SUBJECT);
        jdbc.update("INSERT INTO questionbank.question_version(question_id, version_no, snapshot) VALUES (?, 1, '{}'::jsonb)", question);
        String create = "{\"kind\":\"HSA\",\"scope\":\"FULL\",\"name\":\"New exam\",\"code\":\"PUB-" + UUID.randomUUID().toString().substring(0, 8) + "\"}";
        String created = mockMvc.perform(post("/api/v1/admin/exams").with(asUser(actor, "editor", Permissions.EXAM_MODIFY))
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.hasUnpublishedChanges").value(true))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(mapper.readTree(created).path("id").asText());
        long revision = mapper.readTree(created).path("revision").asLong();
        saveCheck(id, question, "VALID");
        String publishUrl = "/api/v1/admin/exams/" + id + "/publish";
        String request = "{\"pathIds\":[\"default\"],\"expectedRevision\":" + revision + "}";
        mockMvc.perform(post(publishUrl).with(asUser(actor, "editor", Permissions.EXAM_PUBLISH))
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        entityManager.clear();
        long updatedRevision = jdbc.queryForObject("SELECT revision FROM exam.exam WHERE id = ?", Long.class, id);
        int oldDuration = jdbc.queryForObject("SELECT duration_minutes FROM exam.exam WHERE id = ?", Integer.class, id);
        String patchBody = "{\"durationMinutes\":" + (oldDuration + 10) + ",\"expectedRevision\":" + updatedRevision + "}";
        String updated = mockMvc.perform(patch("/api/v1/admin/exams/" + id).with(asUser(actor, "editor", Permissions.EXAM_MODIFY))
                .contentType(MediaType.APPLICATION_JSON).content(patchBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hasUnpublishedChanges").value(true))
                .andReturn().getResponse().getContentAsString();
        long newRevision = mapper.readTree(updated).path("revision").asLong();
        saveCheck(id, question, "VALID");
        request = "{\"pathIds\":[\"default\"],\"expectedRevision\":" + newRevision + "}";
        mockMvc.perform(post(publishUrl).with(asUser(actor, "editor", Permissions.EXAM_PUBLISH))
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        assertThat(jdbc.queryForObject("SELECT (config->>'durationMinutes')::int FROM exam.exam_version WHERE exam_id=? AND version_no=1", Integer.class, id)).isEqualTo(oldDuration);
        assertThat(jdbc.queryForObject("SELECT (config->>'durationMinutes')::int FROM exam.exam_version WHERE exam_id=? AND version_no=2", Integer.class, id)).isEqualTo(oldDuration + 10);
        String detailUrl = "/api/v1/admin/exams/" + id + "/versions/2";
        mockMvc.perform(get(detailUrl)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(detailUrl).with(asUser(actor, "editor", Permissions.EXAM_PUBLISH))).andExpect(status().isForbidden());
        mockMvc.perform(get(detailUrl).with(asUser(actor, "editor", Permissions.EXAM_MODIFY))).andExpect(status().isForbidden());
        var detail = mapper.readTree(mockMvc.perform(get(detailUrl).with(asUser(actor, "editor", Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.row.version").value(2))
                .andReturn().getResponse().getContentAsString());
        assertThat(detail.path("config").path("durationMinutes").asInt()).isEqualTo(oldDuration + 10);
        assertThat(detail.path("changes")).anySatisfy(change -> {
            assertThat(change.path("where").asText()).isEqualTo("config/durationMinutes");
            assertThat(change.path("before").asText()).isEqualTo(Integer.toString(oldDuration));
            assertThat(change.path("after").asText()).isEqualTo(Integer.toString(oldDuration + 10));
        });
    }

    @Test
    void publishReplaysOriginalResultAndKeepsSnapshot() throws Exception {
        UUID question = UUID.randomUUID();
        jdbc.update("INSERT INTO questionbank.question(id, code, type, status, subject_item_id, current_version_no, points) VALUES (?, ?, 'SINGLE_CHOICE', 'READY', ?, 1, 1)", question, "PUB-" + question, SUBJECT);
        jdbc.update("INSERT INTO questionbank.question_version(question_id, version_no, snapshot) VALUES (?, 1, '{}'::jsonb)", question);
        jdbc.update("UPDATE exam.exam SET has_unpublished_changes = true WHERE id = ?", EXAM);
        long revision = jdbc.queryForObject("SELECT revision FROM exam.exam WHERE id = ?", Long.class, EXAM);
        int duration = jdbc.queryForObject("SELECT duration_minutes FROM exam.exam WHERE id = ?", Integer.class, EXAM);
        saveCheck(EXAM, question, "VALID");
        UUID actor = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        String body = "{\"pathIds\":[\"default\"],\"note\":null,\"expectedRevision\":" + revision + "}";
        var first = mockMvc.perform(post(url()).with(asUser(actor, "publisher", Permissions.EXAM_PUBLISH)).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andReturn().getResponse().getContentAsString();
        var second = mockMvc.perform(post(url()).with(asUser(actor, "publisher", Permissions.EXAM_PUBLISH)).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(second)).isEqualTo(mapper.readTree(first));
        mockMvc.perform(post(url()).with(asUser(actor, "publisher", Permissions.EXAM_PUBLISH)).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body.replace("null", "\"changed\"")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        jdbc.update("UPDATE exam.exam SET duration_minutes = duration_minutes + 10 WHERE id = ?", EXAM);
        assertThat(jdbc.queryForObject("SELECT (config->>'durationMinutes')::integer FROM exam.exam_version WHERE exam_id = ? AND version_no = 1", Integer.class, EXAM)).isEqualTo(duration);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM questionbank.question_usage_ledger WHERE question_id = ? AND kind = 'PUBLISH_WITNESS' AND exam_id = ?", Long.class, question, EXAM)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shared.outbox_event WHERE topic='audit' AND payload->>'action'='EXAM.PUBLISH' AND payload->>'targetId' = ?", Long.class, EXAM.toString())).isEqualTo(1);
    }

    @Test
    void staleSourceAndInvalidPathCannotPublish() throws Exception {
        jdbc.update("UPDATE exam.exam SET has_unpublished_changes = true WHERE id = ?", EXAM);
        long revision = jdbc.queryForObject("SELECT revision FROM exam.exam WHERE id = ?", Long.class, EXAM);
        String body = "{\"pathIds\":[\"default\"],\"expectedRevision\":" + revision + "}";
        saveCheck(EXAM, UUID.randomUUID(), "INVALID");
        jdbc.update("UPDATE exam.exam_check_run SET result = jsonb_set(result, '{paths,0,selections}', ?::jsonb, true) WHERE exam_id = ?",
                "[{\"subjectCodes\":[\"PHYSICS\",\"CHEMISTRY\",\"BIOLOGY\"],\"counts\":[17,17,16],\"result\":\"INVALID\"}]", EXAM);
        mockMvc.perform(post(url()).with(asUser(Permissions.EXAM_PUBLISH)).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("PATH_NOT_VALID"));
        jdbc.update("INSERT INTO questionbank.question_source_revision (subject_item_id, revision) VALUES (?, 1) "
                + "ON CONFLICT (subject_item_id) DO UPDATE SET revision = questionbank.question_source_revision.revision + 1", SUBJECT);
        mockMvc.perform(post(url()).with(asUser(Permissions.EXAM_PUBLISH)).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CHECK_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("SOURCE_CHANGED"));
    }

    @Test
    void missingCheckAndPermissions() throws Exception {
        jdbc.update("UPDATE exam.exam SET has_unpublished_changes = true WHERE id = ?", EXAM);
        long revision = jdbc.queryForObject("SELECT revision FROM exam.exam WHERE id = ?", Long.class, EXAM);
        String body = "{\"pathIds\":[\"default\"],\"expectedRevision\":" + revision + "}";
        mockMvc.perform(post(url()).header("Origin", "http://localhost:3001").header("Idempotency-Key", "one").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        for (String permission : List.of(Permissions.EXAM_VIEW, Permissions.EXAM_MODIFY)) {
            mockMvc.perform(post(url()).with(asUser(permission)).header("Idempotency-Key", "one").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        }
        mockMvc.perform(post(url()).with(asUser(Permissions.EXAM_PUBLISH)).header("Idempotency-Key", "one").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CHECK_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("NO_CHECK"));
        String stale = body.replace("\"expectedRevision\":" + revision, "\"expectedRevision\":" + (revision + 1));
        mockMvc.perform(post(url()).with(asUser(Permissions.EXAM_PUBLISH)).header("Idempotency-Key", "two").contentType(MediaType.APPLICATION_JSON).content(stale)).andExpect(status().isPreconditionFailed());
    }
}
