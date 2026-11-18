package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.exam.application.DefaultExamChecker;
import vn.unicom.thi360.exam.application.ExamChecker;
import vn.unicom.thi360.identity.spi.Permissions;

@Import(ExamDeepCheckIT.GatedCheckerConfiguration.class)
class ExamDeepCheckIT extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private Gate gate;
    private UUID examId;
    private String url;

    @TestConfiguration(proxyBeanMethods = false)
    static class GatedCheckerConfiguration {
        @Bean Gate gate() { return new Gate(); }
        @Bean @Primary ExamChecker gatedChecker(DefaultExamChecker delegate, Gate gate) {
            return (snapshot, deadline, budget, scope) -> {
                if (snapshot.exam().getId().equals(gate.examId)) {
                    gate.entered.countDown();
                    try {
                        if (!gate.release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("test gate timed out");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                }
                return delegate.check(snapshot, deadline, budget, scope);
            };
        }
    }

    static class Gate {
        volatile UUID examId;
        volatile CountDownLatch entered;
        volatile CountDownLatch release;
        void arm(UUID id) { examId = id; entered = new CountDownLatch(1); release = new CountDownLatch(1); }
    }

    @BeforeEach
    void createExam() {
        examId = UUID.randomUUID();
        url = "/api/v1/admin/exams/" + examId + "/check";
        jdbc.update("INSERT INTO exam.exam (id,code,code_normalized,name,scope,timing) "
                + "VALUES (?,?,?,'Deep check','FULL','SHARED')", examId, examId.toString(), examId.toString());
        jdbc.update("INSERT INTO exam.exam_structure (exam_id,parts,structure_hash) VALUES (?,'{\"parts\":[]}'::jsonb,'empty')", examId);
        gate.arm(examId);
    }

    @AfterEach
    void cleanup() {
        gate.release.countDown();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM exam.exam_check_job WHERE exam_id=? AND state='RUNNING'", Integer.class, examId)).isZero());
        jdbc.update("DELETE FROM exam.exam WHERE id=?", examId);
    }

    @Test
    void repeatedRequestsShareOneRunningJobAndPreserveActorAndTrace() throws Exception {
        String first = mockMvc.perform(post(url).param("deep", "true").header("X-Request-Id", "deep-check-trace")
                        .with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        UUID runId = UUID.fromString(mapper.readTree(first).path("runId").asText());
        assertThat(gate.entered.await(10, TimeUnit.SECONDS)).isTrue();
        String second = mockMvc.perform(post(url).param("deep", "true").with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(second).path("runId").asText()).isEqualTo(runId.toString());
        mockMvc.perform(get(url + "/runs/" + runId).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("RUNNING"));
        gate.release.countDown();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(state(runId)).isEqualTo("DONE"));
        mockMvc.perform(get(url + "/runs/" + runId).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("DONE"))
                .andExpect(jsonPath("$.result.stale").value(false)).andExpect(jsonPath("$.result.timeLimitSeconds").value(120));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_check_run WHERE exam_id=?", Integer.class, examId)).isEqualTo(1);
        var event = mapper.readTree(jdbc.queryForObject("SELECT payload::text FROM shared.outbox_event WHERE topic='audit' "
                + "AND payload->>'action'='EXAM.CHECK' AND payload->>'targetId'=?", String.class, examId.toString()));
        assertThat(event.path("traceId").asText()).isEqualTo("deep-check-trace");
        assertThat(event.path("actorId").asText()).isEqualTo(jdbc.queryForObject(
                "SELECT started_by::text FROM exam.exam_check_job WHERE id=?", String.class, runId));
    }

    @Test
    void savedUnrepresentableDraftsCompleteWithCheckIssues() throws Exception {
        gate.release.countDown();
        for (String[] fixture : ExamCheckApiIT.draftFixtures()) {
            jdbc.update("UPDATE exam.exam_structure SET parts=?::jsonb WHERE exam_id=?", fixture[0], examId);
            String response = mockMvc.perform(post(url).param("deep", "true").with(asUser(Permissions.EXAM_MODIFY)))
                    .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
            UUID runId = UUID.fromString(mapper.readTree(response).path("runId").asText());
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(state(runId)).isEqualTo("DONE"));
            String completed = mockMvc.perform(get(url + "/runs/" + runId).with(asUser(Permissions.EXAM_VIEW)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.result.status").value("INVALID"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(mapper.readTree(completed).path("result").path("groups").findValuesAsText("code")).contains(fixture[1]);
        }
    }

    @Test
    void editingWhileWorkerRunsReturnsStaleWithoutSavingCheck() throws Exception {
        String response = mockMvc.perform(post(url).param("deep", "true").with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        UUID runId = UUID.fromString(mapper.readTree(response).path("runId").asText());
        assertThat(gate.entered.await(10, TimeUnit.SECONDS)).isTrue();
        jdbc.update("UPDATE exam.exam SET revision=revision+1 WHERE id=?", examId);
        gate.release.countDown();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(state(runId)).isEqualTo("DONE"));
        mockMvc.perform(get(url + "/runs/" + runId).with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.stale").value(true));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_check_run WHERE exam_id=?", Integer.class, examId)).isZero();
    }

    @Test
    void abandonedRunFailsAndAllowsANewRun() throws Exception {
        UUID old = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam_check_job (id,exam_id,state,started_by,started_at) "
                + "VALUES (?,?,'RUNNING',?,now()-interval '151 seconds')", old, examId, UUID.randomUUID());
        mockMvc.perform(get(url + "/runs/" + old).header("X-Request-Id", "abandoned-check-trace")
                        .with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("CHECK_RUN_ABANDONED"));
        var audit = mapper.readTree(jdbc.queryForObject("SELECT payload::text FROM shared.outbox_event WHERE topic='audit' "
                + "AND payload->>'action'='EXAM.CHECK_FAILED' AND payload->>'targetId'=?", String.class, examId.toString()));
        assertThat(audit.path("traceId").asText()).isEqualTo("abandoned-check-trace");
        assertThat(audit.path("after").path("errorCode").asText()).isEqualTo("CHECK_RUN_ABANDONED");
        assertThat(audit.path("after").path("runId").asText()).isEqualTo(old.toString());
        UUID other = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam_check_job (id,exam_id,state,started_by,started_at) "
                + "VALUES (?,?,'RUNNING',?,now()-interval '151 seconds')", other, examId, UUID.randomUUID());
        String response = mockMvc.perform(post(url).param("deep", "true").with(asUser(Permissions.EXAM_MODIFY)))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        UUID next = UUID.fromString(mapper.readTree(response).path("runId").asText());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shared.outbox_event WHERE topic='audit' "
                + "AND payload->>'action'='EXAM.CHECK_FAILED' AND payload->'after'->>'runId'=?", Integer.class, other.toString()))
                .isEqualTo(1);
        assertThat(next).isNotEqualTo(old);
        assertThat(gate.entered.await(10, TimeUnit.SECONDS)).isTrue();
        gate.release.countDown();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(state(next)).isEqualTo("DONE"));
    }

    private String state(UUID runId) {
        return jdbc.queryForObject("SELECT state FROM exam.exam_check_job WHERE id=?", String.class, runId);
    }
}
