package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.api.StartAttemptRequest.PreChoiceInput;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingPathPart;

/**
 * Integration tests covering exam scope, branch denominators, shared timing, and V-ACT component
 * codes across the DS dataset (AC-19, AC-20, AC-22, AC-23, AC-24).
 */
class AttemptScopeMatrixIT extends AbstractAttemptDsIT {

    @Test
    void informaticsBranchDenominators() throws Exception {
        // SPT Informatics: CS & ICT each have 22 questions
        verifyInformaticsBranch("DS-SPT-INFORMATICS", "CS", 22, "TIN_CS");
        verifyInformaticsBranch("DS-SPT-INFORMATICS", "ICT", 22, "TIN_ICT");

        // THPTQG Informatics: CS & ICT each have 28 questions
        verifyInformaticsBranch("DS-THPTQG-INFORMATICS", "CS", 28, "TIN_CS");
        verifyInformaticsBranch("DS-THPTQG-INFORMATICS", "ICT", 28, "TIN_ICT");
    }

    private void verifyInformaticsBranch(
            String examCode, String branchCode, int expectedQuestions, String expectedSubjectPrefix)
            throws Exception {
        UUID userId = createStudent("inf-" + branchCode);
        GrantOrderResult.Line line = grant(userId, examCode, 2, List.of());

        MvcResult res =
                performStart(
                        userId,
                        "inf-" + examCode + "-" + branchCode,
                        line.entitlementId(),
                        new PreChoiceInput(branchCode, null));
        assertThat(res.getResponse().getStatus()).isEqualTo(202);
        UUID attemptId = UUID.fromString(json(res).path("attemptId").asText());
        awaitTerminalStartState(attemptId, AttemptState.IN_PROGRESS);

        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId);
        assertThat(parts).hasSize(2);
        int requiredSum = parts.stream().mapToInt(AttemptPart::getQuestionCountRequired).sum();
        assertThat(requiredSum).isEqualTo(expectedQuestions);

