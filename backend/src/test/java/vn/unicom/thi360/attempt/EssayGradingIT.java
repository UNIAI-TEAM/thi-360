package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.api.EssayCriterionInput;
import vn.unicom.thi360.attempt.api.FinalizeEssayRequest;
import vn.unicom.thi360.attempt.api.FinalAnswerChange;
import vn.unicom.thi360.attempt.api.FinishPartRequest;
import vn.unicom.thi360.attempt.api.ReopenEssayRequest;
import vn.unicom.thi360.attempt.api.SaveEssayDraftRequest;
import vn.unicom.thi360.attempt.api.StartAttemptRequest.PreChoiceInput;
import vn.unicom.thi360.attempt.api.SubmitAttemptRequest;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.EssayGrade;
import vn.unicom.thi360.attempt.domain.EssayGradeState;
import vn.unicom.thi360.attempt.domain.ItemOutcome;
import vn.unicom.thi360.attempt.infrastructure.EssayGradeRepository;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.outbox.OutboxRelay;

class EssayGradingIT extends AbstractAttemptDsIT {

    @Autowired private OutboxRelay outboxRelay;
    @Autowired private EssayGradeRepository essayGradeRepository;
    @Autowired private QuestionSourcePort questionSourcePort;

    @BeforeEach
    void drainBefore() {
        drainAllOutbox();
    }

    @Test
    void claimAfterThirtyIdleMinutes() throws Exception {
        UUID student = createStudent("essay-30m-student");
        UUID graderA = createGrader("grader-a", "Giám khảo An");
        UUID graderB = createGrader("grader-b", "Giám khảo Bình");

        EssaySubmission sub = submitSingleBcaEssay(student, "<p>Bài làm tự luận đầy đủ nội dung</p>");

        MvcResult claimA = performClaim(graderA, sub.essayItemId());
        assertThat(claimA.getResponse().getStatus()).isEqualTo(200);
        JsonNode claimedA = json(claimA);
        assertThat(claimedA.path("state").asText()).isEqualTo("DRAFT");
        assertThat(claimedA.path("graderId").asText()).isEqualTo(graderA.toString());
        assertThat(claimedA.path("graderName").asText()).isEqualTo("Giám khảo An");
        UUID firstGradeId = UUID.fromString(claimedA.path("id").asText());

        // Exactly 30 minutes: must STILL be IN_PROGRESS (claimExpired=false) in list/counts/detail and reject takeover (AC-2, R1-13)
        mutableClock.advance(Duration.ofMinutes(30));
        JsonNode inProgAt30 = json(mockMvc.perform(
                        get("/api/v1/admin/grading/items")
                                .param("state", "IN_PROGRESS")
                                .param("exam", sub.examId().toString())
                                .with(asUser(graderB, "grader.b", Permissions.GRADING_VIEW)))
                .andReturn());
        assertThat(inProgAt30.path("items")).hasSize(1);
        assertThat(inProgAt30.path("items").get(0).path("itemId").asText()).isEqualTo(sub.essayItemId().toString());
        assertThat(inProgAt30.path("items").get(0).path("state").asText()).isEqualTo("IN_PROGRESS");
        assertThat(inProgAt30.path("items").get(0).path("claimExpired").asBoolean()).isFalse();

        JsonNode waitingAt30 = json(mockMvc.perform(
                        get("/api/v1/admin/grading/items")
                                .param("state", "WAITING")
                                .param("exam", sub.examId().toString())
                                .with(asUser(graderB, "grader.b", Permissions.GRADING_VIEW)))
                .andReturn());
        assertThat(waitingAt30.path("items")).isEmpty();

        JsonNode countsAt30 = json(mockMvc.perform(
                        get("/api/v1/admin/grading/items/counts")
                                .param("exam", sub.examId().toString())
                                .with(asUser(graderB, "grader.b", Permissions.GRADING_VIEW)))
                .andReturn());
        assertThat(countsAt30.path("waiting").asLong()).isEqualTo(0L);
        assertThat(countsAt30.path("inProgress").asLong()).isEqualTo(1L);
        assertThat(countsAt30.path("done").asLong()).isEqualTo(0L);
        assertThat(countsAt30.path("total").asLong()).isEqualTo(1L);

        JsonNode detailAt30 = json(performGetDetail(graderB, sub.essayItemId()));
        assertThat(detailAt30.path("state").asText()).isEqualTo("IN_PROGRESS");
        assertThat(detailAt30.path("activeGrade").path("claimExpired").asBoolean()).isFalse();

        MvcResult claimBAt30 = performClaim(graderB, sub.essayItemId());
        assertThat(claimBAt30.getResponse().getStatus()).isEqualTo(409);
        JsonNode conflictAt30 = json(claimBAt30);
        assertThat(conflictAt30.path("code").asText()).isEqualTo("GRADING_CLAIMED");
        assertThat(conflictAt30.path("claimedById").asText()).isEqualTo(graderA.toString());
        assertThat(conflictAt30.path("claimedByName").asText()).isEqualTo("Giám khảo An");
        assertThat(conflictAt30.path("claimedAt").asText()).isNotBlank();
        assertThat(conflictAt30.path("lastActivityAt").asText()).isNotBlank();

        // 30 minutes + 1 second: queue/counts/detail move item back to WAITING (claimExpired=true) before takeover succeeds (R1-13)
        mutableClock.advance(Duration.ofSeconds(1));
        JsonNode waitingAt30m1s = json(mockMvc.perform(
                        get("/api/v1/admin/grading/items")
                                .param("state", "WAITING")
                                .param("exam", sub.examId().toString())
                                .with(asUser(graderB, "grader.b", Permissions.GRADING_VIEW)))
                .andReturn());
        assertThat(waitingAt30m1s.path("items")).hasSize(1);
        assertThat(waitingAt30m1s.path("items").get(0).path("itemId").asText()).isEqualTo(sub.essayItemId().toString());
        assertThat(waitingAt30m1s.path("items").get(0).path("state").asText()).isEqualTo("WAITING");
        assertThat(waitingAt30m1s.path("items").get(0).path("claimExpired").asBoolean()).isTrue();

        JsonNode inProgAt30m1s = json(mockMvc.perform(
                        get("/api/v1/admin/grading/items")
                                .param("state", "IN_PROGRESS")
                                .param("exam", sub.examId().toString())
                                .with(asUser(graderB, "grader.b", Permissions.GRADING_VIEW)))
                .andReturn());
        assertThat(inProgAt30m1s.path("items")).isEmpty();

        JsonNode countsAt30m1s = json(mockMvc.perform(
                        get("/api/v1/admin/grading/items/counts")
                                .param("exam", sub.examId().toString())
                                .with(asUser(graderB, "grader.b", Permissions.GRADING_VIEW)))
                .andReturn());
        assertThat(countsAt30m1s.path("waiting").asLong()).isEqualTo(1L);
        assertThat(countsAt30m1s.path("inProgress").asLong()).isEqualTo(0L);
        assertThat(countsAt30m1s.path("done").asLong()).isEqualTo(0L);
        assertThat(countsAt30m1s.path("total").asLong()).isEqualTo(1L);

        JsonNode detailAt30m1s = json(performGetDetail(graderB, sub.essayItemId()));
        assertThat(detailAt30m1s.path("state").asText()).isEqualTo("WAITING");
        assertThat(detailAt30m1s.path("activeGrade").path("claimExpired").asBoolean()).isTrue();

        MvcResult claimBAt30m1s = performClaim(graderB, sub.essayItemId());
        assertThat(claimBAt30m1s.getResponse().getStatus()).isEqualTo(200);
        JsonNode taken = json(claimBAt30m1s);
        assertThat(taken.path("graderId").asText()).isEqualTo(graderB.toString());
        assertThat(taken.path("graderName").asText()).isEqualTo("Giám khảo Bình");
        assertThat(taken.path("state").asText()).isEqualTo("DRAFT");
        UUID secondGradeId = UUID.fromString(taken.path("id").asText());
        assertThat(secondGradeId).isNotEqualTo(firstGradeId);

        EssayGrade supersededOld = essayGradeRepository.findById(firstGradeId).orElseThrow();
        assertThat(supersededOld.getSupersededBy()).isEqualTo(secondGradeId);
    }

