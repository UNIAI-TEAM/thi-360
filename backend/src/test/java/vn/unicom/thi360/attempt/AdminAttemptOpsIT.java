package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.api.CompensateAttemptRequest;
import vn.unicom.thi360.attempt.api.FaultAttemptRequest;
import vn.unicom.thi360.attempt.api.FinalizeEssayRequest;
import vn.unicom.thi360.attempt.api.FinalAnswerChange;
import vn.unicom.thi360.attempt.api.FinishPartRequest;
import vn.unicom.thi360.attempt.api.SaveAnswersRequest;
import vn.unicom.thi360.attempt.api.StartAttemptRequest.PreChoiceInput;
import vn.unicom.thi360.attempt.api.SubmitAttemptRequest;
import vn.unicom.thi360.attempt.api.AnswerChange;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.EndReason;
import vn.unicom.thi360.attempt.domain.EssayGrade;
import vn.unicom.thi360.attempt.domain.EssayGradeState;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.attempt.infrastructure.EssayGradeRepository;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.outbox.OutboxRelay;

class AdminAttemptOpsIT extends AbstractAttemptDsIT {

    @Autowired private OutboxRelay outboxRelay;
    @Autowired private EssayGradeRepository essayGradeRepository;
    @Autowired private EntitlementGrantService entitlementGrantService;

    @BeforeEach
    void drainBefore() {
        drainAllOutbox();
    }

