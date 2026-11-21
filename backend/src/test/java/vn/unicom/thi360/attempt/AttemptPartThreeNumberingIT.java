package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.unicom.thi360.attempt.api.AttemptChoiceController.ConfirmPartChoiceRequest;
import vn.unicom.thi360.attempt.api.AttemptChoiceController.RetryPartRequest;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.exam.spi.PaperGenerationException;
import vn.unicom.thi360.exam.spi.PaperSolverPort;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Integration tests verifying continuous cross-part {@code display_no} numbering into Part 3 on
 * HSA FULL and QDA FULL, including after generation failure and retry (AC-22, BS §5).
 */
class AttemptPartThreeNumberingIT extends AbstractAttemptDsIT {

    @MockitoSpyBean private PaperSolverPort paperSolverPort;

    @AfterEach
    void resetSpy() {
        reset(paperSolverPort);
    }

    @Test
    void partThreeContinuesNumbering() throws Exception {
        // 1. HSA FULL: Parts 0+1 = 1..100, Part 2 = 101..150
        UUID u1 = createStudent("num-hsa");
        GrantOrderResult.Line hsaLine = grant(u1, "DS-HSA-FULL", 2, List.of());
        Attempt hsa = advanceToAwaitingChoice(u1, "num-hsa", hsaLine.entitlementId());
        List<AttemptPart> hsaParts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(hsa.getId());
        AttemptPart hsaP2 = hsaParts.get(2);

        assertThat(
                        performChoice(
                                        u1,
                                        hsa.getId(),
                                        hsaP2.getId(),
                                        "num-hsa-ch",
                                        null,
                                        new ConfirmPartChoiceRequest("ANH", null))
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);
        awaitPartState(hsaP2.getId(), PartState.OPEN);
        assertContiguousDisplayNumbers(hsa.getId(), hsaParts, 150, 100);

        // 2. QDA FULL: Parts 0+1 = 1..100, Part 2 (QD02) = 101..150
        UUID u2 = createStudent("num-qda");
        GrantOrderResult.Line qdaLine = grant(u2, "DS-QDA-FULL", 2, List.of());
        Attempt qda = advanceToAwaitingChoice(u2, "num-qda", qdaLine.entitlementId());
        List<AttemptPart> qdaParts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(qda.getId());
        AttemptPart qdaP2 = qdaParts.get(2);

        assertThat(
                        performChoice(
                                        u2,
                                        qda.getId(),
                                        qdaP2.getId(),
                                        "num-qda-ch",
                                        null,
                                        new ConfirmPartChoiceRequest("QD02", null))
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);
        awaitPartState(qdaP2.getId(), PartState.OPEN);
        assertContiguousDisplayNumbers(qda.getId(), qdaParts, 150, 100);
    }

    @Test
    void retryKeepsNumbering() throws Exception {
        UUID userId = createStudent("num-retry");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL", 2, List.of());
        Attempt attempt = advanceToAwaitingChoice(userId, "num-ret", line.entitlementId());
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        AttemptPart p2 = parts.get(2);

        AtomicInteger p2Calls = new AtomicInteger();
        doAnswer(
                        inv -> {
                            List<String> partCodes = inv.getArgument(2);
                            if (partCodes.contains(p2.getPartCode()) && p2Calls.incrementAndGet() == 1) {
                                throw new PaperGenerationException(
                                        PaperGenerationException.Reason.GENERATION_FAILED,
                                        "simulated fail before retry");
                            }
                            return inv.callRealMethod();
                        })
                .when(paperSolverPort)
                .generate(any(), anyInt(), any(), any(), anyLong(), any());

        assertThat(
                        performChoice(
                                        userId,
                                        attempt.getId(),
                                        p2.getId(),
                                        "num-ret-ch",
                                        null,
                                        new ConfirmPartChoiceRequest(
                                                "SCIENCE", List.of("LY", "HOA", "SU")))
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);
        awaitPartState(p2.getId(), PartState.GENERATION_FAILED);

        assertThat(
                        performRetry(
                                        userId,
                                        attempt.getId(),
                                        p2.getId(),
                                        "num-ret-2",
                                        null,
                                        new RetryPartRequest("retry numbering"))
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);
        awaitPartState(p2.getId(), PartState.OPEN);

        assertContiguousDisplayNumbers(attempt.getId(), parts, 150, 100);
    }

    private void assertContiguousDisplayNumbers(
            UUID attemptId, List<AttemptPart> parts, int totalExpectedUnits, int part2Max) {
        List<Integer> allNumbers = new ArrayList<>();
        List<Integer> part3Numbers = new ArrayList<>();
        for (int idx = 0; idx < parts.size(); idx++) {
            List<AttemptItem> items =
                    attemptItemRepository.findByPartIdOrderByOrdAsc(parts.get(idx).getId());
            for (AttemptItem item : items) {
                if (item.getQuestionType() == QuestionType.QUESTION_SET) {
                    assertThat(item.getDisplayNo()).isNull();
                    assertThat(item.getUnitCount()).isZero();
                } else {
                    assertThat(item.getDisplayNo()).isNotNull();
                    for (int u = 0; u < item.getUnitCount(); u++) {
                        int num = item.getDisplayNo() + u;
                        allNumbers.add(num);
                        if (idx == 2) {
                            part3Numbers.add(num);
                        }
                    }
                }
            }
        }
        List<Integer> expectedAll =
                java.util.stream.IntStream.rangeClosed(1, totalExpectedUnits).boxed().toList();
        List<Integer> expectedP3 =
                java.util.stream.IntStream.rangeClosed(part2Max + 1, totalExpectedUnits)
                        .boxed()
                        .toList();
        assertThat(allNumbers).containsExactlyElementsOf(expectedAll);
        assertThat(part3Numbers).containsExactlyElementsOf(expectedP3);
    }

    private Attempt advanceToAwaitingChoice(UUID userId, String keyPrefix, UUID entitlementId)
            throws Exception {
        Attempt attempt = startAndAwaitOpen(userId, keyPrefix, entitlementId, null);
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        assertThat(performFinishPart(userId, attempt.getId(), parts.get(0).getId(), null, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
        assertThat(performFinishPart(userId, attempt.getId(), parts.get(1).getId(), null, null)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
        return attempt;
    }
}
