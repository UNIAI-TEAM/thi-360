package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.application.OverdueAttemptCloser;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;

/**
 * Verifies a write-performing {@link OverdueAttemptCloser} executing SQL {@code UPDATE} inside
 * {@code GET /me/attempts/{id}}, {@code GET /me/entitlements/{id}/prepare}, and
 * {@code POST /me/attempts} succeeds without read-only transaction failures (R1-3, R1-4, R1-14).
 */
class AttemptLazyCloseIT extends AbstractAttemptDsIT {

    @MockitoSpyBean private OverdueAttemptCloser overdueCloser;

    @AfterEach
    void resetCloser() {
        reset(overdueCloser);
    }

    @Test
    void writePerformingOverdueCloserWorksAcrossStatePrepareAndStart() throws Exception {
        UUID userId = createStudent("lazy-close");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 4, List.of());

        // 1. GET /me/attempts/{id} closes overdue IN_PROGRESS attempt via SQL UPDATE inside its transaction
        MvcResult s1 = performStart(userId, "lc-start-1", line.entitlementId(), null);
        assertThat(s1.getResponse().getStatus()).isEqualTo(202);
        UUID a1 = UUID.fromString(json(s1).path("attemptId").asText());
        awaitTerminalStartState(a1, AttemptState.IN_PROGRESS);

        stubSqlCloseOn(a1);
        MvcResult stateRes = performGetState(userId, a1);
        assertThat(stateRes.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(stateRes).path("state").asText()).isEqualTo("SCORED");
        assertThat(jdbc.queryForObject(
                "SELECT state FROM attempt.attempt WHERE id = ?", String.class, a1))
                .isEqualTo("SCORED");

        // 2. GET /me/entitlements/{id}/prepare closes overdue IN_PROGRESS attempt and hides openAttempt
        MvcResult s2 = performStart(userId, "lc-start-2", line.entitlementId(), null);
        assertThat(s2.getResponse().getStatus()).isEqualTo(202);
        UUID a2 = UUID.fromString(json(s2).path("attemptId").asText());
        awaitTerminalStartState(a2, AttemptState.IN_PROGRESS);

        stubSqlCloseOn(a2);
        MvcResult prepRes = performPrepare(userId, line.entitlementId());
        assertThat(prepRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode prepJson = json(prepRes);
        assertThat(prepJson.path("openAttempt").isNull()).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT state FROM attempt.attempt WHERE id = ?", String.class, a2))
                .isEqualTo("SCORED");

        // 3. POST /me/attempts closes overdue IN_PROGRESS attempt and starts a fresh attempt (202, not resumed)
        MvcResult s3 = performStart(userId, "lc-start-3", line.entitlementId(), null);
        assertThat(s3.getResponse().getStatus()).isEqualTo(202);
        UUID a3 = UUID.fromString(json(s3).path("attemptId").asText());
        awaitTerminalStartState(a3, AttemptState.IN_PROGRESS);

        stubSqlCloseOn(a3);
        MvcResult s4 = performStart(userId, "lc-start-4", line.entitlementId(), null);
        assertThat(s4.getResponse().getStatus()).isEqualTo(202);
        JsonNode s4Json = json(s4);
        UUID a4 = UUID.fromString(s4Json.path("attemptId").asText());
        assertThat(a4).isNotEqualTo(a3);
        assertThat(s4Json.path("resumed").asBoolean()).isFalse();
        awaitTerminalStartState(a4, AttemptState.IN_PROGRESS);
        assertThat(jdbc.queryForObject(
                "SELECT state FROM attempt.attempt WHERE id = ?", String.class, a3))
                .isEqualTo("SCORED");
    }

    private void stubSqlCloseOn(UUID attemptId) {
        doAnswer(
                        inv -> {
                            jdbc.update(
                                    "UPDATE attempt.attempt SET state = 'SCORED', end_reason = 'TIME_UP', "
                                            + "finished_at = now() WHERE id = ? AND state = 'IN_PROGRESS'",
                                    attemptId);
                            return null;
                        })
                .when(overdueCloser)
                .closeIfOverdue(eq(attemptId), any());
    }
}
