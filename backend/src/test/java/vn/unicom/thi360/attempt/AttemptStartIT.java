package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.api.StartAttemptRequest.PreChoiceInput;
import vn.unicom.thi360.attempt.domain.AbortReason;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.NavigationMode;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.exam.spi.PaperSolverPort;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedGroup;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPaper;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPart;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedQuestion;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedSection;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingPath;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.api.BulkQuestionStatusRequest;
import vn.unicom.thi360.questionbank.api.SaveQuestionRequest;
import vn.unicom.thi360.questionbank.domain.QuestionBody;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.ids.Ids;

/** Integration tests for {@code POST /api/v1/me/attempts} (AC-1..5, 13..15, 17, 21, 28..31, 34). */
class AttemptStartIT extends AbstractAttemptDsIT {

    @MockitoSpyBean private PaperSolverPort paperSolverPort;
    @Autowired private EntitlementPort entitlementPort;

    @AfterEach
    void resetSpy() {
        reset(paperSolverPort);
    }

    @Test
    void concurrentStartsCreateOneAttempt() throws Exception {
        UUID userId = createStudent("concurrent-start");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 3, List.of());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        try {
            Callable<MvcResult> task =
                    () -> {
                        ready.countDown();
                        assertThat(fire.await(10, TimeUnit.SECONDS)).isTrue();
                        return performStart(
                                userId, "key-" + UUID.randomUUID(), line.entitlementId(), null);
                    };
            Future<MvcResult> f1 = pool.submit(task);
            Future<MvcResult> f2 = pool.submit(task);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            fire.countDown();

            MvcResult r1 = f1.get(15, TimeUnit.SECONDS);
            MvcResult r2 = f2.get(15, TimeUnit.SECONDS);
            List<Integer> statuses =
                    List.of(r1.getResponse().getStatus(), r2.getResponse().getStatus());
            assertThat(statuses).containsExactlyInAnyOrder(202, 200);

            JsonNode j1 = json(r1);
            JsonNode j2 = json(r2);
            UUID attemptId = UUID.fromString(j1.path("attemptId").asText());
            assertThat(j2.path("attemptId").asText()).isEqualTo(attemptId.toString());
            assertThat(List.of(j1.path("resumed").asBoolean(), j2.path("resumed").asBoolean()))
                    .containsExactlyInAnyOrder(false, true);

            Attempt completed = awaitTerminalStartState(attemptId, AttemptState.IN_PROGRESS);
            assertThat(completed.getAttemptNo()).isEqualTo(1);
            assertThat(countEntitlementUsageForGrant(line.grantId(), "RESERVED")).isEqualTo(1);
            Integer attemptCount =
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM attempt.attempt WHERE user_id = ? AND exam_id = ?",
                            Integer.class,
                            userId,
                            line.examId());
            assertThat(attemptCount).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void openAttemptIsResumed() throws Exception {
        UUID user1 = createStudent("resume-starting");
        GrantOrderResult.Line l1 = grant(user1, "DS-HSA-PART-MATH-MINI", 3, List.of());
        ServingExam serving1 = publishedExamPort.findServing(l1.examId()).orElseThrow();
        PublishedVersion v1 = serving1.version();

        // 1. Existing STARTING attempt resumes with 200 resumed=true and zero extra reserve
        UUID startingId = Ids.newId();
        entitlementPort.reserve(user1, l1.entitlementId(), startingId);
        attemptRepository.saveAndFlush(
                Attempt.start(
                        startingId,
                        user1,
                        l1.examId(),
                        v1.versionNo(),
                        l1.entitlementId(),
                        l1.grantId(),
                        v1.name(),
                        v1.kindItemId(),
                        v1.kindCode(),
                        v1.scope(),
                        v1.partCode(),
                        v1.subjectItemId(),
                        AttemptTiming.valueOf(v1.timing()),
                        NavigationMode.SEQUENTIAL,
                        Map.of(),
                        99L,
                        Ids.newId(),
                        "SCORE_AND_ANSWERS",
                        v1.practiceScoring(),
                        v1.referenceMax(),
                        v1.maxScore(),
                        "seed-starting-key",
                        mutableClock.instant()));

        MvcResult resumeStarting = performStart(user1, "new-key-1", l1.entitlementId(), null);
        assertThat(resumeStarting.getResponse().getStatus()).isEqualTo(200);
        JsonNode rsJson = json(resumeStarting);
        assertThat(rsJson.path("attemptId").asText()).isEqualTo(startingId.toString());
        assertThat(rsJson.path("state").asText()).isEqualTo("STARTING");
        assertThat(rsJson.path("resumed").asBoolean()).isTrue();
        assertThat(countEntitlementUsageForGrant(l1.grantId(), "RESERVED")).isEqualTo(1);

        // 2. Existing IN_PROGRESS attempt resumes with 200 resumed=true and zero extra reserve
        UUID user2 = createStudent("resume-inprogress");
        GrantOrderResult.Line l2 = grant(user2, "DS-HSA-PART-MATH-MINI", 3, List.of());
        MvcResult first = performStart(user2, "start-inprog-1", l2.entitlementId(), null);
        assertThat(first.getResponse().getStatus()).isEqualTo(202);
        UUID inProgId = UUID.fromString(json(first).path("attemptId").asText());
        awaitTerminalStartState(inProgId, AttemptState.IN_PROGRESS);

        MvcResult resumeInProg = performStart(user2, "start-inprog-2", l2.entitlementId(), null);
        assertThat(resumeInProg.getResponse().getStatus()).isEqualTo(200);
        JsonNode ripJson = json(resumeInProg);
        assertThat(ripJson.path("attemptId").asText()).isEqualTo(inProgId.toString());
        assertThat(ripJson.path("state").asText()).isEqualTo("IN_PROGRESS");
        assertThat(ripJson.path("resumed").asBoolean()).isTrue();
        assertThat(countEntitlementUsageForGrant(l2.grantId(), "RESERVED")).isEqualTo(1);
    }

