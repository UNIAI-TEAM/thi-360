package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.api.SubmitAttemptRequest;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptEvent;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.outbox.OutboxRelay;

class AttemptHistoryIT extends AbstractAttemptDsIT {

    @Autowired private OutboxRelay outboxRelay;

    @BeforeEach
    void drainOutbox() {
        while (outboxRelay.relayBatch(500) > 0) {
            // drain backlog
        }
    }

    @Test
    void excludesStartingAndAbortedAndMasksUnscoredTotals() throws Exception {
        UUID student = createStudent("hist-states");
        GrantOrderResult.Line grant = grant(student, "DS-HSA-PART-MATH-MINI", 10, List.of());

        // 1. Scored attempt (EARNED 0 -> genuine 0 displayed only on SCORED)
        Attempt scored = startAndAwaitOpen(student, UUID.randomUUID().toString(), grant.entitlementId(), null);
        UUID s1 = claimSession(student, scored.getId());
        performSubmit(student, scored.getId(), UUID.randomUUID().toString(), s1, new SubmitAttemptRequest(List.of()));
        outboxRelay.relayBatch(500);
        jdbc.update(
                "UPDATE attempt.attempt SET started_at = '2026-11-20T08:00:00Z' WHERE id = ?",
                scored.getId());

        // Record COMPENSATED event on scored attempt
        attemptEventRepository.saveAndFlush(new AttemptEvent(
                Ids.newId(), scored.getId(), AttemptEventType.COMPENSATED, null, null, Instant.now(), Map.of("r", "x")));

        // 2. GRADING_ERROR attempt (must NOT show 0 score!)
        mutableClock.setAt(Instant.parse("2026-11-20T09:00:00Z"));
        Attempt gradingError = startAndAwaitOpen(student, UUID.randomUUID().toString(), grant.entitlementId(), null);
        UUID s2 = claimSession(student, gradingError.getId());
        performSubmit(student, gradingError.getId(), UUID.randomUUID().toString(), s2, new SubmitAttemptRequest(List.of()));
        jdbc.update(
                "UPDATE attempt.attempt SET state = 'GRADING_ERROR', grading_error = 'boom', raw_score = NULL, display_score = NULL, started_at = '2026-11-20T09:00:00Z' WHERE id = ?",
                gradingError.getId());

        // 3. PENDING_REVIEW + regrading=true attempt (must show old score & label)
        mutableClock.setAt(Instant.parse("2026-11-20T09:30:00Z"));
        Attempt regrading = startAndAwaitOpen(student, UUID.randomUUID().toString(), grant.entitlementId(), null);
        UUID s3 = claimSession(student, regrading.getId());
        performSubmit(student, regrading.getId(), UUID.randomUUID().toString(), s3, new SubmitAttemptRequest(List.of()));
        jdbc.update(
                "UPDATE attempt.attempt SET state = 'PENDING_REVIEW', regrading = true, raw_score = 12.00, display_score = 12.00, started_at = '2026-11-20T09:30:00Z' WHERE id = ?",
                regrading.getId());

        // 4. STARTING and ABORTED rows on another examId (must NEVER appear in history)
        UUID startingId = Ids.newId();
        UUID abortedId = Ids.newId();
        UUID hiddenExamId = Ids.newId();
        jdbc.update(
                """
                        INSERT INTO attempt.attempt (
                            id, user_id, exam_id, exam_version_no, entitlement_id, grant_id,
                            exam_name, exam_kind_code, scope, timing, navigation_mode,
                            choices, seed, generation_token, state, regrading, result_view, created_at, updated_at
                        )
                        VALUES
                            (?, ?, ?, 1, ?, ?, 'Hidden Starting', 'HSA', 'FULL', 'PER_PART', 'SEQUENTIAL', '{}'::jsonb, 1, gen_random_uuid(), 'STARTING', false, 'SCORE', now(), now()),
                            (?, ?, ?, 1, ?, ?, 'Hidden Aborted', 'HSA', 'FULL', 'PER_PART', 'SEQUENTIAL', '{}'::jsonb, 2, gen_random_uuid(), 'ABORTED', false, 'SCORE', now(), now())
                        """,
                startingId, student, hiddenExamId, grant.entitlementId(), grant.grantId(),
                abortedId, student, hiddenExamId, grant.entitlementId(), grant.grantId());

        // 5. Open IN_PROGRESS attempt (must appear with CONTINUE & null scores)
        mutableClock.setAt(Instant.parse("2026-11-20T10:00:00Z"));
        Attempt openAttempt = startAndAwaitOpen(student, UUID.randomUUID().toString(), grant.entitlementId(), null);
        jdbc.update(
                "UPDATE attempt.attempt SET started_at = '2026-11-20T10:00:00Z' WHERE id = ?",
                openAttempt.getId());

        MvcResult res = mockMvc.perform(
                        get("/api/v1/me/attempts")
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        JsonNode items = objectMapper.readTree(res.getResponse().getContentAsString()).path("items");

        assertThat(items).hasSize(4);
        List<String> returnedIds = new ArrayList<>();
        items.forEach(i -> returnedIds.add(i.path("attemptId").asText()));
        assertThat(returnedIds).doesNotContain(startingId.toString(), abortedId.toString());
        // Ordered started_at DESC: openAttempt -> regrading -> gradingError -> scored
        assertThat(returnedIds).containsExactly(
                openAttempt.getId().toString(),
                regrading.getId().toString(),
                gradingError.getId().toString(),
                scored.getId().toString());

        JsonNode openNode = items.get(0);
        assertThat(openNode.path("action").asText()).isEqualTo("CONTINUE");
        assertThat(openNode.path("displayScore").isNull()).isTrue();

        JsonNode regradingNode = items.get(1);
        assertThat(regradingNode.path("regrading").asBoolean()).isTrue();
        assertThat(regradingNode.path("regradingLabel").asText()).isEqualTo("Đang chấm lại");
        assertThat(regradingNode.path("displayScore").decimalValue()).isEqualByComparingTo("12.00");

        JsonNode errorNode = items.get(2);
        assertThat(errorNode.path("state").asText()).isEqualTo("GRADING_ERROR");
        assertThat(errorNode.path("displayScore").isNull()).isTrue();

        JsonNode scoredNode = items.get(3);
        assertThat(scoredNode.path("action").asText()).isEqualTo("VIEW_RESULT");
        assertThat(scoredNode.path("displayScore").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(scoredNode.path("compensated").asBoolean()).isTrue();
    }

    @Test
    void filtersBySearchKindScopeExamIdDateRangeAndPaginates() throws Exception {
        UUID student = createStudent("hist-filter");
        UUID other = createStudent("hist-other");
        UUID hsaExamId = AttemptTestFixtures.dsExamId(jdbc, "DS-HSA-PART-MATH-MINI");
        UUID vactExamId = AttemptTestFixtures.dsExamId(jdbc, "DS-VACT-PART-LANGUAGE-MINI");

        GrantOrderResult.Line hsaGrant = grant(student, "DS-HSA-PART-MATH-MINI", 3, List.of());
        GrantOrderResult.Line vactGrant = grant(student, "DS-VACT-PART-LANGUAGE-MINI", 3, List.of());
        GrantOrderResult.Line otherGrant = grant(other, "DS-HSA-PART-MATH-MINI", 2, List.of());

        Attempt aHsa = startAndAwaitOpen(student, UUID.randomUUID().toString(), hsaGrant.entitlementId(), null);
        UUID sHsa = claimSession(student, aHsa.getId());
        performSubmit(student, aHsa.getId(), UUID.randomUUID().toString(), sHsa, new SubmitAttemptRequest(List.of()));

        Attempt aVact = startAndAwaitOpen(student, UUID.randomUUID().toString(), vactGrant.entitlementId(), null);
        UUID sVact = claimSession(student, aVact.getId());
        performSubmit(student, aVact.getId(), UUID.randomUUID().toString(), sVact, new SubmitAttemptRequest(List.of()));

        // Other student's attempt must never leak
        startAndAwaitOpen(other, UUID.randomUUID().toString(), otherGrant.entitlementId(), null);
        outboxRelay.relayBatch(500);

        jdbc.update("UPDATE attempt.attempt SET started_at = '2026-11-10T03:00:00Z' WHERE id = ?", aHsa.getId());
        jdbc.update("UPDATE attempt.attempt SET started_at = '2026-11-20T03:00:00Z' WHERE id = ?", aVact.getId());

        // Filter by kind=VACT
        JsonNode byKind = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/me/attempts").param("kind", "VACT")
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                        .andReturn().getResponse().getContentAsString());
        assertThat(byKind.path("items")).hasSize(1);
        assertThat(byKind.path("items").get(0).path("attemptId").asText()).isEqualTo(aVact.getId().toString());

        // Filter by examId
        JsonNode byExam = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/me/attempts").param("examId", hsaExamId.toString())
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                        .andReturn().getResponse().getContentAsString());
        assertThat(byExam.path("items")).hasSize(1);
        assertThat(byExam.path("items").get(0).path("attemptId").asText()).isEqualTo(aHsa.getId().toString());

        // Filter by date window covering only aVact
        JsonNode byDate = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/me/attempts")
                                .param("from", "2026-11-18")
                                .param("to", "2026-11-21")
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                        .andReturn().getResponse().getContentAsString());
        assertThat(byDate.path("items")).hasSize(1);
        assertThat(byDate.path("items").get(0).path("attemptId").asText()).isEqualTo(aVact.getId().toString());