    @Test
    void regradeKeepsFinalEssays() throws Exception {
        UUID student = createStudent("ops-regrade-student");
        UUID operator = createManager("ops-regrade-admin");

        GrantOrderResult.Line grant = grant(student, "DS-BCA-FULL-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(student, UUID.randomUUID().toString(), grant.entitlementId(), new PreChoiceInput("CA2", null));
        UUID sessionId = claimSession(student, attempt.getId());
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        AttemptItem essayItem = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attempt.getId())
                .stream()
                .filter(i -> i.getQuestionType() == QuestionType.ESSAY)
                .findFirst()
                .orElseThrow();

        List<FinalAnswerChange> essayAnswer = List.of(
                new FinalAnswerChange(essayItem.getId(), 1L, new AnswerValue.Essay("<p>Bài tự luận đã chấm</p>", 5)));
        for (int idx = 0; idx < parts.size() - 1; idx++) {
            AttemptPart p = parts.get(idx);
            List<FinalAnswerChange> finals = p.getId().equals(essayItem.getPartId()) ? essayAnswer : List.of();
            assertThat(performFinishPart(student, attempt.getId(), p.getId(), sessionId,
                    new FinishPartRequest(finals)).getResponse().getStatus()).isEqualTo(200);
        }
        assertThat(performSubmit(student, attempt.getId(), UUID.randomUUID().toString(), sessionId,
                new SubmitAttemptRequest(List.of())).getResponse().getStatus()).isEqualTo(200);
        drainAllOutbox();

        // Grade & finalize essay
        assertThat(mockMvc.perform(post("/api/v1/admin/grading/items/{id}/claim", essayItem.getId())
                        .with(asUser(operator, "grader", Permissions.GRADING_MODIFY)))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mockMvc.perform(post("/api/v1/admin/grading/items/{id}/finalize", essayItem.getId())
                        .with(asUser(operator, "grader", Permissions.GRADING_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FinalizeEssayRequest(null, null, new BigDecimal("19.50"), "Giữ điểm"))))
                .andReturn().getResponse().getStatus()).isEqualTo(200);

        EssayGrade beforeRegrade = essayGradeRepository.findByAttemptItemIdAndSupersededByIsNull(essayItem.getId())
                .orElseThrow();

        // Simulate GRADING_ERROR AND wipe attempt_item earned_points to prove FINAL essay_grade restoration
        jdbc.update(
                "UPDATE attempt.attempt SET state = 'GRADING_ERROR', grading_error = 'Simulated error', raw_score = NULL, display_score = NULL WHERE id = ?",
                attempt.getId());
        jdbc.update(
                "UPDATE attempt.attempt_item SET earned_points = NULL, outcome = NULL, grading_detail = NULL WHERE id = ?",
                essayItem.getId());

        MvcResult res = mockMvc.perform(
                        post("/api/v1/admin/attempts/{id}/regrade", attempt.getId())
                                .with(asUser(operator, "grader", Permissions.GRADING_MODIFY)))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(res);
        assertThat(body.path("state").asText()).isEqualTo("SCORED");
        assertThat(new BigDecimal(body.path("rawScore").asText())).isEqualByComparingTo("19.50");

        Attempt reloaded = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(AttemptState.SCORED);
        assertThat(reloaded.getGradingError()).isNull();

        EssayGrade afterRegrade = essayGradeRepository.findByAttemptItemIdAndSupersededByIsNull(essayItem.getId())
                .orElseThrow();
        assertThat(afterRegrade.getId()).isEqualTo(beforeRegrade.getId());
        assertThat(afterRegrade.getState()).isEqualTo(EssayGradeState.FINAL);
        assertThat(afterRegrade.getPoints()).isEqualByComparingTo("19.50");

        AttemptItem restoredItem = attemptItemRepository.findById(essayItem.getId()).orElseThrow();
        assertThat(restoredItem.getEarnedPoints()).isEqualByComparingTo("19.50");
        assertThat(attemptEventRepository.existsByAttemptIdAndType(attempt.getId(), AttemptEventType.REGRADED))
                .isTrue();

        drainAllOutbox();
        Integer auditCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit.audit_event WHERE module = 'attempt' AND action = 'ATTEMPT_REGRADED' AND target_id = ?",
                Integer.class,
                attempt.getId().toString());
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void regradeRejectedForOtherStates() throws Exception {
        UUID student = createStudent("ops-regrade-reject");
        UUID operator = createManager("ops-regrade-reject-admin");
        GrantOrderResult.Line grant = grant(student, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(student, UUID.randomUUID().toString(), grant.entitlementId(), null);

        for (AttemptState st : List.of(
                AttemptState.STARTING,
                AttemptState.IN_PROGRESS,
                AttemptState.GRADING,
                AttemptState.PENDING_REVIEW,
                AttemptState.SCORED,
                AttemptState.ABORTED)) {
            jdbc.update("UPDATE attempt.attempt SET state = ? WHERE id = ?", st.name(), attempt.getId());
            MvcResult res = mockMvc.perform(
                            post("/api/v1/admin/attempts/{id}/regrade", attempt.getId())
                                    .with(asUser(operator, "grader", Permissions.GRADING_MODIFY)))
                    .andReturn();
            assertThat(res.getResponse().getStatus()).as("state %s", st).isEqualTo(409);
            assertThat(json(res).path("code").asText()).isEqualTo("ATTEMPT_NOT_GRADING_ERROR");
        }

        // Restore valid state for cleanup
        jdbc.update("UPDATE attempt.attempt SET state = 'SCORED' WHERE id = ?", attempt.getId());
    }

    @Test
    void faultClosesOpenAttempt() throws Exception {
        UUID student = createStudent("ops-fault-open");
        UUID operator = createManager("ops-fault-admin");
        GrantOrderResult.Line grant = grant(student, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(student, UUID.randomUUID().toString(), grant.entitlementId(), null);
        UUID sessionId = claimSession(student, attempt.getId());

        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        assertThat(parts).hasSize(3);
        // Finish part 1 & part 2 -> part 3 enters AWAITING_CHOICE
        assertThat(performFinishPart(student, attempt.getId(), parts.get(0).getId(), sessionId,
                new FinishPartRequest(List.of())).getResponse().getStatus()).isEqualTo(200);
        assertThat(performFinishPart(student, attempt.getId(), parts.get(1).getId(), sessionId,
                new FinishPartRequest(List.of())).getResponse().getStatus()).isEqualTo(200);

        AttemptPart part3 = awaitPartState(parts.get(2).getId(), PartState.AWAITING_CHOICE);
        // Simulate persistent generation failure on part 3
        jdbc.update(
                """
                UPDATE attempt.attempt_part
                SET state = 'GENERATION_FAILED',
                    generation_tries = 3,
                    generation_error = 'GENERATION_FAILED'
                WHERE id = ?
                """,
                part3.getId());

        assertThat(countEntitlementUsage(attempt.getId(), "RESERVED")).isEqualTo(1);

        MvcResult faultRes = mockMvc.perform(
                        post("/api/v1/admin/attempts/{id}/fault", attempt.getId())
                                .with(asUser(operator, "ops", Permissions.ATTEMPT_COMPENSATE))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        new FaultAttemptRequest("Lỗi sinh đề phần 3 kéo dài"))))
                .andReturn();
        assertThat(faultRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode faultJson = json(faultRes);
        assertThat(faultJson.path("faultFlag").asText()).isEqualTo("SYSTEM_FAULT");
        assertThat(faultJson.path("faultReason").asText()).isEqualTo("Lỗi sinh đề phần 3 kéo dài");

        Attempt closedAttempt = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(closedAttempt.getEndReason()).isEqualTo(EndReason.FAULT_CLOSED);
        assertThat(closedAttempt.getFaultFlag()).isEqualTo("SYSTEM_FAULT");

        AttemptPart skippedPart3 = attemptPartRepository.findById(part3.getId()).orElseThrow();
        assertThat(skippedPart3.getState()).isEqualTo(PartState.SKIPPED);

        // confirmUse called once
        assertThat(countEntitlementUsage(attempt.getId(), "USED")).isEqualTo(1);
        assertThat(countEntitlementUsage(attempt.getId(), "RESERVED")).isEqualTo(0);

        drainAllOutbox();
        Attempt scored = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(scored.getState()).isEqualTo(AttemptState.SCORED);

        // Student result displays fault notice
        MvcResult studentResult = mockMvc.perform(
                        get("/api/v1/me/attempts/{id}/result", attempt.getId())
                                .with(AttemptTestFixtures.asExamUser(student, "student")))
                .andReturn();
        assertThat(studentResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(studentResult).path("faultFlag").asText()).isEqualTo("SYSTEM_FAULT");
        assertThat(json(studentResult).path("faultMessage").asText()).isEqualTo("Lượt kết thúc do lỗi hệ thống");

        // Second fault call rejected with 409 ATTEMPT_ALREADY_FAULTED
        MvcResult dupFault = mockMvc.perform(
                        post("/api/v1/admin/attempts/{id}/fault", attempt.getId())
                                .with(asUser(operator, "ops", Permissions.ATTEMPT_COMPENSATE))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new FaultAttemptRequest("Lần 2"))))
                .andReturn();
        assertThat(dupFault.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(dupFault).path("code").asText()).isEqualTo("ATTEMPT_ALREADY_FAULTED");
    }

    @Test
    void faultOnClosedAttemptKeepsResult() throws Exception {
        UUID student = createStudent("ops-fault-closed");
        UUID operator = createManager("ops-fault-closed-admin");
        GrantOrderResult.Line grant = grant(student, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(student, UUID.randomUUID().toString(), grant.entitlementId(), null);
        UUID sessionId = claimSession(student, attempt.getId());

        assertThat(performSubmit(student, attempt.getId(), UUID.randomUUID().toString(), sessionId,
                new SubmitAttemptRequest(List.of())).getResponse().getStatus()).isEqualTo(200);
        drainAllOutbox();

        Attempt before = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(before.getState()).isEqualTo(AttemptState.SCORED);
        BigDecimal oldRaw = before.getRawScore();
        BigDecimal oldDisplay = before.getDisplayScore();
        Instant oldGradedAt = before.getGradedAt();

        MvcResult res = mockMvc.perform(
                        post("/api/v1/admin/attempts/{id}/fault", attempt.getId())
                                .with(asUser(operator, "ops", Permissions.ATTEMPT_COMPENSATE))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new FaultAttemptRequest("Sự cố mạng"))))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(200);

        Attempt after = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(after.getState()).isEqualTo(AttemptState.SCORED);
        assertThat(after.getRawScore()).isEqualByComparingTo(oldRaw);
        assertThat(after.getDisplayScore()).isEqualByComparingTo(oldDisplay);
        assertThat(after.getGradedAt()).isEqualTo(oldGradedAt);
        assertThat(after.getFaultFlag()).isEqualTo("SYSTEM_FAULT");

        // STARTING and ABORTED attempts return 409 ATTEMPT_FAULT_NOT_ALLOWED
        Attempt openAttempt = startAndAwaitOpen(student, UUID.randomUUID().toString(), grant.entitlementId(), null);
        for (AttemptState invalidState : List.of(AttemptState.STARTING, AttemptState.ABORTED)) {
            jdbc.update("UPDATE attempt.attempt SET state = ? WHERE id = ?", invalidState.name(), openAttempt.getId());
            MvcResult rejected = mockMvc.perform(
                            post("/api/v1/admin/attempts/{id}/fault", openAttempt.getId())
                                    .with(asUser(operator, "ops", Permissions.ATTEMPT_COMPENSATE))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(new FaultAttemptRequest("Thử"))))
                    .andReturn();
            assertThat(rejected.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(rejected).path("code").asText()).isEqualTo("ATTEMPT_FAULT_NOT_ALLOWED");
        }
        jdbc.update("UPDATE attempt.attempt SET state = 'ABORTED' WHERE id = ?", openAttempt.getId());
    }

    @Test
    void compensateOnlyFaultedOnce() throws Exception {
        UUID student = createStudent("ops-comp-student");
        UUID operator = createManager("ops-comp-admin");
        GrantOrderResult.Line grant = grant(student, "DS-HSA-PART-MATH-MINI", 1, List.of());
        Attempt attempt = startAndAwaitOpen(student, UUID.randomUUID().toString(), grant.entitlementId(), null);
        UUID sessionId = claimSession(student, attempt.getId());
        assertThat(performSubmit(student, attempt.getId(), UUID.randomUUID().toString(), sessionId,
                new SubmitAttemptRequest(List.of())).getResponse().getStatus()).isEqualTo(200);
        drainAllOutbox();

        // Unfaulted attempt -> 409 ATTEMPT_NOT_FAULTED
        MvcResult unfaultedRes = mockMvc.perform(
                        post("/api/v1/admin/attempts/{id}/compensate", attempt.getId())
                                .with(asUser(operator, "ops", Permissions.ATTEMPT_COMPENSATE))
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new CompensateAttemptRequest("Bù"))))
                .andReturn();
        assertThat(unfaultedRes.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(unfaultedRes).path("code").asText()).isEqualTo("ATTEMPT_NOT_FAULTED");

        // Mark fault
        assertThat(mockMvc.perform(post("/api/v1/admin/attempts/{id}/fault", attempt.getId())
                        .with(asUser(operator, "ops", Permissions.ATTEMPT_COMPENSATE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FaultAttemptRequest("Lỗi hệ thống"))))
                .andReturn().getResponse().getStatus()).isEqualTo(200);

        // Compensate -> 200 + 1 COMPENSATION grant + COMPENSATED event + student result updated
        MvcResult compRes = mockMvc.perform(
                        post("/api/v1/admin/attempts/{id}/compensate", attempt.getId())
                                .with(asUser(operator, "ops", Permissions.ATTEMPT_COMPENSATE))
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new CompensateAttemptRequest("Cấp bù sự cố"))))
                .andReturn();
        assertThat(compRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode compJson = json(compRes);
        assertThat(compJson.path("compensated").asBoolean()).isTrue();
        assertThat(compJson.path("entitlementId").asText()).isEqualTo(grant.entitlementId().toString());
        assertThat(compJson.path("grantId").asText()).isNotBlank();

        assertThat(attemptEventRepository.existsByAttemptIdAndType(attempt.getId(), AttemptEventType.COMPENSATED))
                .isTrue();

        drainAllOutbox();
        Integer compAudit = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit.audit_event WHERE module = 'attempt' AND action = 'ATTEMPT_COMPENSATED' AND target_id = ?",
                Integer.class,
                attempt.getId().toString());
        assertThat(compAudit).isEqualTo(1);

        // Student result shows compensated message
        MvcResult studentRes = mockMvc.perform(
                        get("/api/v1/me/attempts/{id}/result", attempt.getId())
                                .with(AttemptTestFixtures.asExamUser(student, "student")))
                .andReturn();
        assertThat(studentRes.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(studentRes).path("compensated").asBoolean()).isTrue();
        assertThat(json(studentRes).path("faultMessage").asText())
                .isEqualTo("Lượt bị ảnh hưởng bởi lỗi hệ thống, đã cấp bù 1 lượt");

        // Second compensate with different key -> 409 ALREADY_COMPENSATED
        MvcResult secondComp = mockMvc.perform(
                        post("/api/v1/admin/attempts/{id}/compensate", attempt.getId())
                                .with(asUser(operator, "ops", Permissions.ATTEMPT_COMPENSATE))
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new CompensateAttemptRequest("Cấp lần 2"))))
                .andReturn();
        assertThat(secondComp.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(secondComp).path("code").asText()).isEqualTo("ALREADY_COMPENSATED");

        // Concurrent two-key race on a fresh faulted attempt -> only one succeeds
        UUID studentRace = createStudent("ops-comp-race");
        GrantOrderResult.Line raceGrant = grant(studentRace, "DS-HSA-PART-MATH-MINI", 1, List.of());
        Attempt raceAttempt = startAndAwaitOpen(studentRace, UUID.randomUUID().toString(), raceGrant.entitlementId(), null);
        UUID raceSession = claimSession(studentRace, raceAttempt.getId());
        assertThat(performSubmit(studentRace, raceAttempt.getId(), UUID.randomUUID().toString(), raceSession,
                new SubmitAttemptRequest(List.of())).getResponse().getStatus()).isEqualTo(200);
        drainAllOutbox();
        assertThat(mockMvc.perform(post("/api/v1/admin/attempts/{id}/fault", raceAttempt.getId())
                        .with(asUser(operator, "ops", Permissions.ATTEMPT_COMPENSATE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FaultAttemptRequest("Fault race"))))
                .andReturn().getResponse().getStatus()).isEqualTo(200);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            Future<MvcResult> f1 = pool.submit(() -> {
                ready.countDown();
                go.await();
                return mockMvc.perform(post("/api/v1/admin/attempts/{id}/compensate", raceAttempt.getId())
                                .with(asUser(operator, "ops", Permissions.ATTEMPT_COMPENSATE))
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new CompensateAttemptRequest("Race 1"))))
                        .andReturn();
            });
            Future<MvcResult> f2 = pool.submit(() -> {
                ready.countDown();
                go.await();
                return mockMvc.perform(post("/api/v1/admin/attempts/{id}/compensate", raceAttempt.getId())
                                .with(asUser(operator, "ops", Permissions.ATTEMPT_COMPENSATE))
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new CompensateAttemptRequest("Race 2"))))
                        .andReturn();
            });
            ready.await();
            go.countDown();

            List<MvcResult> outcomes = List.of(f1.get(), f2.get());
            assertThat(outcomes.stream().filter(r -> r.getResponse().getStatus() == 200)).hasSize(1);
            List<MvcResult> conflicts = outcomes.stream().filter(r -> r.getResponse().getStatus() == 409).toList();
            assertThat(conflicts).hasSize(1);
            assertThat(json(conflicts.getFirst()).path("code").asText()).isEqualTo("ALREADY_COMPENSATED");

            Integer grantCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM commerce.entitlement_grant WHERE compensation_attempt_id = ?",
                    Integer.class,
                    raceAttempt.getId());
            assertThat(grantCount).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void compensateReplay() throws Exception {
        UUID student = createStudent("ops-replay-student");
        UUID operator = createManager("ops-replay-admin");
        GrantOrderResult.Line grant = grant(student, "DS-HSA-PART-MATH-MINI", 1, List.of());
        Attempt attempt = startAndAwaitOpen(student, UUID.randomUUID().toString(), grant.entitlementId(), null);
        UUID sessionId = claimSession(student, attempt.getId());
        assertThat(performSubmit(student, attempt.getId(), UUID.randomUUID().toString(), sessionId,
                new SubmitAttemptRequest(List.of())).getResponse().getStatus()).isEqualTo(200);
        drainAllOutbox();
        assertThat(mockMvc.perform(post("/api/v1/admin/attempts/{id}/fault", attempt.getId())
                        .with(asUser(operator, "ops", Permissions.ATTEMPT_COMPENSATE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FaultAttemptRequest("Fault"))))
                .andReturn().getResponse().getStatus()).isEqualTo(200);

        // Missing Idempotency-Key -> 400 IDEMPOTENCY_KEY_REQUIRED
        MvcResult missingKey = mockMvc.perform(
                        post("/api/v1/admin/attempts/{id}/compensate", attempt.getId())
                                .with(asUser(operator, "ops", Permissions.ATTEMPT_COMPENSATE))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new CompensateAttemptRequest("Bù"))))
                .andReturn();
        assertThat(missingKey.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(missingKey).path("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");

        String key = UUID.randomUUID().toString();
        MvcResult first = mockMvc.perform(
                        post("/api/v1/admin/attempts/{id}/compensate", attempt.getId())
                                .with(asUser(operator, "ops", Permissions.ATTEMPT_COMPENSATE))
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new CompensateAttemptRequest("Bù lần 1"))))
                .andReturn();
        assertThat(first.getResponse().getStatus()).isEqualTo(200);

        // Same key, same body -> cached 200 with exact same grantId
        MvcResult replay = mockMvc.perform(
                        post("/api/v1/admin/attempts/{id}/compensate", attempt.getId())
                                .with(asUser(operator, "ops", Permissions.ATTEMPT_COMPENSATE))
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new CompensateAttemptRequest("Bù lần 1"))))
                .andReturn();
        assertThat(replay.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(replay).path("grantId").asText()).isEqualTo(json(first).path("grantId").asText());

        // Same key, different body -> 409 IDEMPOTENCY_KEY_REUSED
        MvcResult conflict = mockMvc.perform(
                        post("/api/v1/admin/attempts/{id}/compensate", attempt.getId())
                                .with(asUser(operator, "ops", Permissions.ATTEMPT_COMPENSATE))
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new CompensateAttemptRequest("Khác lý do"))))
                .andReturn();
        assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(conflict).path("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void compensateRefundedGrant() throws Exception {
        UUID student = createStudent("ops-refunded-student");
        UUID operator = createManager("ops-refunded-admin");
        GrantOrderResult.Line grant = grant(student, "DS-HSA-PART-MATH-MINI", 1, List.of());
        Attempt attempt = startAndAwaitOpen(student, UUID.randomUUID().toString(), grant.entitlementId(), null);
        UUID sessionId = claimSession(student, attempt.getId());
        assertThat(performSubmit(student, attempt.getId(), UUID.randomUUID().toString(), sessionId,
                new SubmitAttemptRequest(List.of())).getResponse().getStatus()).isEqualTo(200);
        drainAllOutbox();

        assertThat(mockMvc.perform(post("/api/v1/admin/attempts/{id}/fault", attempt.getId())
                        .with(asUser(operator, "ops", Permissions.ATTEMPT_COMPENSATE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FaultAttemptRequest("Fault before refund"))))
                .andReturn().getResponse().getStatus()).isEqualTo(200);

        // Refund the source grant
        entitlementGrantService.revokeUnused(grant.grantId());

        MvcResult res = mockMvc.perform(
                        post("/api/v1/admin/attempts/{id}/compensate", attempt.getId())
                                .with(asUser(operator, "ops", Permissions.ATTEMPT_COMPENSATE))
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new CompensateAttemptRequest("Cố bù sau hoàn"))))
                .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(res).path("code").asText()).isEqualTo("GRANT_REFUNDED");
    }

    @Test
    void detailShowsEventsAndAudit() throws Exception {
        UUID student = createStudent("ops-detail-student");
        String username = jdbc.queryForObject(
                "SELECT username FROM identity.user_account WHERE id = ?", String.class, student);
        UUID viewer = createManager("ops-detail-viewer");

        GrantOrderResult.Line grant = grant(student, "DS-HSA-PART-MATH-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(student, UUID.randomUUID().toString(), grant.entitlementId(), null);
        UUID sessionId = claimSession(student, attempt.getId());

        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        AttemptItem scItem = singleChoiceItems(parts.get(0).getId()).getFirst();
        String chosenOption = scItem.getOptionOrder().getFirst();

        assertThat(performPutAnswers(
                student,
                attempt.getId(),
                sessionId,
                new SaveAnswersRequest(
                        List.of(new AnswerChange(scItem.getId(), 1L, new AnswerValue.SingleChoice(chosenOption))),
                        List.of(),
                        null)).getResponse().getStatus()).isEqualTo(200);

        assertThat(performSubmit(student, attempt.getId(), UUID.randomUUID().toString(), sessionId,
                new SubmitAttemptRequest(List.of())).getResponse().getStatus()).isEqualTo(200);
        drainAllOutbox();

        // Filter list by user UUID, username, unknown username, exam, state, date
        JsonNode byUuid = json(mockMvc.perform(get("/api/v1/admin/attempts")
                        .param("user", student.toString())
                        .param("exam", attempt.getExamId().toString())
                        .param("state", "SCORED")
                        .with(asUser(viewer, "viewer", Permissions.ATTEMPT_VIEW)))
                .andReturn());
        assertThat(byUuid.path("items").size()).isEqualTo(1);
        assertThat(byUuid.path("items").get(0).path("username").asText()).isEqualTo(username);

        String vnDate = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(attempt.getStartedAt());
        JsonNode byUsername = json(mockMvc.perform(get("/api/v1/admin/attempts")
                        .param("user", username)
                        .param("exam", "hsa")
                        .param("from", vnDate)
                        .param("to", vnDate)
                        .with(asUser(viewer, "viewer", Permissions.ATTEMPT_VIEW)))
                .andReturn());
        assertThat(byUsername.path("items")).extracting(n -> n.path("attemptId").asText())
                .containsExactly(attempt.getId().toString());

        JsonNode unknownUser = json(mockMvc.perform(get("/api/v1/admin/attempts")
                        .param("user", "no.such.user.xyz")
                        .with(asUser(viewer, "viewer", Permissions.ATTEMPT_VIEW)))
                .andReturn());
        assertThat(unknownUser.path("items")).isEmpty();
        assertThat(unknownUser.path("totalItems").asLong()).isEqualTo(0L);

        // Detail endpoint returns parts, events, items with answerReceivedAt & answerSeq, and writes ATTEMPT_VIEWED_BY_ADMIN audit
        MvcResult detailRes = mockMvc.perform(get("/api/v1/admin/attempts/{id}", attempt.getId())
                        .with(asUser(viewer, "viewer", Permissions.ATTEMPT_VIEW)))
                .andReturn();
        assertThat(detailRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode detail = json(detailRes);
        assertThat(detail.path("username").asText()).isEqualTo(username);
        assertThat(detail.path("parts").size()).isEqualTo(1);
        assertThat(detail.path("events")).extracting(e -> e.path("type").asText())
                .contains("STARTED", "SESSION_CLAIMED", "SUBMITTED", "GRADED");

        JsonNode matchedItem = null;
        for (JsonNode it : detail.path("items")) {
            if (scItem.getId().toString().equals(it.path("itemId").asText())) {
                matchedItem = it;
                break;
            }
        }
        assertThat(matchedItem).isNotNull();
        assertThat(matchedItem.path("answerSeq").asLong()).isEqualTo(1L);
        assertThat(matchedItem.path("answerReceivedAt").asText()).isNotBlank();
        assertThat(matchedItem.path("answer").path("optionId").asText()).isEqualTo(chosenOption);

        drainAllOutbox();
        Integer viewedAudit = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit.audit_event WHERE module = 'attempt' AND action = 'ATTEMPT_VIEWED_BY_ADMIN' AND target_id = ?",
                Integer.class,
                attempt.getId().toString());
        assertThat(viewedAudit).isEqualTo(1);
    }

    private void drainAllOutbox() {
        int batch;
        do {
            batch = outboxRelay.relayBatch(500);
        } while (batch > 0);
    }
}