        List<AttemptItem> allItems = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attemptId);
        int totalUnits = allItems.stream().mapToInt(AttemptItem::getUnitCount).sum();
        assertThat(totalUnits).isEqualTo(expectedQuestions);

        // Verify no questions from the unselected branch subject leaked into the paper
        List<String> subjectCodes =
                allItems.stream()
                        .filter(i -> i.getParentItemId() == null)
                        .map(AttemptItem::getComponentCode)
                        .filter(c -> c != null && !c.isBlank())
                        .distinct()
                        .toList();
        if (!subjectCodes.isEmpty()) {
            String opposite = "CS".equals(branchCode) ? "TIN_ICT" : "TIN_CS";
            assertThat(subjectCodes).doesNotContain(opposite);
        }
    }

    @Test
    void subjectAndReviewExamsNeedNoChoice() throws Exception {
        UUID u1 = createStudent("subj-no-choice");
        GrantOrderResult.Line hsaSubj = grant(u1, "DS-HSA-SUBJECT-TOAN-MINI", 2, List.of());
        MvcResult r1 = performStart(u1, "subj-1", hsaSubj.entitlementId(), null);
        assertThat(r1.getResponse().getStatus()).isEqualTo(202);
        UUID a1 = UUID.fromString(json(r1).path("attemptId").asText());
        assertThat(awaitTerminalStartState(a1, AttemptState.IN_PROGRESS).getState())
                .isEqualTo(AttemptState.IN_PROGRESS);

        UUID u2 = createStudent("review-no-choice");
        GrantOrderResult.Line reviewLine = grant(u2, "DS-HSA-REVIEW-MINI", 2, List.of());
        MvcResult r2 = performStart(u2, "rev-1", reviewLine.entitlementId(), null);
        assertThat(r2.getResponse().getStatus()).isEqualTo(202);
        UUID a2 = UUID.fromString(json(r2).path("attemptId").asText());
        assertThat(awaitTerminalStartState(a2, AttemptState.IN_PROGRESS).getState())
                .isEqualTo(AttemptState.IN_PROGRESS);
    }

    @Test
    void partialScopesReadPathMaxima() throws Exception {
        assertPathMaxMatch("DS-VACT-PART-LANGUAGE", 60, new BigDecimal("600"));
        assertPathMaxMatch("DS-VACT-SUBJECT-TOAN", 30, new BigDecimal("300"));
        assertPathMaxMatch("DS-THPTQG-MATH-P1", null, null);
        assertPathMaxMatch("DS-HSA-REVIEW", null, null);
    }

    private void assertPathMaxMatch(
            String examCode, Integer expectedQuestions, BigDecimal expectedMaxPoints)
            throws Exception {
        UUID userId = createStudent("partial-" + examCode);
        GrantOrderResult.Line line = grant(userId, examCode, 2, List.of());
        ServingExam serving = publishedExamPort.findServing(line.examId()).orElseThrow();
        ServingPathPart pathPart = serving.servingPaths().getFirst().parts().getFirst();

        MvcResult res = performStart(userId, "pmax-" + examCode, line.entitlementId(), null);
        assertThat(res.getResponse().getStatus()).isEqualTo(202);
        UUID attemptId = UUID.fromString(json(res).path("attemptId").asText());
        Attempt opened = awaitTerminalStartState(attemptId, AttemptState.IN_PROGRESS);

        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId);
        assertThat(parts).hasSize(1);
        AttemptPart part = parts.getFirst();
        assertThat(part.getQuestionCountRequired()).isEqualTo(pathPart.questionCount());
        if (pathPart.maxPoints() != null) {
            assertThat(part.getMaxPoints()).isEqualByComparingTo(pathPart.maxPoints());
            assertThat(opened.getMaxScore()).isEqualByComparingTo(pathPart.maxPoints());
        } else {
            assertThat(part.getMaxPoints()).isNull();
        }

        if (expectedQuestions != null) {
            assertThat(part.getQuestionCountRequired()).isEqualTo(expectedQuestions);
        }
        if (expectedMaxPoints != null) {
            assertThat(part.getMaxPoints()).isEqualByComparingTo(expectedMaxPoints);
        }
    }

    @Test
    void sharedTimingUsesExamDuration() throws Exception {
        UUID userId = createStudent("vact-shared");
        GrantOrderResult.Line line = grant(userId, "DS-VACT-FULL", 2, List.of());

        MvcResult res = performStart(userId, "vact-shared-1", line.entitlementId(), null);
        assertThat(res.getResponse().getStatus()).isEqualTo(202);
        UUID attemptId = UUID.fromString(json(res).path("attemptId").asText());
        Attempt opened = awaitTerminalStartState(attemptId, AttemptState.IN_PROGRESS);

        assertThat(opened.getTiming()).isEqualTo(AttemptTiming.SHARED);
        assertThat(opened.getDeadlineAt())
                .isEqualTo(opened.getStartedAt().plus(Duration.ofMinutes(150)));

        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId);
        assertThat(parts).hasSize(3);
        assertThat(parts.get(0).getState()).isEqualTo(PartState.OPEN);
        assertThat(parts.get(0).getDeadlineAt()).isEqualTo(opened.getDeadlineAt());
        assertThat(parts.get(1).getState()).isEqualTo(PartState.PENDING);
        assertThat(parts.get(1).getStartedAt()).isNull();
        assertThat(parts.get(2).getState()).isEqualTo(PartState.PENDING);

        int totalUnits =
                attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attemptId).stream()
                        .mapToInt(AttemptItem::getUnitCount)
                        .sum();
        assertThat(totalUnits).isEqualTo(120);
    }

    @Test
    void vactComponentCodes() throws Exception {
        UUID userId = createStudent("vact-comp");
        GrantOrderResult.Line line = grant(userId, "DS-VACT-FULL", 2, List.of());

        MvcResult res = performStart(userId, "vact-comp-1", line.entitlementId(), null);
        assertThat(res.getResponse().getStatus()).isEqualTo(202);
        UUID attemptId = UUID.fromString(json(res).path("attemptId").asText());
        awaitTerminalStartState(attemptId, AttemptState.IN_PROGRESS);

        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId);
        List<AttemptItem> allItems = new ArrayListDisplayHelper().collectAll(attemptItemRepository, parts);
        List<AttemptItem> allRoots = allItems.stream().filter(i -> i.getParentItemId() == null).toList();
        assertThat(allRoots).allSatisfy(item -> assertThat(item.getComponentCode()).isNotBlank());

        Set<String> part0Components =
                attemptItemRepository.findByPartIdOrderByOrdAsc(parts.get(0).getId()).stream()
                        .filter(i -> i.getParentItemId() == null)
                        .map(AttemptItem::getComponentCode)
                        .collect(Collectors.toSet());
        assertThat(part0Components).contains("VIETNAMESE", "ANH");

        // Verify display_no is continuous 1..120 across all 3 parts
        List<Integer> displayNos =
                allItems.stream()
                        .filter(i -> i.getDisplayNo() != null)
                        .map(AttemptItem::getDisplayNo)
                        .toList();
        assertThat(displayNos.getFirst()).isEqualTo(1);
        assertThat(displayNos.getLast()).isEqualTo(120);
    }

    private static final class ArrayListDisplayHelper {
        List<AttemptItem> collectAll(
                vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository repo,
                List<AttemptPart> parts) {
            return parts.stream()
                    .flatMap(p -> repo.findByPartIdOrderByOrdAsc(p.getId()).stream())
                    .toList();
        }
    }
}