    @Test
    void startedAtIsWhenPaperSaved() throws Exception {
        UUID userId = createStudent("clock-save");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());

        CountDownLatch solverEntered = new CountDownLatch(1);
        CountDownLatch solverRelease = new CountDownLatch(1);
        doAnswer(
                        inv -> {
                            solverEntered.countDown();
                            assertThat(solverRelease.await(15, TimeUnit.SECONDS)).isTrue();
                            return inv.callRealMethod();
                        })
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());

        Instant clickTime = mutableClock.instant();
        MvcResult startRes = performStart(userId, "clock-key-1", line.entitlementId(), null);
        assertThat(startRes.getResponse().getStatus()).isEqualTo(202);
        UUID attemptId = UUID.fromString(json(startRes).path("attemptId").asText());

        assertThat(solverEntered.await(10, TimeUnit.SECONDS)).isTrue();
        mutableClock.advance(Duration.ofMinutes(5));
        Instant expectedSaveAnchor = mutableClock.instant();
        solverRelease.countDown();

        Attempt opened = awaitTerminalStartState(attemptId, AttemptState.IN_PROGRESS);
        assertThat(opened.getStartedAt()).isAfterOrEqualTo(expectedSaveAnchor.minusSeconds(1));
        assertThat(opened.getStartedAt()).isAfter(clickTime.plus(Duration.ofMinutes(4)));

        var parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId);
        assertThat(parts).isNotEmpty();
        assertThat(parts.getFirst().getStartedAt()).isEqualTo(opened.getStartedAt());
        if (parts.getFirst().getDurationMinutes() != null) {
            assertThat(parts.getFirst().getDeadlineAt())
                    .isEqualTo(
                            opened.getStartedAt()
                                    .plus(Duration.ofMinutes(parts.getFirst().getDurationMinutes())));
        }
    }

    @Test
    void noAttemptsLeftIsRejected() throws Exception {
        UUID userId = createStudent("exhausted");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 1, List.of());

        MvcResult okRes = performStart(userId, "ex-1", line.entitlementId(), null);
        assertThat(okRes.getResponse().getStatus()).isEqualTo(202);
        UUID firstId = UUID.fromString(json(okRes).path("attemptId").asText());
        awaitTerminalStartState(firstId, AttemptState.IN_PROGRESS);

        // Force first attempt closed so no open attempt blocks reaching reserve()
        jdbc.update(
                "UPDATE attempt.attempt SET state = 'SCORED', finished_at = now() WHERE id = ?",
                firstId);

        MvcResult denied = performStart(userId, "ex-2", line.entitlementId(), null);
        assertThat(denied.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(denied).path("code").asText()).isEqualTo("NO_ATTEMPTS_LEFT");
        Integer count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM attempt.attempt WHERE user_id = ? AND exam_id = ?",
                        Integer.class,
                        userId,
                        line.examId());
        assertThat(count).isEqualTo(1);
    }

    @Test
    void discontinuedExamCanStart() throws Exception {
        UUID userId = createStudent("discontinued");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-MATH-DISCONTINUED", 2, List.of());

        MvcResult res = performStart(userId, "disc-1", line.entitlementId(), null);
        assertThat(res.getResponse().getStatus()).isEqualTo(202);
        UUID attemptId = UUID.fromString(json(res).path("attemptId").asText());
        Attempt opened = awaitTerminalStartState(attemptId, AttemptState.IN_PROGRESS);
        assertThat(opened.getState()).isEqualTo(AttemptState.IN_PROGRESS);
    }

    @Test
    void versionFollowsChosenPath() throws Exception {
        UUID examId = AttemptTestFixtures.dsExamId(jdbc, "DS-SPT-INFORMATICS-MINI");
        ServingExam serving = publishedExamPort.findServing(examId).orElseThrow();
        String csPathId = pathIdForCode(serving, "CS");
        String ictPathId = pathIdForCode(serving, "ICT");

        String[] origV1Serving =
                jdbc.queryForObject(
                        "SELECT serving_path_ids FROM exam.exam_version WHERE exam_id = ? AND version_no = 1",
                        (rs, rn) -> (String[]) rs.getArray(1).getArray(),
                        examId);

        UUID s1 = createStudent("ver-cs");
        UUID s2 = createStudent("ver-ict");
        try {
            jdbc.update(
                    "INSERT INTO exam.exam_version (id, exam_id, version_no, config, parts, published_path_ids, serving_path_ids, check_run_id, state, status, published_at, published_by, note) "
                            + "SELECT gen_random_uuid(), exam_id, 2, config, parts, published_path_ids, ARRAY[?]::text[], check_run_id, 'SERVING', status, now(), published_by, 'v2 test' "
                            + "FROM exam.exam_version WHERE exam_id = ? AND version_no = 1",
                    ictPathId,
                    examId);
            jdbc.update(
                    "UPDATE exam.exam_version SET serving_path_ids = ARRAY[?]::text[] WHERE exam_id = ? AND version_no = 1",
                    csPathId,
                    examId);

            GrantOrderResult.Line l1 = grant(s1, "DS-SPT-INFORMATICS-MINI", 2, List.of());
            GrantOrderResult.Line l2 = grant(s2, "DS-SPT-INFORMATICS-MINI", 2, List.of());

            MvcResult r1 =
                    performStart(s1, "v-cs", l1.entitlementId(), new PreChoiceInput("CS", null));
            assertThat(r1.getResponse().getStatus()).isEqualTo(202);
            Attempt a1 =
                    awaitTerminalStartState(
                            UUID.fromString(json(r1).path("attemptId").asText()),
                            AttemptState.IN_PROGRESS);
            assertThat(a1.getExamVersionNo()).isEqualTo(1);

            MvcResult r2 =
                    performStart(s2, "v-ict", l2.entitlementId(), new PreChoiceInput("ICT", null));
            assertThat(r2.getResponse().getStatus()).isEqualTo(202);
            Attempt a2 =
                    awaitTerminalStartState(
                            UUID.fromString(json(r2).path("attemptId").asText()),
                            AttemptState.IN_PROGRESS);
            assertThat(a2.getExamVersionNo()).isEqualTo(2);
        } finally {
            jdbc.update("DELETE FROM attempt.attempt WHERE user_id IN (?, ?)", s1, s2);
            jdbc.update(
                    "UPDATE exam.exam_version SET serving_path_ids = ? WHERE exam_id = ? AND version_no = 1",
                    origV1Serving,
                    examId);
            jdbc.update("DELETE FROM exam.exam_version WHERE exam_id = ? AND version_no = 2", examId);
        }
    }

    @Test
    void runningAttemptKeepsIssuedPaper() throws Exception {
        UUID userId = createStudent("keep-paper");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());

        MvcResult res = performStart(userId, "kp-1", line.entitlementId(), null);
        assertThat(res.getResponse().getStatus()).isEqualTo(202);
        UUID attemptId = UUID.fromString(json(res).path("attemptId").asText());
        Attempt opened = awaitTerminalStartState(attemptId, AttemptState.IN_PROGRESS);
        assertThat(opened.getExamVersionNo()).isEqualTo(1);
        List<AttemptItem> itemsBefore = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attemptId);

        try {
            jdbc.update(
                    "INSERT INTO exam.exam_version (id, exam_id, version_no, config, parts, published_path_ids, serving_path_ids, check_run_id, state, status, published_at, published_by, note) "
                            + "SELECT gen_random_uuid(), exam_id, 2, config, parts, published_path_ids, serving_path_ids, check_run_id, 'SERVING', status, now(), published_by, 'v2 test' "
                            + "FROM exam.exam_version WHERE exam_id = ? AND version_no = 1",
                    line.examId());

            MvcResult stateRes = performGetState(userId, attemptId);
            assertThat(stateRes.getResponse().getStatus()).isEqualTo(200);
            JsonNode state = json(stateRes);
            assertThat(state.path("examVersionNo").asInt()).isEqualTo(1);
            List<AttemptItem> itemsAfter =
                    attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attemptId);
            assertThat(itemsAfter).hasSameSizeAs(itemsBefore);
            assertThat(itemsAfter.stream().map(AttemptItem::getQuestionId).toList())
                    .isEqualTo(itemsBefore.stream().map(AttemptItem::getQuestionId).toList());
        } finally {
            jdbc.update("DELETE FROM attempt.attempt WHERE id = ?", attemptId);
            jdbc.update(
                    "DELETE FROM exam.exam_version WHERE exam_id = ? AND version_no = 2",
                    line.examId());
        }
    }

    @Test
    void optionOrderStoredWhenKept() throws Exception {
        UUID adminId = UUID.randomUUID();
        SaveQuestionRequest createReq =
                new SaveQuestionRequest(
                        QuestionType.SINGLE_CHOICE,
                        "TOAN",
                        "G12",
                        "TH",
                        null,
                        null,
                        BigDecimal.ONE,
                        List.of(),
                        List.of(),
                        "<p>Keep option order question</p>",
                        null,
                        List.of(),
                        List.of(),
                        new QuestionBody.SingleChoiceBody(
                                QuestionType.SINGLE_CHOICE,
                                List.of(
                                        new QuestionBody.ChoiceOption("opt-a", "<p>A</p>", true),
                                        new QuestionBody.ChoiceOption("opt-b", "<p>B</p>", false),
                                        new QuestionBody.ChoiceOption("opt-c", "<p>C</p>", false),
                                        new QuestionBody.ChoiceOption("opt-d", "<p>D</p>", false),
                                        new QuestionBody.ChoiceOption("opt-e", "<p>E</p>", false)),
                                false),
                        "KEEP-ORDER-" + UUID.randomUUID().toString().substring(0, 6));

        MvcResult qCreateRes =
                mockMvc.perform(
                                post("/api/v1/admin/questions")
                                        .with(asUser(adminId, "q-author", Permissions.QUESTION_MODIFY))
                                        .header("Idempotency-Key", "create-keep-" + UUID.randomUUID())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(createReq)))
                        .andReturn();
        assertThat(qCreateRes.getResponse().getStatus()).isBetween(200, 201);
        UUID questionId = UUID.fromString(json(qCreateRes).path("summary").path("id").asText());

        MvcResult readyRes =
                mockMvc.perform(
                                post("/api/v1/admin/questions/bulk/ready")
                                        .with(asUser(adminId, "q-author", Permissions.QUESTION_MODIFY))
                                        .header("Idempotency-Key", "ready-keep-" + UUID.randomUUID())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        new BulkQuestionStatusRequest(List.of(questionId)))))
                        .andReturn();
        assertThat(readyRes.getResponse().getStatus()).isEqualTo(200);

        UUID userId = createStudent("keep-opt");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-REVIEW-MINI", 2, List.of());
        ServingExam serving = publishedExamPort.findServing(line.examId()).orElseThrow();
        String partId = serving.version().parts().getFirst().id();
        String blockId = "B1";

        doReturn(
                        new GeneratedPaper(
                                List.of(
                                        new GeneratedPart(
                                                partId,
                                                List.of(
                                                        new GeneratedSection(
                                                                "S1",
                                                                "TOAN",
                                                                1,
                                                                List.of(
                                                                        new GeneratedGroup(
                                                                                blockId,
                                                                                List.of(
                                                                                        new GeneratedQuestion(
                                                                                                questionId, 1)),
                                                                                false)))))),
                                List.of()))
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());

        MvcResult startRes = performStart(userId, "keep-opt-key", line.entitlementId(), null);
        assertThat(startRes.getResponse().getStatus()).isEqualTo(202);
        UUID attemptId = UUID.fromString(json(startRes).path("attemptId").asText());
        awaitTerminalStartState(attemptId, AttemptState.IN_PROGRESS);

        List<AttemptItem> items = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attemptId);
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().getOptionOrder())
                .containsExactly("opt-a", "opt-b", "opt-c", "opt-d", "opt-e");

        JsonNode state = json(performGetState(userId, attemptId));
        JsonNode options = state.path("parts").get(0).path("items").get(0).path("questionBody").path("options");
        List<String> renderedIds = new ArrayList<>();
        options.forEach(o -> renderedIds.add(o.path("id").asText()));
        assertThat(renderedIds).containsExactly("opt-a", "opt-b", "opt-c", "opt-d", "opt-e");
    }

    @Test
    void preChoiceOutsideScopeIsRejected() throws Exception {
        UUID userId = createStudent("bad-choice");
        GrantOrderResult.Line sptCsLine = grant(userId, "DS-SPT-INFORMATICS-MINI", 2, List.of("CS"));

        // ICT outside purchased scope ["CS"] -> 422 CHOICE_NOT_ALLOWED
        MvcResult outsideOpt =
                performStart(
                        userId,
                        "bad-opt-1",
                        sptCsLine.entitlementId(),
                        new PreChoiceInput("ICT", null));
        assertThat(outsideOpt.getResponse().getStatus()).isEqualTo(422);
        assertThat(json(outsideOpt).path("code").asText()).isEqualTo("CHOICE_NOT_ALLOWED");

        // Missing preChoice when required -> 422 CHOICE_NOT_ALLOWED
        MvcResult missingPre = performStart(userId, "bad-opt-2", sptCsLine.entitlementId(), null);
        assertThat(missingPre.getResponse().getStatus()).isEqualTo(422);
        assertThat(json(missingPre).path("code").asText()).isEqualTo("CHOICE_NOT_ALLOWED");

        // Unexpected subjectCodes on non-SUBJECT_CHOICE branch -> 422 SUBJECTS_INVALID
        MvcResult unexpectedSubjects =
                performStart(
                        userId,
                        "bad-opt-3",
                        sptCsLine.entitlementId(),
                        new PreChoiceInput("CS", List.of("TOAN")));
        assertThat(unexpectedSubjects.getResponse().getStatus()).isEqualTo(422);
        assertThat(json(unexpectedSubjects).path("code").asText()).isEqualTo("SUBJECTS_INVALID");

        // QDA_PART_SCIENCE outside purchased scope ["QD01"] -> 422 CHOICE_NOT_ALLOWED
        UUID qdaUser = createStudent("bad-subj");
        GrantOrderResult.Line qdaLine = grant(qdaUser, "DS-QDA-PART-SCIENCE-MINI", 2, List.of("QD01"));
        MvcResult badQda =
                performStart(
                        qdaUser,
                        "bad-qda-1",
                        qdaLine.entitlementId(),
                        new PreChoiceInput("QD02", null));
        assertThat(badQda.getResponse().getStatus()).isEqualTo(422);
        assertThat(json(badQda).path("code").asText()).isEqualTo("CHOICE_NOT_ALLOWED");
    }

    @Test
    void emptyServingIntersectionIsRejected() throws Exception {
        UUID userId = createStudent("empty-inter");
        UUID examId = AttemptTestFixtures.dsExamId(jdbc, "DS-QDA-PART-SCIENCE-MINI");
        GrantOrderResult.Line line = grant(userId, "DS-QDA-PART-SCIENCE-MINI", 2, List.of("QD01"));

        String[] origServing =
                jdbc.queryForObject(
                        "SELECT serving_path_ids FROM exam.exam_version WHERE exam_id = ? AND version_no = 1",
                        (rs, rn) -> (String[]) rs.getArray(1).getArray(),
                        examId);
        ServingExam serving = publishedExamPort.findServing(examId).orElseThrow();
        String nonQd01Path =
                serving.servingPaths().stream()
                        .filter(sp -> sp.choices().stream().noneMatch(c -> "QD01".equals(c.code())))
                        .findFirst()
                        .orElseThrow()
                        .pathId();
        try {
            jdbc.update(
                    "UPDATE exam.exam_version SET serving_path_ids = ARRAY[?]::text[] WHERE exam_id = ? AND version_no = 1",
                    nonQd01Path,
                    examId);
            MvcResult res =
                    performStart(
                            userId,
                            "empty-inter-key",
                            line.entitlementId(),
                            new PreChoiceInput("QD01", null));
            assertThat(res.getResponse().getStatus()).isEqualTo(409);
            assertThat(json(res).path("code").asText()).isEqualTo("NO_SERVING_OPTION");
        } finally {
            jdbc.update(
                    "UPDATE exam.exam_version SET serving_path_ids = ? WHERE exam_id = ? AND version_no = 1",
                    origServing,
                    examId);
        }
    }

    @Test
    void replaySameKeyReturnsCurrentState() throws Exception {
        UUID userId = createStudent("replay-same");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 3, List.of());

        MvcResult first = performStart(userId, "idem-fixed-1", line.entitlementId(), null);
        assertThat(first.getResponse().getStatus()).isEqualTo(202);
        UUID attemptId = UUID.fromString(json(first).path("attemptId").asText());
        awaitTerminalStartState(attemptId, AttemptState.IN_PROGRESS);

        MvcResult replay = performStart(userId, "idem-fixed-1", line.entitlementId(), null);
        assertThat(replay.getResponse().getStatus()).isEqualTo(200);
        JsonNode replayBody = json(replay);
        assertThat(replayBody.path("attemptId").asText()).isEqualTo(attemptId.toString());
        assertThat(replayBody.path("state").asText()).isEqualTo("IN_PROGRESS");
        assertThat(replayBody.path("resumed").asBoolean()).isFalse();
        assertThat(countEntitlementUsageForGrant(line.grantId(), "RESERVED")).isEqualTo(1);
    }

    @Test
    void sameKeyDifferentBodyConflicts() throws Exception {
        UUID userId = createStudent("idem-diff");
        GrantOrderResult.Line line1 = grant(userId, "DS-SPT-INFORMATICS-MINI", 3, List.of());
        GrantOrderResult.Line line2 = grant(userId, "DS-HSA-PART-MATH-MINI", 3, List.of());

        MvcResult first =
                performStart(
                        userId, "shared-key", line1.entitlementId(), new PreChoiceInput("CS", null));
        assertThat(first.getResponse().getStatus()).isEqualTo(202);
        awaitTerminalStartState(
                UUID.fromString(json(first).path("attemptId").asText()), AttemptState.IN_PROGRESS);

        MvcResult diffPreChoice =
                performStart(
                        userId, "shared-key", line1.entitlementId(), new PreChoiceInput("ICT", null));
        assertThat(diffPreChoice.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(diffPreChoice).path("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");

        MvcResult diffEntitlement =
                performStart(userId, "shared-key", line2.entitlementId(), null);
        assertThat(diffEntitlement.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(diffEntitlement).path("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void missingKeyIsRejected() throws Exception {
        UUID userId = createStudent("missing-key");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());

        MvcResult missing = performStart(userId, null, line.entitlementId(), null);
        assertThat(missing.getResponse().getStatus()).isEqualTo(400);

        MvcResult blank = performStart(userId, "   ", line.entitlementId(), null);
        assertThat(blank.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void newKeyAfterAbortStartsNewAttempt() throws Exception {
        UUID userId = createStudent("after-abort");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());

        UUID abortedId = Ids.newId();
        ServingExam serving = publishedExamPort.findServing(line.examId()).orElseThrow();
        PublishedVersion v = serving.version();
        entitlementPort.reserve(userId, line.entitlementId(), abortedId);
        Attempt aborted =
                Attempt.start(
                        abortedId,
                        userId,
                        line.examId(),
                        v.versionNo(),
                        line.entitlementId(),
                        line.grantId(),
                        v.name(),
                        v.kindItemId(),
                        v.kindCode(),
                        v.scope(),
                        v.partCode(),
                        v.subjectItemId(),
                        AttemptTiming.valueOf(v.timing()),
                        NavigationMode.SEQUENTIAL,
                        Map.of(),
                        101L,
                        Ids.newId(),
                        "SCORE_AND_ANSWERS",
                        v.practiceScoring(),
                        v.referenceMax(),
                        v.maxScore(),
                        "aborted-key-1",
                        mutableClock.instant());
        aborted.abort(AbortReason.GENERATION_FAILED, mutableClock.instant());
        attemptRepository.saveAndFlush(aborted);
        entitlementPort.release(abortedId);

        MvcResult retry = performStart(userId, "retry-key-2", line.entitlementId(), null);
        assertThat(retry.getResponse().getStatus()).isEqualTo(202);
        UUID newId = UUID.fromString(json(retry).path("attemptId").asText());
        assertThat(newId).isNotEqualTo(abortedId);
        Attempt opened = awaitTerminalStartState(newId, AttemptState.IN_PROGRESS);
        assertThat(opened.getAttemptNo()).isEqualTo(1);
    }

    @Test
    void attemptNoSkipsAborted() throws Exception {
        UUID userId = createStudent("attempt-no-seq");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 3, List.of());
        ServingExam serving = publishedExamPort.findServing(line.examId()).orElseThrow();
        PublishedVersion v = serving.version();

        UUID abortedId = Ids.newId();
        Attempt aborted =
                Attempt.start(
                        abortedId,
                        userId,
                        line.examId(),
                        v.versionNo(),
                        line.entitlementId(),
                        line.grantId(),
                        v.name(),
                        v.kindItemId(),
                        v.kindCode(),
                        v.scope(),
                        v.partCode(),
                        v.subjectItemId(),
                        AttemptTiming.valueOf(v.timing()),
                        NavigationMode.SEQUENTIAL,
                        Map.of(),
                        55L,
                        Ids.newId(),
                        "SCORE_AND_ANSWERS",
                        v.practiceScoring(),
                        v.referenceMax(),
                        v.maxScore(),
                        "skip-abort-seed",
                        mutableClock.instant());
        aborted.abort(AbortReason.STALE_STARTING, mutableClock.instant());
        attemptRepository.saveAndFlush(aborted);
        assertThat(attemptRepository.findById(abortedId).orElseThrow().getAttemptNo()).isNull();

        MvcResult r1 = performStart(userId, "seq-key-1", line.entitlementId(), null);
        UUID id1 = UUID.fromString(json(r1).path("attemptId").asText());
        Attempt a1 = awaitTerminalStartState(id1, AttemptState.IN_PROGRESS);
        assertThat(a1.getAttemptNo()).isEqualTo(1);

        jdbc.update(
                "UPDATE attempt.attempt SET state = 'SCORED', finished_at = now() WHERE id = ?",
                id1);

        MvcResult r2 = performStart(userId, "seq-key-2", line.entitlementId(), null);
        UUID id2 = UUID.fromString(json(r2).path("attemptId").asText());
        Attempt a2 = awaitTerminalStartState(id2, AttemptState.IN_PROGRESS);
        assertThat(a2.getAttemptNo()).isEqualTo(2);
    }

    @Test
    void usageRecordedWithPaperAndUsedQuestionCannotBeDeleted() throws Exception {
        UUID userId = createStudent("usage-ledger");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());

        MvcResult res = performStart(userId, "usage-key-1", line.entitlementId(), null);
        assertThat(res.getResponse().getStatus()).isEqualTo(202);
        UUID attemptId = UUID.fromString(json(res).path("attemptId").asText());
        awaitTerminalStartState(attemptId, AttemptState.IN_PROGRESS);

        List<AttemptItem> items = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attemptId);
        AttemptItem rootItem = items.stream().filter(i -> i.getParentItemId() == null).findFirst().orElseThrow();

        Integer ledgerCount =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM questionbank.question_usage_ledger "
                                + "WHERE question_id = ? AND version_no = ? AND kind = 'ATTEMPT' AND exam_id = ?",
                        Integer.class,
                        rootItem.getQuestionId(),
                        rootItem.getQuestionVersionNo(),
                        line.examId());
        assertThat(ledgerCount).isEqualTo(1);

        Long revision =
                jdbc.queryForObject(
                        "SELECT revision FROM questionbank.question WHERE id = ?",
                        Long.class,
                        rootItem.getQuestionId());
        MvcResult delRes =
                mockMvc.perform(
                                delete("/api/v1/admin/questions/{id}", rootItem.getQuestionId())
                                        .param("expectedRevision", String.valueOf(revision))
                                        .header("Idempotency-Key", "del-used-" + UUID.randomUUID())
                                        .with(asUser(UUID.randomUUID(), "q-admin", Permissions.QUESTION_MODIFY)))
                        .andReturn();
        assertThat(delRes.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(delRes).path("code").asText()).isEqualTo("QUESTION_IN_USE");
    }

    @Test
    void concurrentSavesOnSameExamFinish() throws Exception {
        int n = 5;
        List<UUID> users = new ArrayList<>();
        List<GrantOrderResult.Line> lines = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            UUID u = createStudent("conc-save-" + i);
            users.add(u);
            lines.add(grant(u, "DS-HSA-PART-MATH-MINI", 2, List.of()));
        }

        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch fire = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                final int idx = i;
                futures.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    assertThat(fire.await(10, TimeUnit.SECONDS)).isTrue();
                                    return performStart(
                                            users.get(idx),
                                            "conc-save-key-" + idx,
                                            lines.get(idx).entitlementId(),
                                            null);
                                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            fire.countDown();

            AtomicInteger openCount = new AtomicInteger();
            for (Future<MvcResult> f : futures) {
                MvcResult r = f.get(20, TimeUnit.SECONDS);
                assertThat(r.getResponse().getStatus()).isEqualTo(202);
                UUID id = UUID.fromString(json(r).path("attemptId").asText());
                Attempt opened = awaitTerminalStartState(id, AttemptState.IN_PROGRESS);
                assertThat(attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(id))
                        .isNotEmpty();
                openCount.incrementAndGet();
            }
            assertThat(openCount.get()).isEqualTo(n);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void authAndAccessMatrixEnforced() throws Exception {
        UUID owner = createStudent("auth-owner");
        UUID stranger = createStudent("auth-stranger");
        UUID manager = createManager("auth-manager");
        GrantOrderResult.Line ownerLine = grant(owner, "DS-HSA-PART-MATH-MINI", 2, List.of());

        MvcResult ownerStart = performStart(owner, "owner-start", ownerLine.entitlementId(), null);
        assertThat(ownerStart.getResponse().getStatus()).isEqualTo(202);
        UUID attemptId = UUID.fromString(json(ownerStart).path("attemptId").asText());
        awaitTerminalStartState(attemptId, AttemptState.IN_PROGRESS);

        // Anonymous -> 401
        assertThat(
                        mockMvc.perform(
                                        post("/api/v1/me/attempts")
                                                .header("Idempotency-Key", "anon-1")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        "{\"entitlementId\":\""
                                                                + ownerLine.entitlementId()
                                                                + "\"}"))
                                .andReturn()
                                .getResponse()
                                .getStatus())
                .isEqualTo(401);
        assertThat(mockMvc.perform(get("/api/v1/me/attempts/{id}", attemptId)).andReturn().getResponse().getStatus())
                .isEqualTo(401);

        // MANAGE account -> 403 ATTEMPT_ACCESS_FORBIDDEN
        MvcResult mgrStart =
                mockMvc.perform(
                                post("/api/v1/me/attempts")
                                        .with(AttemptTestFixtures.asManageUser(manager, "mgr"))
                                        .header("Idempotency-Key", "mgr-1")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"entitlementId\":\""
                                                        + ownerLine.entitlementId()
                                                        + "\"}"))
                        .andReturn();
        assertThat(mgrStart.getResponse().getStatus()).isEqualTo(403);
        assertThat(json(mgrStart).path("code").asText()).isEqualTo("ATTEMPT_ACCESS_FORBIDDEN");

        MvcResult mgrGet =
                mockMvc.perform(
                                get("/api/v1/me/attempts/{id}", attemptId)
                                        .with(AttemptTestFixtures.asManageUser(manager, "mgr")))
                        .andReturn();
        assertThat(mgrGet.getResponse().getStatus()).isEqualTo(403);

        // Stranger learner -> 404 on start (other user's entitlement) and 404 on state
        MvcResult strangerStart = performStart(stranger, "stranger-1", ownerLine.entitlementId(), null);
        assertThat(strangerStart.getResponse().getStatus()).isEqualTo(404);
        assertThat(performGetState(stranger, attemptId).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void kindAndNavigationAreSnapshotted() throws Exception {
        UUID userId = createStudent("kind-nav-snap");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-PART-MATH-MINI", 2, List.of());

        MvcResult res = performStart(userId, "snap-1", line.entitlementId(), null);
        assertThat(res.getResponse().getStatus()).isEqualTo(202);
        UUID attemptId = UUID.fromString(json(res).path("attemptId").asText());
        Attempt opened = awaitTerminalStartState(attemptId, AttemptState.IN_PROGRESS);
        assertThat(opened.getExamKindCode()).isEqualTo("HSA");
        assertThat(opened.getNavigationMode()).isEqualTo(NavigationMode.SEQUENTIAL);

        UUID kindItemId = opened.getExamKindItemId();
        String origCode =
                jdbc.queryForObject(
                        "SELECT code FROM catalog.catalog_item WHERE id = ?",
                        String.class,
                        kindItemId);
        try {
            jdbc.update(
                    "UPDATE catalog.catalog_item SET code = ? WHERE id = ?",
                    "HSA_CHANGED",
                    kindItemId);
            JsonNode state = json(performGetState(userId, attemptId));
            assertThat(state.path("examKindCode").asText()).isEqualTo("HSA");
            assertThat(state.path("navigationMode").asText()).isEqualTo("SEQUENTIAL");
        } finally {
            jdbc.update("UPDATE catalog.catalog_item SET code = ? WHERE id = ?", origCode, kindItemId);
        }
    }

    private static String pathIdForCode(ServingExam serving, String code) {
        return serving.servingPaths().stream()
                .filter(sp -> sp.choices().stream().anyMatch(c -> code.equalsIgnoreCase(c.code())))
                .map(ServingPath::pathId)
                .findFirst()
                .orElseThrow();
    }
}