        // Filter by ?q= alone (substring match on examName)
        String vactNameToken = aVact.getExamName().split(" ")[0];
        JsonNode byQ = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/me/attempts")
                                .param("q", vactNameToken)
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                        .andReturn().getResponse().getContentAsString());
        assertThat(byQ.path("items")).isNotEmpty();
        boolean containsVact = false;
        for (JsonNode n : byQ.path("items")) {
            if (n.path("attemptId").asText().equals(aVact.getId().toString())) {
                containsVact = true;
            }
        }
        assertThat(containsVact).isTrue();

        // Pagination size=1, page=0 and page=1
        JsonNode p0 = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/me/attempts").param("page", "0").param("size", "1")
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                        .andReturn().getResponse().getContentAsString());
        assertThat(p0.path("items")).hasSize(1);
        assertThat(p0.path("totalItems").asLong()).isEqualTo(2);
        assertThat(p0.path("totalPages").asInt()).isEqualTo(2);

        JsonNode p1 = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/me/attempts").param("page", "1").param("size", "1")
                                .with(AttemptTestFixtures.asExamUser(student, "learner")))
                        .andReturn().getResponse().getContentAsString());
        assertThat(p1.path("items")).hasSize(1);
        assertThat(p1.path("items").get(0).path("attemptId").asText()).isEqualTo(aHsa.getId().toString());

        // Role / auth gates
        assertThat(mockMvc.perform(get("/api/v1/me/attempts")).andReturn().getResponse().getStatus())
                .isEqualTo(401);
        UUID manager = createManager("hist-mgr");
        assertThat(mockMvc.perform(get("/api/v1/me/attempts")
                                .with(AttemptTestFixtures.asManageUser(manager, "mgr")))
                        .andReturn().getResponse().getStatus())
                .isEqualTo(403);
    }
}