    @Test
    void draftRefreshesActivity() throws Exception {
        UUID student = createStudent("essay-refresh-student");
        UUID graderA = createGrader("grader-refresh-a", "Giám khảo Lan");
        UUID graderB = createGrader("grader-refresh-b", "Giám khảo Minh");

        EssaySubmission sub = submitSingleBcaEssay(student, "<p>Bài làm gia hạn nháp</p>");

        JsonNode claimA = json(performClaim(graderA, sub.essayItemId()));
        long v0 = claimA.path("version").asLong();
        Instant initialActivity = Instant.parse(claimA.path("lastActivityAt").asText());

        // Advance 20 minutes and save a draft -> refreshes lastActivityAt
        mutableClock.advance(Duration.ofMinutes(20));
        MvcResult saveRes = performSaveDraft(
                graderA,
                sub.essayItemId(),
                new SaveEssayDraftRequest(null, new BigDecimal("10.00"), "Đang chấm phần mở bài", v0));
        assertThat(saveRes.getResponse().getStatus()).isEqualTo(200);
        Instant refreshedActivity = Instant.parse(json(saveRes).path("lastActivityAt").asText());
        assertThat(refreshedActivity).isEqualTo(initialActivity.plus(Duration.ofMinutes(20)));

        // Advance 25 more minutes (45m since claim, 25m since draft save): still claimed!
        mutableClock.advance(Duration.ofMinutes(25));
        MvcResult rejected = performClaim(graderB, sub.essayItemId());
        assertThat(rejected.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(rejected).path("code").asText()).isEqualTo("GRADING_CLAIMED");

        // Advance 5m + 1s more (30m1s since draft save): takeover succeeds!
        mutableClock.advance(Duration.ofMinutes(5).plusSeconds(1));
        MvcResult accepted = performClaim(graderB, sub.essayItemId());
        assertThat(accepted.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(accepted).path("graderId").asText()).isEqualTo(graderB.toString());
    }

    @Test
    void otherGraderCannotSave() throws Exception {
        UUID student = createStudent("essay-other-student");
        UUID graderA = createGrader("grader-owner", "Giám khảo Chính");
        UUID graderB = createGrader("grader-viewer", "Giám khảo Phụ");

        EssaySubmission sub = submitSingleBcaEssay(student, "<p>Bài làm khóa quyền lưu</p>");

        JsonNode claimA = json(performClaim(graderA, sub.essayItemId()));
        long version = claimA.path("version").asLong();
        assertThat(performSaveDraft(
                graderA,
                sub.essayItemId(),
                new SaveEssayDraftRequest(null, new BigDecimal("12.00"), "Nháp ban đầu", version)).getResponse()
                .getStatus()).isEqualTo(200);

        // Grader B can read detail (with GRADING_VIEW)
        MvcResult viewB = mockMvc.perform(
                        get("/api/v1/admin/grading/items/{itemId}", sub.essayItemId())
                                .with(asUser(graderB, "grader.b", Permissions.GRADING_VIEW)))
                .andReturn();
        assertThat(viewB.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(viewB).path("activeGrade").path("graderName").asText()).isEqualTo("Giám khảo Chính");

        // Grader B cannot save draft even past 30m without claiming
        mutableClock.advance(Duration.ofMinutes(40));
        MvcResult saveB = performSaveDraft(
                graderB,
                sub.essayItemId(),
                new SaveEssayDraftRequest(null, new BigDecimal("5.00"), "Ghi đè", version + 1));
        assertThat(saveB.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(saveB).path("code").asText()).isEqualTo("GRADING_CLAIMED");
        assertThat(json(saveB).path("claimedByName").asText()).isEqualTo("Giám khảo Chính");

        // Grader B cannot finalize either
        MvcResult finalizeB = performFinalize(
                graderB,
                sub.essayItemId(),
                new FinalizeEssayRequest(null, null, new BigDecimal("5.00"), "Ghi đè"));
        assertThat(finalizeB.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(finalizeB).path("code").asText()).isEqualTo("GRADING_CLAIMED");

        EssayGrade unchanged = essayGradeRepository.findByAttemptItemIdAndSupersededByIsNull(sub.essayItemId())
                .orElseThrow();
        assertThat(unchanged.getGraderId()).isEqualTo(graderA);
        assertThat(unchanged.getPoints()).isEqualByComparingTo("12.00");
        assertThat(unchanged.getComment()).isEqualTo("Nháp ban đầu");
    }

    @Test
    void staleVersionRejected() throws Exception {
        UUID student = createStudent("essay-ver-student");
        UUID grader = createGrader("grader-ver", "Giám khảo Phiên Bản");

        EssaySubmission sub = submitSingleBcaEssay(student, "<p>Bài làm kiểm phiên bản</p>");
        JsonNode claim = json(performClaim(grader, sub.essayItemId()));
        long v0 = claim.path("version").asLong();

        MvcResult firstSave = performSaveDraft(
                grader,
                sub.essayItemId(),
                new SaveEssayDraftRequest(null, new BigDecimal("14.00"), "Lần 1", v0));
        assertThat(firstSave.getResponse().getStatus()).isEqualTo(200);
        long v1 = json(firstSave).path("version").asLong();
        assertThat(v1).isGreaterThan(v0);

        // Stale version on PUT -> 412 VERSION_CONFLICT
        MvcResult staleSave = performSaveDraft(
                grader,
                sub.essayItemId(),
                new SaveEssayDraftRequest(null, new BigDecimal("15.00"), "Lần cũ", v0));
        assertThat(staleSave.getResponse().getStatus()).isEqualTo(412);
        assertThat(json(staleSave).path("code").asText()).isEqualTo("VERSION_CONFLICT");

        // Stale version on POST /finalize -> 412 VERSION_CONFLICT
        MvcResult staleFinalize = performFinalize(
                grader,
                sub.essayItemId(),
                new FinalizeEssayRequest(v0, null, new BigDecimal("15.00"), "Hoàn tất cũ"));
        assertThat(staleFinalize.getResponse().getStatus()).isEqualTo(412);
        assertThat(json(staleFinalize).path("code").asText()).isEqualTo("VERSION_CONFLICT");

        // Fresh version succeeds
        MvcResult okFinalize = performFinalize(
                grader,
                sub.essayItemId(),
                new FinalizeEssayRequest(v1, null, new BigDecimal("15.00"), "Hoàn tất mới"));
        assertThat(okFinalize.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void finalizeValidatesPoints() throws Exception {
        UUID student = createStudent("essay-points-student");
        UUID grader = createGrader("grader-points", "Giám khảo Điểm");

        EssaySubmission sub = submitSingleBcaEssay(student, "<p>Bài làm kiểm tra điểm và tiêu chí</p>");
        AttemptItem essayItem = attemptItemRepository.findById(sub.essayItemId()).orElseThrow();
        BigDecimal maxPts = essayItem.getPointsMax();

        // Install a rubric on this issued snapshot temporarily to validate criteria sum & bounds
        var origVersion = questionSourcePort.loadVersions(
                List.of(new VersionKey(essayItem.getQuestionId(), essayItem.getQuestionVersionNo())))
                .getFirst();
        ObjectNode origSnap = origVersion.snapshot().deepCopy();
        ObjectNode patchedSnap = origSnap.deepCopy();
        ArrayNode rubricArr = objectMapper.createArrayNode();
        BigDecimal part1Max = maxPts.multiply(new BigDecimal("0.4"));
        BigDecimal part2Max = maxPts.subtract(part1Max);
        rubricArr.add(objectMapper.createObjectNode().put("id", "c1").put("description", "Mở bài").put("points", part1Max));
        rubricArr.add(objectMapper.createObjectNode().put("id", "c2").put("description", "Thân bài").put("points", part2Max));
        ((ObjectNode) patchedSnap.path("body")).set("rubric", rubricArr);

        try {
            jdbc.update(
                    "UPDATE questionbank.question_version SET snapshot = ?::jsonb WHERE question_id = ? AND version_no = ?",
                    objectMapper.writeValueAsString(patchedSnap),
                    essayItem.getQuestionId(),
                    essayItem.getQuestionVersionNo());

            performClaim(grader, sub.essayItemId());

            // Negative points -> 422 ESSAY_POINTS_OUT_OF_RANGE
            MvcResult negRes = performFinalize(grader, sub.essayItemId(),
                    new FinalizeEssayRequest(null, List.of(
                            new EssayCriterionInput("c1", BigDecimal.ZERO, null),
                            new EssayCriterionInput("c2", BigDecimal.ZERO, null)),
                            new BigDecimal("-0.01"), "Âm"));
            assertThat(negRes.getResponse().getStatus()).isEqualTo(422);
            assertThat(json(negRes).path("code").asText()).isEqualTo("ESSAY_POINTS_OUT_OF_RANGE");

            // Exceeds max -> 422 ESSAY_POINTS_OUT_OF_RANGE
            MvcResult overRes = performFinalize(grader, sub.essayItemId(),
                    new FinalizeEssayRequest(null, List.of(
                            new EssayCriterionInput("c1", part1Max, null),
                            new EssayCriterionInput("c2", part2Max, null)),
                            maxPts.add(new BigDecimal("0.01")), "Vượt"));
            assertThat(overRes.getResponse().getStatus()).isEqualTo(422);
            assertThat(json(overRes).path("code").asText()).isEqualTo("ESSAY_POINTS_OUT_OF_RANGE");

            // Criteria sum off by 0.01 -> 422 ESSAY_CRITERIA_SUM_MISMATCH
            MvcResult sumMismatch = performFinalize(grader, sub.essayItemId(),
                    new FinalizeEssayRequest(null, List.of(
                            new EssayCriterionInput("c1", part1Max.subtract(new BigDecimal("0.01")), null),
                            new EssayCriterionInput("c2", part2Max, null)),
                            maxPts, "Lệch tổng"));
            assertThat(sumMismatch.getResponse().getStatus()).isEqualTo(422);
            assertThat(json(sumMismatch).path("code").asText()).isEqualTo("ESSAY_CRITERIA_SUM_MISMATCH");

            // Missing rubric criterion -> 422 ESSAY_CRITERIA_INVALID
            MvcResult missingCrit = performFinalize(grader, sub.essayItemId(),
                    new FinalizeEssayRequest(null, List.of(
                            new EssayCriterionInput("c1", part1Max, null)),
                            part1Max, "Thiếu tiêu chí"));
            assertThat(missingCrit.getResponse().getStatus()).isEqualTo(422);
            assertThat(json(missingCrit).path("code").asText()).isEqualTo("ESSAY_CRITERIA_INVALID");

            // Single-criterion rubric cap overflow while total sum equals maxPts -> 422 ESSAY_CRITERIA_INVALID (R1-21)
            MvcResult singleCriterionOverflow = performFinalize(grader, sub.essayItemId(),
                    new FinalizeEssayRequest(null, List.of(
                            new EssayCriterionInput("c1", part1Max.add(new BigDecimal("0.01")), null),
                            new EssayCriterionInput("c2", part2Max.subtract(new BigDecimal("0.01")), null)),
                            maxPts, "Vượt trần 1 tiêu chí"));
            assertThat(singleCriterionOverflow.getResponse().getStatus()).isEqualTo(422);
            assertThat(json(singleCriterionOverflow).path("code").asText()).isEqualTo("ESSAY_CRITERIA_INVALID");

            // Fractional scale > 4 -> 422 ESSAY_POINTS_OUT_OF_RANGE / ESSAY_CRITERIA_INVALID (R1-4)
            MvcResult badScaleTotal = performFinalize(grader, sub.essayItemId(),
                    new FinalizeEssayRequest(null, List.of(
                            new EssayCriterionInput("c1", part1Max, null),
                            new EssayCriterionInput("c2", part2Max, null)),
                            new BigDecimal("1.12345"), "Quá 4 số thập phân"));
            assertThat(badScaleTotal.getResponse().getStatus()).isEqualTo(422);
            assertThat(json(badScaleTotal).path("code").asText()).isEqualTo("ESSAY_POINTS_OUT_OF_RANGE");

            MvcResult badScaleCriterion = performFinalize(grader, sub.essayItemId(),
                    new FinalizeEssayRequest(null, List.of(
                            new EssayCriterionInput("c1", new BigDecimal("1.12345"), null),
                            new EssayCriterionInput("c2", part2Max, null)),
                            part2Max.add(new BigDecimal("1.12345")).setScale(2, java.math.RoundingMode.DOWN), "Tiêu chí quá 4 số lẻ"));
            assertThat(badScaleCriterion.getResponse().getStatus()).isEqualTo(422);
            assertThat(json(badScaleCriterion).path("code").asText()).isEqualTo("ESSAY_CRITERIA_INVALID");

            // 0 points with matching 0 criteria -> 200 WRONG, then reopen and test maxPts -> 200 CORRECT
            MvcResult zeroOk = performFinalize(grader, sub.essayItemId(),
                    new FinalizeEssayRequest(null, List.of(
                            new EssayCriterionInput("c1", BigDecimal.ZERO, "Chưa đạt"),
                            new EssayCriterionInput("c2", BigDecimal.ZERO, "Chưa đạt")),
                            BigDecimal.ZERO, "Không đạt"));
            assertThat(zeroOk.getResponse().getStatus()).isEqualTo(200);
            assertThat(attemptItemRepository.findById(sub.essayItemId()).orElseThrow().getOutcome())
                    .isEqualTo(ItemOutcome.WRONG);

            assertThat(performReopen(grader, sub.essayItemId(), new ReopenEssayRequest("Kiểm tra điểm tối đa"))
                    .getResponse().getStatus()).isEqualTo(200);

            MvcResult maxOk = performFinalize(grader, sub.essayItemId(),
                    new FinalizeEssayRequest(null, List.of(
                            new EssayCriterionInput("c1", part1Max, "Tốt"),
                            new EssayCriterionInput("c2", part2Max, "Xuất sắc")),
                            maxPts, "Đạt điểm tối đa"));
            assertThat(maxOk.getResponse().getStatus()).isEqualTo(200);
            assertThat(attemptItemRepository.findById(sub.essayItemId()).orElseThrow().getOutcome())
                    .isEqualTo(ItemOutcome.CORRECT);
        } finally {
            jdbc.update(
                    "UPDATE questionbank.question_version SET snapshot = ?::jsonb WHERE question_id = ? AND version_no = ?",
                    objectMapper.writeValueAsString(origSnap),
                    essayItem.getQuestionId(),
                    essayItem.getQuestionVersionNo());
        }
    }

    @Test
    void lastEssayScoresAttempt() throws Exception {
        UUID student = createStudent("essay-two-student");
        UUID grader = createGrader("grader-two", "Giám khảo Hai Câu");

        EssaySubmission sub = submitSingleBcaEssay(student, "<p>Bài tự luận thứ nhất</p>");
        AttemptItem essay1 = attemptItemRepository.findById(sub.essayItemId()).orElseThrow();
        UUID essay2Id = addSecondEssayItem(sub.attemptId(), essay1);

        // Finalize 1st essay: attempt stays PENDING_REVIEW with null total scores & null gradedAt
        performClaim(grader, essay1.getId());
        MvcResult fin1 = performFinalize(grader, essay1.getId(),
                new FinalizeEssayRequest(null, null, new BigDecimal("10.00"), "Tốt"));
        assertThat(fin1.getResponse().getStatus()).isEqualTo(200);
        JsonNode fin1Json = json(fin1);
        assertThat(fin1Json.path("attemptState").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(fin1Json.path("rawScore").isNull()).isTrue();
        assertThat(fin1Json.path("gradedAt").isNull()).isTrue();

        Attempt mid = attemptRepository.findById(sub.attemptId()).orElseThrow();
        assertThat(mid.getState()).isEqualTo(AttemptState.PENDING_REVIEW);
        assertThat(mid.getRawScore()).isNull();
        assertThat(mid.getGradedAt()).isNull();

        // Finalize 2nd essay: attempt becomes SCORED with gradedAt and exact sum
        performClaim(grader, essay2Id);
        MvcResult fin2 = performFinalize(grader, essay2Id,
                new FinalizeEssayRequest(null, null, new BigDecimal("5.50"), "Khá"));
        assertThat(fin2.getResponse().getStatus()).isEqualTo(200);
        JsonNode fin2Json = json(fin2);
        assertThat(fin2Json.path("attemptState").asText()).isEqualTo("SCORED");
        assertThat(new BigDecimal(fin2Json.path("rawScore").asText())).isEqualByComparingTo("15.50");
        assertThat(fin2Json.path("gradedAt").asText()).isNotBlank();

        Attempt scored = attemptRepository.findById(sub.attemptId()).orElseThrow();
        assertThat(scored.getState()).isEqualTo(AttemptState.SCORED);
        assertThat(scored.getGradedAt()).isNotNull();
        assertThat(scored.getRawScore()).isEqualByComparingTo("15.50");
        assertThat(attemptEventRepository.existsByAttemptIdAndType(sub.attemptId(), AttemptEventType.GRADED))
                .isTrue();
    }

    @Test
    void reopenKeepsOldScoreAndHistory() throws Exception {
        UUID student = createStudent("essay-reopen-student");
        UUID graderA = createGrader("grader-reopen-a", "Giám khảo Đầu");
        UUID graderB = createGrader("grader-reopen-b", "Giám khảo Mở Lại");

        EssaySubmission sub = submitSingleBcaEssay(student, "<p>Bài làm kiểm tra mở lại</p>");

        performClaim(graderA, sub.essayItemId());
        MvcResult firstFin = performFinalize(graderA, sub.essayItemId(),
                new FinalizeEssayRequest(null, null, new BigDecimal("18.25"), "Chấm lần 1"));
        assertThat(firstFin.getResponse().getStatus()).isEqualTo(200);
        BigDecimal oldRaw = new BigDecimal(json(firstFin).path("rawScore").asText());
        BigDecimal oldDisplay = new BigDecimal(json(firstFin).path("displayScore").asText());
        Instant oldGradedAt = Instant.parse(json(firstFin).path("gradedAt").asText());

        // Reopen with reason
        mutableClock.advance(Duration.ofMinutes(5));
        MvcResult reopenRes = performReopen(graderB, sub.essayItemId(),
                new ReopenEssayRequest("Phúc khảo câu tự luận"));
        assertThat(reopenRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode reopened = json(reopenRes);
        assertThat(reopened.path("state").asText()).isEqualTo("DRAFT");
        assertThat(reopened.path("graderId").asText()).isEqualTo(graderB.toString());
        assertThat(reopened.path("reopenedReason").asText()).isEqualTo("Phúc khảo câu tự luận");
        assertThat(new BigDecimal(reopened.path("points").asText())).isEqualByComparingTo("18.25");
        assertThat(reopened.path("comment").asText()).isEqualTo("Chấm lần 1");

        Attempt afterReopen = attemptRepository.findById(sub.attemptId()).orElseThrow();
        assertThat(afterReopen.getState()).isEqualTo(AttemptState.PENDING_REVIEW);
        assertThat(afterReopen.isRegrading()).isTrue();
        assertThat(afterReopen.getRawScore()).isEqualByComparingTo(oldRaw);
        assertThat(afterReopen.getDisplayScore()).isEqualByComparingTo(oldDisplay);
        assertThat(afterReopen.getGradedAt()).isEqualTo(oldGradedAt);

        // Student result & history STILL show old score + "Đang chấm lại" (AC-8)
        MvcResult resultRes = mockMvc.perform(
                        get("/api/v1/me/attempts/{id}/result", sub.attemptId())
                                .with(AttemptTestFixtures.asExamUser(student, "student")))
                .andReturn();
        assertThat(resultRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode resultJson = json(resultRes);
        assertThat(resultJson.path("state").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(resultJson.path("regrading").asBoolean()).isTrue();
        assertThat(resultJson.path("regradingLabel").asText()).isEqualTo("Đang chấm lại");
        assertThat(new BigDecimal(resultJson.path("rawScore").asText())).isEqualByComparingTo(oldRaw);
        assertThat(new BigDecimal(resultJson.path("displayScore").asText())).isEqualByComparingTo(oldDisplay);

        MvcResult historyRes = mockMvc.perform(
                        get("/api/v1/me/attempts")
                                .with(AttemptTestFixtures.asExamUser(student, "student")))
                .andReturn();
        assertThat(historyRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode historyRow = json(historyRes).path("items").get(0);
        assertThat(historyRow.path("regrading").asBoolean()).isTrue();
        assertThat(historyRow.path("regradingLabel").asText()).isEqualTo("Đang chấm lại");
        assertThat(new BigDecimal(historyRow.path("rawScore").asText())).isEqualByComparingTo(oldRaw);

        // Detail endpoint preserves full history chronologically
        MvcResult detailRes = performGetDetail(graderB, sub.essayItemId());
        assertThat(detailRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode detail = json(detailRes);
        assertThat(detail.path("regrading").asBoolean()).isTrue();
        assertThat(detail.path("activeGrade").path("state").asText()).isEqualTo("DRAFT");
        assertThat(detail.path("history").size()).isEqualTo(1);
        assertThat(detail.path("history").get(0).path("state").asText()).isEqualTo("FINAL");
        assertThat(detail.path("history").get(0).path("graderName").asText()).isEqualTo("Giám khảo Đầu");
        assertThat(new BigDecimal(detail.path("history").get(0).path("points").asText()))
                .isEqualByComparingTo("18.25");

        // Re-finalize updates score, clears regrading, records REGRADED event
        mutableClock.advance(Duration.ofMinutes(2));
        MvcResult secondFin = performFinalize(graderB, sub.essayItemId(),
                new FinalizeEssayRequest(null, null, new BigDecimal("21.00"), "Chấm lại lần 2"));
        assertThat(secondFin.getResponse().getStatus()).isEqualTo(200);
        Attempt reScored = attemptRepository.findById(sub.attemptId()).orElseThrow();
        assertThat(reScored.getState()).isEqualTo(AttemptState.SCORED);
        assertThat(reScored.isRegrading()).isFalse();
        assertThat(reScored.getRawScore()).isEqualByComparingTo("21.00");
        assertThat(attemptEventRepository.existsByAttemptIdAndType(sub.attemptId(), AttemptEventType.REGRADED))
                .isTrue();
    }

    @Test
    void reopenRequiresReason() throws Exception {
        UUID student = createStudent("essay-reopen-reason-student");
        UUID grader = createGrader("grader-reopen-reason", "Giám khảo Lý Do");

        EssaySubmission sub = submitSingleBcaEssay(student, "<p>Bài làm kiểm lý do mở lại</p>");

        performClaim(grader, sub.essayItemId());
        // Cannot reopen while still DRAFT -> 409 ESSAY_NOT_FINALIZED
        MvcResult draftReopen = performReopen(grader, sub.essayItemId(), new ReopenEssayRequest("Muốn mở lại"));
        assertThat(draftReopen.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(draftReopen).path("code").asText()).isEqualTo("ESSAY_NOT_FINALIZED");

        performFinalize(grader, sub.essayItemId(),
                new FinalizeEssayRequest(null, null, new BigDecimal("10.00"), "Done"));

        // Blank reason -> 400 VALIDATION_FAILED
        MvcResult blankReopen = performReopen(grader, sub.essayItemId(), new ReopenEssayRequest("   "));
        assertThat(blankReopen.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(blankReopen).path("code").asText()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void graderSeesNoLearnerIdentity() throws Exception {
        UUID student = createStudent("secret.student.login");
        jdbc.update(
                """
                UPDATE identity.user_account
                SET code = ?, username = ?, username_normalized = ?, full_name = ?, email = ?, email_normalized = ?, phone = ?
                WHERE id = ?
                """,
                "SV-SECRET-9988",
                "secret.learner.username",
                "secret.learner.username",
                "Nguyễn Văn Bí Mật",
                "secret.learner@thi360.test",
                "secret.learner@thi360.test",
                "0912345678",
                student);
        UUID grader = createGrader("grader-anon", "Giám khảo Ẩn Danh");

        EssaySubmission sub = submitSingleBcaEssay(student, "<p>Bài làm ẩn danh</p>");
        performClaim(grader, sub.essayItemId());

        List<String> forbiddenIdentityKeys = List.of(
                "userId",
                "username",
                "fullName",
                "email",
                "phone",
                "studentCode",
                "userCode",
                "code",
                "userFullName",
                "userEmail",
                "entitlementId",
                "grantId");

        MvcResult listRes = mockMvc.perform(
                        get("/api/v1/admin/grading/items")
                                .param("exam", sub.examId().toString())
                                .with(asUser(grader, "grader.anon", Permissions.GRADING_VIEW)))
                .andReturn();
        assertThat(listRes.getResponse().getStatus()).isEqualTo(200);
        String listJson = listRes.getResponse().getContentAsString();
        assertNoLearnerLeak(listJson, student);
        JsonNode firstItem = json(listRes).path("items").get(0);
        assertThat(firstItem.has("attemptId")).isTrue();
        for (String forbiddenKey : forbiddenIdentityKeys) {
            assertThat(firstItem.has(forbiddenKey)).as("list item must not contain %s", forbiddenKey).isFalse();
        }

        MvcResult detailRes = performGetDetail(grader, sub.essayItemId());
        assertThat(detailRes.getResponse().getStatus()).isEqualTo(200);
        String detailJson = detailRes.getResponse().getContentAsString();
        assertNoLearnerLeak(detailJson, student);
        JsonNode detailNode = json(detailRes);
        for (String forbiddenKey : forbiddenIdentityKeys) {
            assertThat(detailNode.has(forbiddenKey)).as("detail must not contain %s", forbiddenKey).isFalse();
        }
    }

    @Test
    void itemDetailUsesIssuedVersion() throws Exception {
        UUID student = createStudent("essay-ver-detail-student");
        UUID grader = createGrader("grader-ver-detail", "Giám khảo Phiên Bản Câu");

        EssaySubmission sub = submitSingleBcaEssay(student, "<p>Một hai ba bốn năm</p>");
        AttemptItem essayItem = attemptItemRepository.findById(sub.essayItemId()).orElseThrow();
        int v1No = essayItem.getQuestionVersionNo();
        int v2No = v1No + 1;

        var v1 = questionSourcePort.loadVersions(List.of(new VersionKey(essayItem.getQuestionId(), v1No))).getFirst();
        ObjectNode origV1Snap = v1.snapshot().deepCopy();

        UUID contentAssetId = UUID.randomUUID();
        UUID passageAssetId = UUID.randomUUID();
        UUID guideAssetId = UUID.randomUUID();
        UUID sampleAssetId = UUID.randomUUID();

        ObjectNode v1Snap = origV1Snap.deepCopy();
        v1Snap.put("content", "<p>Đề bài v1 <img data-asset-id=\"" + contentAssetId + "\" alt=\"c\"></p>");
        ObjectNode v1Body = (ObjectNode) v1Snap.path("body");
        v1Body.put("gradingGuide", "<p>Hướng dẫn v1 <img data-asset-id=\"" + guideAssetId + "\" alt=\"g\"></p>");
        v1Body.put("sampleAnswer", "<p>Bài mẫu v1 <img data-asset-id=\"" + sampleAssetId + "\" alt=\"s\"></p>");
        v1Body.put("wordLimit", 350);
        ArrayNode v1Rubric = objectMapper.createArrayNode();
        v1Rubric.add(objectMapper.createObjectNode().put("id", "r1").put("description", "Nội dung").put("points", 10.0));
        v1Rubric.add(objectMapper.createObjectNode().put("id", "r2").put("description", "Diễn đạt").put("points", 10.0));
        v1Body.set("rubric", v1Rubric);

        ObjectNode v2Snap = v1Snap.deepCopy();
        v2Snap.put("content", "<p>ĐỀ BÀI V2 ĐÃ SỬA</p>");
        ((ObjectNode) v2Snap.path("body")).put("gradingGuide", "<p>HƯỚNG DẪN V2</p>");
        ((ObjectNode) v2Snap.path("body")).put("sampleAnswer", "<p>BÀI MẪU V2</p>");
        ((ObjectNode) v2Snap.path("body")).put("wordLimit", 999);
        ArrayNode v2Rubric = objectMapper.createArrayNode();
        v2Rubric.add(objectMapper.createObjectNode().put("id", "r2-only").put("description", "Rubric v2").put("points", 20.0));
        ((ObjectNode) v2Snap.path("body")).set("rubric", v2Rubric);

        UUID parentQuestionId = Ids.newId();
        UUID parentAttemptItemId = Ids.newId();
        UUID subjectItemId = jdbc.queryForObject(
                "SELECT subject_item_id FROM questionbank.question WHERE id = ?",
                UUID.class,
                essayItem.getQuestionId());

        try {
            jdbc.update(
                    "UPDATE questionbank.question_version SET snapshot = ?::jsonb WHERE question_id = ? AND version_no = ?",
                    objectMapper.writeValueAsString(v1Snap),
                    essayItem.getQuestionId(),
                    v1No);
            jdbc.update(
                    """
                    INSERT INTO questionbank.question_version (question_id, version_no, snapshot)
                    VALUES (?, ?, ?::jsonb)
                    ON CONFLICT (question_id, version_no) DO UPDATE SET snapshot = EXCLUDED.snapshot
                    """,
                    essayItem.getQuestionId(),
                    v2No,
                    objectMapper.writeValueAsString(v2Snap));
            jdbc.update(
                    "UPDATE questionbank.question SET current_version_no = ? WHERE id = ?",
                    v2No,
                    essayItem.getQuestionId());

            jdbc.update(
                    """
                    INSERT INTO questionbank.question (id, code, type, status, subject_item_id, current_version_no, points)
                    VALUES (?, ?, 'QUESTION_SET', 'READY', ?, 2, 20.0)
                    """,
                    parentQuestionId,
                    "AT5-PARENT-SET-" + parentQuestionId,
                    subjectItemId);
            String parentV1Snap = """
                    {
                      "content": "<p>Set v1</p>",
                      "points": 20.0,
                      "body": {
                        "type": "QUESTION_SET",
                        "passage": "<p>Ngữ liệu v1 <img data-asset-id=\\"%s\\" alt=\\"p\\"></p>",
                        "children": [{"id": "%s"}]
                      }
                    }
                    """.formatted(passageAssetId, essayItem.getQuestionId());
            String parentV2Snap = """
                    {
                      "content": "<p>Set v2</p>",
                      "points": 20.0,
                      "body": {
                        "type": "QUESTION_SET",
                        "passage": "<p>NGỮ LIỆU V2</p>",
                        "children": [{"id": "%s"}]
                      }
                    }
                    """.formatted(essayItem.getQuestionId());
            jdbc.update(
                    "INSERT INTO questionbank.question_version (question_id, version_no, snapshot) VALUES (?, 1, ?::jsonb), (?, 2, ?::jsonb)",
                    parentQuestionId,
                    parentV1Snap,
                    parentQuestionId,
                    parentV2Snap);

            jdbc.update(
                    """
                    INSERT INTO attempt.attempt_item (
                        id, attempt_id, part_id, subject_item_id, section_id, component_code, block_id,
                        ord, display_no, unit_count, question_id, question_version_no, question_type,
                        points_max, in_scope, answer_seq, flagged, version
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, -1, NULL, 1, ?, 1, 'QUESTION_SET', 20.0, true, 0, false, 0)
                    """,
                    parentAttemptItemId,
                    essayItem.getAttemptId(),
                    essayItem.getPartId(),
                    essayItem.getSubjectItemId(),
                    essayItem.getSectionId(),
                    essayItem.getComponentCode(),
                    essayItem.getBlockId(),
                    parentQuestionId);
            jdbc.update(
                    "UPDATE attempt.attempt_item SET parent_item_id = ? WHERE id = ?",
                    parentAttemptItemId,
                    essayItem.getId());

            MvcResult detailRes = performGetDetail(grader, sub.essayItemId());
            assertThat(detailRes.getResponse().getStatus()).isEqualTo(200);
            JsonNode detail = json(detailRes);
            assertThat(detail.path("questionVersionNo").asInt()).isEqualTo(v1No);
            assertThat(detail.path("content").asText())
                    .contains("Đề bài v1")
                    .contains("src=\"/api/v1/admin/question-assets/" + contentAssetId + "\"")
                    .doesNotContain("ĐỀ BÀI V2 ĐÃ SỬA");
            assertThat(detail.path("passage").asText())
                    .contains("Ngữ liệu v1")
                    .contains("src=\"/api/v1/admin/question-assets/" + passageAssetId + "\"")
                    .doesNotContain("NGỮ LIỆU V2");
            assertThat(detail.path("gradingGuide").asText())
                    .contains("Hướng dẫn v1")
                    .contains("src=\"/api/v1/admin/question-assets/" + guideAssetId + "\"")
                    .doesNotContain("HƯỚNG DẪN V2");
            assertThat(detail.path("sampleAnswer").asText())
                    .contains("Bài mẫu v1")
                    .contains("src=\"/api/v1/admin/question-assets/" + sampleAssetId + "\"")
                    .doesNotContain("BÀI MẪU V2");
            assertThat(detail.path("wordLimit").asInt()).isEqualTo(350);
            assertThat(detail.path("wordCount").asInt()).isEqualTo(5);
            assertThat(detail.path("answerHtml").asText()).contains("Một hai ba bốn năm");
            assertThat(detail.path("rubric")).hasSize(2);
            assertThat(detail.path("rubric").get(0).path("id").asText()).isEqualTo("r1");
            assertThat(detail.path("rubric").get(1).path("id").asText()).isEqualTo("r2");
        } finally {
            jdbc.update("UPDATE attempt.attempt_item SET parent_item_id = NULL WHERE id = ?", essayItem.getId());
            jdbc.update("DELETE FROM attempt.attempt_item WHERE id = ?", parentAttemptItemId);
            jdbc.update(
                    "UPDATE questionbank.question SET current_version_no = ? WHERE id = ?",
                    v1No,
                    essayItem.getQuestionId());
            jdbc.update(
                    "UPDATE questionbank.question_version SET snapshot = ?::jsonb WHERE question_id = ? AND version_no = ?",
                    objectMapper.writeValueAsString(origV1Snap),
                    essayItem.getQuestionId(),
                    v1No);
            jdbc.update(
                    "DELETE FROM questionbank.question_version WHERE question_id = ? AND version_no = ?",
                    essayItem.getQuestionId(),
                    v2No);
            AttemptTestFixtures.cleanupSyntheticQuestions(jdbc, List.of(parentQuestionId));
        }
    }

    @Test
    void listFilters() throws Exception {
        UUID student1 = createStudent("essay-filter-1");
        UUID student2 = createStudent("essay-filter-2");
        UUID grader = createGrader("grader-filters", "Giám khảo Lọc");

        EssaySubmission sub1 = submitSingleBcaEssay(student1, "<p>Bài lọc một</p>");
        mutableClock.advance(Duration.ofHours(2));
        EssaySubmission sub2 = submitSingleBcaEssay(student2, "<p>Bài lọc hai</p>");

        // Claim & finalize sub1 -> DONE; claim sub2 -> IN_PROGRESS
        performClaim(grader, sub1.essayItemId());
        performFinalize(grader, sub1.essayItemId(),
                new FinalizeEssayRequest(null, null, new BigDecimal("10.00"), "Xong"));
        performClaim(grader, sub2.essayItemId());

        // Filter by state DONE & IN_PROGRESS
        JsonNode doneList = json(mockMvc.perform(
                        get("/api/v1/admin/grading/items")
                                .param("state", "DONE")
                                .param("exam", sub1.examId().toString())
                                .with(asUser(grader, "grader.f", Permissions.GRADING_VIEW)))
                .andReturn());
        assertThat(doneList.path("items")).extracting(n -> n.path("itemId").asText())
                .containsExactly(sub1.essayItemId().toString());

        JsonNode inProgList = json(mockMvc.perform(
                        get("/api/v1/admin/grading/items")
                                .param("state", "IN_PROGRESS")
                                .param("exam", "bca")
                                .param("kind", "BCA")
                                .with(asUser(grader, "grader.f", Permissions.GRADING_VIEW)))
                .andReturn());
        assertThat(inProgList.path("items")).extracting(n -> n.path("itemId").asText())
                .containsExactly(sub2.essayItemId().toString());

        // Date filter (from/to on finished_at)
        Attempt att2 = attemptRepository.findById(sub2.attemptId()).orElseThrow();
        String vnDate = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(att2.getFinishedAt());
        JsonNode dateFiltered = json(mockMvc.perform(
                        get("/api/v1/admin/grading/items")
                                .param("from", att2.getFinishedAt().minusSeconds(60).toString())
                                .param("to", vnDate)
                                .with(asUser(grader, "grader.f", Permissions.GRADING_VIEW)))
                .andReturn());
        assertThat(dateFiltered.path("items")).extracting(n -> n.path("itemId").asText())
                .containsExactly(sub2.essayItemId().toString());

        // Pagination
        JsonNode page0 = json(mockMvc.perform(
                        get("/api/v1/admin/grading/items")
                                .param("exam", sub1.examId().toString())
                                .param("page", "0")
                                .param("size", "1")
                                .with(asUser(grader, "grader.f", Permissions.GRADING_VIEW)))
                .andReturn());
        assertThat(page0.path("items").size()).isEqualTo(1);
        assertThat(page0.path("totalItems").asLong()).isEqualTo(2L);
        assertThat(page0.path("totalPages").asInt()).isEqualTo(2);
    }

    @Test
    void countsFollowListFilters() throws Exception {
        UUID student1 = createStudent("essay-cnt-1");
        UUID student2 = createStudent("essay-cnt-2");
        UUID grader = createGrader("grader-cnt", "Giám khảo Đếm");

        EssaySubmission sub1 = submitSingleBcaEssay(student1, "<p>Đếm 1</p>");
        mutableClock.advance(Duration.ofDays(2));
        EssaySubmission sub2 = submitSingleBcaEssay(student2, "<p>Đếm 2</p>");

        Attempt att1 = attemptRepository.findById(sub1.attemptId()).orElseThrow();
        Attempt att2 = attemptRepository.findById(sub2.attemptId()).orElseThrow();
        String vnDate1 = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(att1.getFinishedAt());
        String vnDate2 = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(att2.getFinishedAt());

        // from/to date filter applies to /counts (R1-29)
        JsonNode countsDay1 = json(mockMvc.perform(
                        get("/api/v1/admin/grading/items/counts")
                                .param("from", vnDate1)
                                .param("to", vnDate1)
                                .with(asUser(grader, "grader.cnt", Permissions.GRADING_VIEW)))
                .andReturn());
        assertThat(countsDay1.path("waiting").asLong()).isEqualTo(1L);
        assertThat(countsDay1.path("total").asLong()).isEqualTo(1L);

        JsonNode countsDay2 = json(mockMvc.perform(
                        get("/api/v1/admin/grading/items/counts")
                                .param("from", att2.getFinishedAt().minusSeconds(60).toString())
                                .param("to", vnDate2)
                                .with(asUser(grader, "grader.cnt", Permissions.GRADING_VIEW)))
                .andReturn());
        assertThat(countsDay2.path("waiting").asLong()).isEqualTo(1L);
        assertThat(countsDay2.path("total").asLong()).isEqualTo(1L);

        // Initially both WAITING
        JsonNode c1 = json(mockMvc.perform(
                        get("/api/v1/admin/grading/items/counts")
                                .param("exam", sub1.examId().toString())
                                .param("kind", "BCA")
                                .param("state", "DONE") // must be ignored!
                                .with(asUser(grader, "grader.cnt", Permissions.GRADING_VIEW)))
                .andReturn());
        assertThat(c1.path("waiting").asLong()).isEqualTo(2L);
        assertThat(c1.path("inProgress").asLong()).isEqualTo(0L);
        assertThat(c1.path("done").asLong()).isEqualTo(0L);
        assertThat(c1.path("total").asLong()).isEqualTo(2L);

        // Claim sub1 -> 1 waiting, 1 inProgress
        performClaim(grader, sub1.essayItemId());
        JsonNode c2 = json(mockMvc.perform(
                        get("/api/v1/admin/grading/items/counts")
                                .param("exam", "bca")
                                .param("state", "WAITING")
                                .with(asUser(grader, "grader.cnt", Permissions.GRADING_VIEW)))
                .andReturn());
        assertThat(c2.path("waiting").asLong()).isEqualTo(1L);
        assertThat(c2.path("inProgress").asLong()).isEqualTo(1L);
        assertThat(c2.path("done").asLong()).isEqualTo(0L);
        assertThat(c2.path("total").asLong()).isEqualTo(2L);

        // Finalize sub1 -> 1 waiting, 0 inProgress, 1 done
        performFinalize(grader, sub1.essayItemId(),
                new FinalizeEssayRequest(null, null, new BigDecimal("11.00"), "OK"));
        JsonNode c3 = json(mockMvc.perform(
                        get("/api/v1/admin/grading/items/counts")
                                .param("kind", "BCA")
                                .with(asUser(grader, "grader.cnt", Permissions.GRADING_VIEW)))
                .andReturn());
        assertThat(c3.path("waiting").asLong()).isEqualTo(1L);
        assertThat(c3.path("inProgress").asLong()).isEqualTo(0L);
        assertThat(c3.path("done").asLong()).isEqualTo(1L);
        assertThat(c3.path("total").asLong()).isEqualTo(2L);

        // Non-matching kind filter -> 0
        JsonNode cZero = json(mockMvc.perform(
                        get("/api/v1/admin/grading/items/counts")
                                .param("kind", "HSA")
                                .with(asUser(grader, "grader.cnt", Permissions.GRADING_VIEW)))
                .andReturn());
        assertThat(cZero.path("total").asLong()).isEqualTo(0L);
    }

    @Test
    void countsNeedGradingView() throws Exception {
        UUID viewer = createGrader("grader-view-only", "Người Xem Hàng Chờ");
        MvcResult ok = mockMvc.perform(
                        get("/api/v1/admin/grading/items/counts")
                                .with(asUser(viewer, "viewer", Permissions.GRADING_VIEW)))
                .andReturn();
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);

        MvcResult forbidden = mockMvc.perform(
                        get("/api/v1/admin/grading/items/counts")
                                .with(asUser(viewer, "other", Permissions.ATTEMPT_VIEW)))
                .andReturn();
        assertThat(forbidden.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void auditRecorded() throws Exception {
        UUID student = createStudent("essay-audit-student");
        UUID grader = createGrader("grader-audit", "Giám khảo Nhật Ký");

        EssaySubmission sub = submitSingleBcaEssay(student, "<p>Bài làm kiểm tra audit</p>");
        performClaim(grader, sub.essayItemId());
        performFinalize(grader, sub.essayItemId(),
                new FinalizeEssayRequest(null, null, new BigDecimal("16.00"), "Tốt"));
        performReopen(grader, sub.essayItemId(), new ReopenEssayRequest("Cần rà soát lại"));

        drainAllOutbox();

        Integer gradedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit.audit_event WHERE module = 'attempt' AND action = 'ESSAY_GRADED' AND target_id = ?",
                Integer.class,
                sub.essayItemId().toString());
        assertThat(gradedCount).isEqualTo(1);

        List<String> reopenReasons = jdbc.queryForList(
                "SELECT reason FROM audit.audit_event WHERE module = 'attempt' AND action = 'ESSAY_REOPENED' AND target_id = ?",
                String.class,
                sub.essayItemId().toString());
        assertThat(reopenReasons).containsExactly("Cần rà soát lại");
    }

    @Test
    void validationErrorsLocalizationAndDraftFinalize() throws Exception {
        UUID student = createStudent("essay-val-student");
        UUID graderA = createGrader("grader-val-a", "Giám khảo Kiểm Tra A");
        UUID graderB = createGrader("grader-val-b", "Giám khảo Kiểm Tra B");

        EssaySubmission sub = submitSingleBcaEssay(student, "<p>Bài làm kiểm tra validation</p>");
        AttemptItem essayItem = attemptItemRepository.findById(sub.essayItemId()).orElseThrow();
        AttemptItem scItem = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(sub.attemptId())
                .stream()
                .filter(i -> i.isInScope() && i.getQuestionType() == QuestionType.SINGLE_CHOICE)
                .findFirst()
                .orElseThrow();

        // 1. Blank essay / non-essay claim -> 409 ESSAY_NOT_GRADABLE (R1-20)
        MvcResult nonEssayClaim = performClaim(graderA, scItem.getId());
        assertThat(nonEssayClaim.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(nonEssayClaim).path("code").asText()).isEqualTo("ESSAY_NOT_GRADABLE");

        UUID blankStudent = createStudent("essay-blank-claim");
        GrantOrderResult.Line blankGrant = grant(blankStudent, "DS-BCA-FULL-MINI", 2, List.of());
        Attempt blankAttempt = startAndAwaitOpen(
                blankStudent, UUID.randomUUID().toString(), blankGrant.entitlementId(), new PreChoiceInput("CA2", null));
        UUID blankSession = claimSession(blankStudent, blankAttempt.getId());
        List<AttemptPart> blankParts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(blankAttempt.getId());
        for (int idx = 0; idx < blankParts.size() - 1; idx++) {
            assertThat(performFinishPart(blankStudent, blankAttempt.getId(), blankParts.get(idx).getId(),
                    blankSession, new FinishPartRequest(List.of())).getResponse().getStatus()).isEqualTo(200);
        }
        assertThat(performSubmit(blankStudent, blankAttempt.getId(), UUID.randomUUID().toString(),
                blankSession, new SubmitAttemptRequest(List.of())).getResponse().getStatus()).isEqualTo(200);
        drainAllOutbox();
        AttemptItem blankEssay = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(blankAttempt.getId())
                .stream()
                .filter(i -> i.getQuestionType() == QuestionType.ESSAY)
                .findFirst()
                .orElseThrow();
        assertThat(blankEssay.getOutcome()).isEqualTo(ItemOutcome.BLANK);
        MvcResult blankClaim = performClaim(graderA, blankEssay.getId());
        assertThat(blankClaim.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(blankClaim).path("code").asText()).isEqualTo("ESSAY_NOT_GRADABLE");

        // 2. Unclaimed PUT and POST /finalize -> 409 GRADING_NOT_CLAIMED (R1-20/21)
        MvcResult unclaimedPut = performSaveDraft(
                graderA, sub.essayItemId(), new SaveEssayDraftRequest(null, BigDecimal.ONE, "Chưa nhận", 0L));
        assertThat(unclaimedPut.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(unclaimedPut).path("code").asText()).isEqualTo("GRADING_NOT_CLAIMED");

        MvcResult unclaimedFinalize = performFinalize(
                graderA, sub.essayItemId(), new FinalizeEssayRequest(null, null, BigDecimal.ONE, "Chưa nhận"));
        assertThat(unclaimedFinalize.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(unclaimedFinalize).path("code").asText()).isEqualTo("GRADING_NOT_CLAIMED");

        // 3. Malformed scalar JSON on PUT -> 400 VALIDATION_FAILED (R1-1/2/3)
        MvcResult badScalarJson = mockMvc.perform(
                        put("/api/v1/admin/grading/items/{itemId}", sub.essayItemId())
                                .with(asUser(graderA, "grader", Permissions.GRADING_MODIFY))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"points\":\"abc\",\"expectedVersion\":0}"))
                .andReturn();
        assertThat(badScalarJson.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(badScalarJson).path("code").asText()).isEqualTo("VALIDATION_FAILED");

        // 4. Claim item and test empty-body finalize before saving draft -> 422 ESSAY_POINTS_OUT_OF_RANGE (R1-21)
        long v0 = json(performClaim(graderA, sub.essayItemId())).path("version").asLong();
        MvcResult emptyFinalizeBeforeDraft = performFinalize(graderA, sub.essayItemId(), null);
        assertThat(emptyFinalizeBeforeDraft.getResponse().getStatus()).isEqualTo(422);
        assertThat(json(emptyFinalizeBeforeDraft).path("code").asText()).isEqualTo("ESSAY_POINTS_OUT_OF_RANGE");

        // 5. saveDraft overflow / precision / null criterion guards (R1-4/5, R1-24/25)
        MvcResult overflowDraft = performSaveDraft(
                graderA, sub.essayItemId(), new SaveEssayDraftRequest(null, new BigDecimal("1000000"), "Tràn", v0));
        assertThat(overflowDraft.getResponse().getStatus()).isEqualTo(422);
        assertThat(json(overflowDraft).path("code").asText()).isEqualTo("ESSAY_POINTS_OUT_OF_RANGE");

        MvcResult scaleDraft = performSaveDraft(
                graderA, sub.essayItemId(), new SaveEssayDraftRequest(null, new BigDecimal("1.12345"), "Lẻ", v0));
        assertThat(scaleDraft.getResponse().getStatus()).isEqualTo(422);
        assertThat(json(scaleDraft).path("code").asText()).isEqualTo("ESSAY_POINTS_OUT_OF_RANGE");

        MvcResult overflowCritDraft = performSaveDraft(
                graderA,
                sub.essayItemId(),
                new SaveEssayDraftRequest(
                        List.of(new EssayCriterionInput("k1", new BigDecimal("1000000"), null)),
                        new BigDecimal("10.00"),
                        null,
                        v0));
        assertThat(overflowCritDraft.getResponse().getStatus()).isEqualTo(422);
        assertThat(json(overflowCritDraft).path("code").asText()).isEqualTo("ESSAY_CRITERIA_INVALID");

        MvcResult nullCriterionPut = mockMvc.perform(
                        put("/api/v1/admin/grading/items/{itemId}", sub.essayItemId())
                                .with(asUser(graderA, "grader", Permissions.GRADING_MODIFY))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"criteria\":[null],\"points\":10,\"expectedVersion\":" + v0 + "}"))
                .andReturn();
        assertThat(nullCriterionPut.getResponse().getStatus()).isEqualTo(422);
        assertThat(json(nullCriterionPut).path("code").asText()).isEqualTo("ESSAY_CRITERIA_INVALID");

        MvcResult nullCriterionFin = mockMvc.perform(
                        post("/api/v1/admin/grading/items/{itemId}/finalize", sub.essayItemId())
                                .with(asUser(graderA, "grader", Permissions.GRADING_MODIFY))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"criteria\":[null],\"points\":10}"))
                .andReturn();
        assertThat(nullCriterionFin.getResponse().getStatus()).isEqualTo(422);
        assertThat(json(nullCriterionFin).path("code").asText()).isEqualTo("ESSAY_CRITERIA_INVALID");

        // 5b. Bean validation bounds: >50 criteria & >100 char criterion id -> 400 VALIDATION_FAILED (R2-6)
        List<EssayCriterionInput> tooManyCriteria = IntStream.range(0, 51)
                .mapToObj(i -> new EssayCriterionInput("c-" + i, BigDecimal.ZERO, null))
                .toList();
        MvcResult tooManyCriteriaPut = performSaveDraft(
                graderA,
                sub.essayItemId(),
                new SaveEssayDraftRequest(tooManyCriteria, BigDecimal.TEN, null, v0));
        assertThat(tooManyCriteriaPut.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(tooManyCriteriaPut).path("code").asText()).isEqualTo("VALIDATION_FAILED");

        MvcResult tooManyCriteriaFin = performFinalize(
                graderA,
                sub.essayItemId(),
                new FinalizeEssayRequest(null, tooManyCriteria, BigDecimal.TEN, null));
        assertThat(tooManyCriteriaFin.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(tooManyCriteriaFin).path("code").asText()).isEqualTo("VALIDATION_FAILED");

        List<EssayCriterionInput> longCriterionId = List.of(
                new EssayCriterionInput("x".repeat(101), BigDecimal.TEN, null));
        MvcResult longIdPut = performSaveDraft(
                graderA,
                sub.essayItemId(),
                new SaveEssayDraftRequest(longCriterionId, BigDecimal.TEN, null, v0));
        assertThat(longIdPut.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(longIdPut).path("code").asText()).isEqualTo("VALIDATION_FAILED");

        MvcResult longIdFin = performFinalize(
                graderA,
                sub.essayItemId(),
                new FinalizeEssayRequest(null, longCriterionId, BigDecimal.TEN, null));
        assertThat(longIdFin.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(longIdFin).path("code").asText()).isEqualTo("VALIDATION_FAILED");

        // 6. Save valid draft then finalize with empty body -> 200 using saved draft (R1-21)
        MvcResult savedDraft = performSaveDraft(
                graderA, sub.essayItemId(), new SaveEssayDraftRequest(null, new BigDecimal("14.50"), "Nháp chuẩn", v0));
        assertThat(savedDraft.getResponse().getStatus()).isEqualTo(200);

        MvcResult finalizedFromDraft = performFinalize(graderA, sub.essayItemId(), null);
        assertThat(finalizedFromDraft.getResponse().getStatus()).isEqualTo(200);
        JsonNode finJson = json(finalizedFromDraft);
        assertThat(finJson.path("grade").path("state").asText()).isEqualTo("FINAL");
        assertThat(new BigDecimal(finJson.path("grade").path("points").asText())).isEqualByComparingTo("14.50");
        assertThat(finJson.path("grade").path("comment").asText()).isEqualTo("Nháp chuẩn");
        assertThat(new BigDecimal(finJson.path("rawScore").asText())).isEqualByComparingTo("14.50");

        // 7. Reopen then idle-expire takeover preserves reopenedReason (R1-22)
        assertThat(performReopen(graderA, sub.essayItemId(), new ReopenEssayRequest("Phúc khảo giữ lý do"))
                .getResponse().getStatus()).isEqualTo(200);
        mutableClock.advance(Duration.ofMinutes(30).plusSeconds(1));
        MvcResult takeover = performClaim(graderB, sub.essayItemId());
        assertThat(takeover.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(takeover).path("reopenedReason").asText()).isEqualTo("Phúc khảo giữ lý do");
        EssayGrade takeoverEntity = essayGradeRepository.findByAttemptItemIdAndSupersededByIsNull(sub.essayItemId())
                .orElseThrow();
        assertThat(takeoverEntity.getState()).isEqualTo(EssayGradeState.DRAFT);
        assertThat(takeoverEntity.getReopenedReason()).isEqualTo("Phúc khảo giữ lý do");

        // 8. Localized INVALID_DATE_FILTER in Vietnamese & English without echoing raw input (R1-8/10)
        for (String endpoint : List.of(
                "/api/v1/admin/grading/items",
                "/api/v1/admin/grading/items/counts",
                "/api/v1/admin/attempts")) {
            String perm = endpoint.contains("/attempts") ? Permissions.ATTEMPT_VIEW : Permissions.GRADING_VIEW;
            MvcResult viDateErr = mockMvc.perform(
                            get(endpoint)
                                    .param("from", "not-a-date")
                                    .with(asUser(graderA, "grader.i18n", perm)))
                    .andReturn();
            assertThat(viDateErr.getResponse().getStatus()).isEqualTo(422);
            assertThat(viDateErr.getResponse().getContentAsString()).doesNotContain("not-a-date");
            JsonNode viJson = json(viDateErr);
            assertThat(viJson.path("code").asText()).isEqualTo("INVALID_DATE_FILTER");
            assertThat(viJson.path("message").asText()).isEqualTo("Tham số lọc thời gian không đúng định dạng");

            MvcResult enDateErr = mockMvc.perform(
                            get(endpoint)
                                    .param("from", "not-a-date")
                                    .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                                    .with(asUser(graderA, "grader.i18n", perm)))
                    .andReturn();
            assertThat(enDateErr.getResponse().getStatus()).isEqualTo(422);
            JsonNode enJson = json(enDateErr);
            assertThat(enJson.path("code").asText()).isEqualTo("INVALID_DATE_FILTER");
            assertThat(enJson.path("message").asText()).isEqualTo("Date filter parameter has an invalid format");
        }
    }

    private record EssaySubmission(UUID examId, UUID attemptId, UUID essayItemId) {}

    private EssaySubmission submitSingleBcaEssay(UUID studentId, String html) throws Exception {
        GrantOrderResult.Line grant = grant(studentId, "DS-BCA-FULL-MINI", 2, List.of());
        Attempt attempt = startAndAwaitOpen(studentId, UUID.randomUUID().toString(), grant.entitlementId(), new PreChoiceInput("CA2", null));
        UUID sessionId = claimSession(studentId, attempt.getId());
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        AttemptItem essayItem = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attempt.getId())
                .stream()
                .filter(i -> i.getQuestionType() == QuestionType.ESSAY)
                .findFirst()
                .orElseThrow();

        List<FinalAnswerChange> essayAnswer = List.of(
                new FinalAnswerChange(essayItem.getId(), 1L, new AnswerValue.Essay(html, null)));
        for (int idx = 0; idx < parts.size() - 1; idx++) {
            AttemptPart p = parts.get(idx);
            List<FinalAnswerChange> partFinals = p.getId().equals(essayItem.getPartId()) ? essayAnswer : List.of();
            MvcResult fin = performFinishPart(studentId, attempt.getId(), p.getId(), sessionId, new FinishPartRequest(partFinals));
            assertThat(fin.getResponse().getStatus()).isEqualTo(200);
        }
        AttemptPart lastPart = parts.getLast();
        List<FinalAnswerChange> submitFinals = lastPart.getId().equals(essayItem.getPartId()) ? essayAnswer : List.of();
        MvcResult subRes = performSubmit(
                studentId,
                attempt.getId(),
                UUID.randomUUID().toString(),
                sessionId,
                new SubmitAttemptRequest(submitFinals));
        assertThat(subRes.getResponse().getStatus()).isEqualTo(200);
        drainAllOutbox();

        Attempt reloaded = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(AttemptState.PENDING_REVIEW);
        return new EssaySubmission(reloaded.getExamId(), reloaded.getId(), essayItem.getId());
    }

    private UUID addSecondEssayItem(UUID attemptId, AttemptItem template) throws Exception {
        UUID newItemId = Ids.newId();
        jdbc.update(
                """
                INSERT INTO attempt.attempt_item (
                    id, attempt_id, part_id, subject_item_id, section_id, component_code, block_id,
                    ord, display_no, unit_count, question_id, question_version_no, question_type,
                    points_max, in_scope, answer, answer_seq, flagged, outcome, grading_detail, version
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, 'ESSAY', ?, true, ?::jsonb, 1, false, 'PENDING_REVIEW', '{"wordCount":3}'::jsonb, 0)
                """,
                newItemId,
                attemptId,
                template.getPartId(),
                template.getSubjectItemId(),
                template.getSectionId(),
                template.getComponentCode(),
                template.getBlockId(),
                template.getOrd() + 100,
                template.getDisplayNo() != null ? template.getDisplayNo() + 1 : 99,
                template.getQuestionId(),
                template.getQuestionVersionNo(),
                new BigDecimal("10.00"),
                objectMapper.writeValueAsString(new AnswerValue.Essay("<p>Bài tự luận hai</p>", 3)));
        return newItemId;
    }

    private UUID createGrader(String label, String fullName) {
        UUID id = createManager(label);
        jdbc.update("UPDATE identity.user_account SET full_name = ? WHERE id = ?", fullName, id);
        return id;
    }

    private MvcResult performGetDetail(UUID graderId, UUID itemId) throws Exception {
        return mockMvc.perform(
                        get("/api/v1/admin/grading/items/{itemId}", itemId)
                                .with(asUser(graderId, "grader", Permissions.GRADING_VIEW)))
                .andReturn();
    }

    private MvcResult performClaim(UUID graderId, UUID itemId) throws Exception {
        return mockMvc.perform(
                        post("/api/v1/admin/grading/items/{itemId}/claim", itemId)
                                .with(asUser(graderId, "grader", Permissions.GRADING_MODIFY)))
                .andReturn();
    }

    private MvcResult performSaveDraft(UUID graderId, UUID itemId, SaveEssayDraftRequest req) throws Exception {
        return mockMvc.perform(
                        put("/api/v1/admin/grading/items/{itemId}", itemId)
                                .with(asUser(graderId, "grader", Permissions.GRADING_MODIFY))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andReturn();
    }

    private MvcResult performFinalize(UUID graderId, UUID itemId, FinalizeEssayRequest req) throws Exception {
        var builder = post("/api/v1/admin/grading/items/{itemId}/finalize", itemId)
                .with(asUser(graderId, "grader", Permissions.GRADING_MODIFY));
        if (req != null) {
            builder = builder.contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req));
        }
        return mockMvc.perform(builder).andReturn();
    }

    private MvcResult performReopen(UUID graderId, UUID itemId, ReopenEssayRequest req) throws Exception {
        return mockMvc.perform(
                        post("/api/v1/admin/grading/items/{itemId}/reopen", itemId)
                                .with(asUser(graderId, "grader", Permissions.GRADING_MODIFY))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andReturn();
    }

    private static void assertNoLearnerLeak(String rawJson, UUID studentId) {
        assertThat(rawJson)
                .doesNotContain(studentId.toString())
                .doesNotContain("SV-SECRET-9988")
                .doesNotContain("secret.learner.username")
                .doesNotContain("Nguyễn Văn Bí Mật")
                .doesNotContain("secret.learner@thi360.test")
                .doesNotContain("0912345678");
    }

    private void drainAllOutbox() {
        int batch;
        do {
            batch = outboxRelay.relayBatch(500);
        } while (batch > 0);
    }
}
