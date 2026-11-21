package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.attempt.api.AttemptChoiceController.ConfirmPartChoiceRequest;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptEvent;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.PartState;
import vn.unicom.thi360.commerce.entitlement.application.GrantOrderResult;
import vn.unicom.thi360.exam.spi.PaperSolverPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Combination;
import vn.unicom.thi360.exam.spi.PublishedExamPort.CombinationSubject;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Integration tests for Part 3 choice flows on HSA FULL and QDA FULL, 10-triple 17-17-16 split +
 * trimmed question auditing, reload determinism, exclusion of issued questions, single-option
 * explicit confirm, and usage ledger recording (AC-1..AC-5, AC-19, regression).
 */
class AttemptChoiceFlowIT extends AbstractAttemptDsIT {

    @MockitoSpyBean private PaperSolverPort paperSolverPort;

    @AfterEach
    void resetSolverSpy() {
        reset(paperSolverPort);
    }

    @Test
    void hsaEnglishBranch() throws Exception {
        UUID userId = createStudent("hsa-eng");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL", 2, List.of());
        Attempt attempt = advanceToPartThreeAwaitingChoice(userId, "hsa-eng", line.entitlementId());
        List<AttemptPart> parts = attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId());
        AttemptPart p2 = parts.get(2);
        assertThat(p2.getState()).isEqualTo(PartState.AWAITING_CHOICE);

        MvcResult choiceRes =
                performChoice(
                        userId,
                        attempt.getId(),
                        p2.getId(),
                        "hsa-eng-choice",
                        null,
                        new ConfirmPartChoiceRequest("ANH", null));
        assertThat(choiceRes.getResponse().getStatus()).isEqualTo(202);
        JsonNode choiceJson = json(choiceRes);
        assertThat(choiceJson.path("state").asText()).isEqualTo("GENERATING");
        assertThat(choiceJson.path("choice").path("optionCode").asText()).isEqualTo("ANH");
        assertThat(choiceJson.path("choice").has("seed")).isFalse();
        assertThat(choiceJson.path("choice").has("subjectCodes")).isFalse();
        assertThat(choiceJson.path("choice").has("subjectCounts")).isFalse();

        AttemptPart opened = awaitPartState(p2.getId(), PartState.OPEN);
        assertThat(opened.getChoice()).containsEntry("optionCode", "ANH");

        AttemptEvent confirmedEvent =
                attemptEventRepository.findByAttemptIdOrderByAtAscIdAsc(attempt.getId()).stream()
                        .filter(
                                e ->
                                        e.getType() == AttemptEventType.CHOICE_CONFIRMED
                                                && p2.getId().equals(e.getPartId()))
                        .findFirst()
                        .orElseThrow();
        assertThat(confirmedEvent.getData())
                .containsEntry("partCode", p2.getPartCode())
                .containsEntry("ord", p2.getOrd())
                .containsEntry("optionCode", "ANH")
                .containsEntry("choiceConfirmedAt", opened.getChoiceConfirmedAt().toString());
        assertThat(((Number) confirmedEvent.getData().get("seed")).longValue())
                .isEqualTo(opened.getSeed());

        MvcResult stateRes = performGetState(userId, attempt.getId());
        assertThat(stateRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode p2View = json(stateRes).path("parts").get(2);
        assertThat(p2View.path("state").asText()).isEqualTo("OPEN");
        int units = 0;
        for (JsonNode item : p2View.path("items")) {
            units += item.path("unitCount").asInt();
        }
        assertThat(units).isEqualTo(50);
    }

    @Test
    void hsaScienceAllTenTriples() throws Exception {
        List<List<String>> triples =
                List.of(
                        List.of("LY", "HOA", "SINH"),
                        List.of("LY", "HOA", "SU"),
                        List.of("LY", "HOA", "DIA"),
                        List.of("LY", "SINH", "SU"),
                        List.of("LY", "SINH", "DIA"),
                        List.of("HOA", "SINH", "SU"),
                        List.of("HOA", "SINH", "DIA"),
                        List.of("LY", "SU", "DIA"),
                        List.of("HOA", "SU", "DIA"),
                        List.of("SINH", "SU", "DIA"));
        Set<String> natural = Set.of("LY", "HOA", "SINH");
        Set<String> social = Set.of("SU", "DIA");

        for (int i = 0; i < triples.size(); i++) {
            List<String> triple = triples.get(i);
            UUID userId = createStudent("hsa-sci-" + i);
            GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL", 2, List.of());
            Attempt attempt =
                    advanceToPartThreeAwaitingChoice(userId, "sci-" + i, line.entitlementId());
            AttemptPart p2 =
                    attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).get(2);

            MvcResult choiceRes =
                    performChoice(
                            userId,
                            attempt.getId(),
                            p2.getId(),
                            "sci-choice-" + i,
                            null,
                            new ConfirmPartChoiceRequest("SCIENCE", triple));
            assertThat(choiceRes.getResponse().getStatus()).isEqualTo(202);
            JsonNode choiceResJson = json(choiceRes);
            assertThat(choiceResJson.path("choice").has("seed")).isFalse();
            assertThat(choiceResJson.path("choice").path("optionCode").asText())
                    .isEqualTo("SCIENCE");
            assertThat(choiceResJson.path("choice").path("subjectCodes")).hasSize(3);
            assertThat(choiceResJson.path("choice").path("subjectCounts")).hasSize(3);

            AttemptPart opened = awaitPartState(p2.getId(), PartState.OPEN);
            @SuppressWarnings("unchecked")
            Map<String, Number> savedCounts =
                    (Map<String, Number>) opened.getChoice().get("subjectCounts");
            assertThat(savedCounts).hasSize(3);

            Map<String, Integer> expectedCounts = new LinkedHashMap<>();
            long natCount = triple.stream().filter(natural::contains).count();
            long socCount = triple.stream().filter(social::contains).count();
            if (natCount > 0 && socCount > 0) {
                boolean minorityNatural = natCount == 1;
                for (String s : triple) {
                    expectedCounts.put(s, (minorityNatural == natural.contains(s)) ? 16 : 17);
                }
            } else {
                int lowIdx = new Random(opened.getSeed()).nextInt(triple.size());
                for (int idx = 0; idx < triple.size(); idx++) {
                    expectedCounts.put(triple.get(idx), idx == lowIdx ? 16 : 17);
                }
            }
            for (String s : triple) {
                assertThat(savedCounts.get(s).intValue()).isEqualTo(expectedCounts.get(s));
                assertThat(choiceResJson.path("choice").path("subjectCounts").path(s).asInt())
                        .isEqualTo(expectedCounts.get(s));
            }

            AttemptEvent confirmedEvent =
                    attemptEventRepository.findByAttemptIdOrderByAtAscIdAsc(attempt.getId()).stream()
                            .filter(
                                    e ->
                                            e.getType() == AttemptEventType.CHOICE_CONFIRMED
                                                    && p2.getId().equals(e.getPartId()))
                            .findFirst()
                            .orElseThrow();
            assertThat(confirmedEvent.getData())
                    .containsEntry("partCode", p2.getPartCode())
                    .containsEntry("ord", p2.getOrd())
                    .containsEntry("optionCode", "SCIENCE")
                    .containsEntry("choiceConfirmedAt", opened.getChoiceConfirmedAt().toString())
                    .containsEntry("subjectCodes", triple);
            assertThat(((Number) confirmedEvent.getData().get("seed")).longValue())
                    .isEqualTo(opened.getSeed());
            @SuppressWarnings("unchecked")
            Map<String, Number> eventCounts =
                    (Map<String, Number>) confirmedEvent.getData().get("subjectCounts");
            expectedCounts.forEach(
                    (subj, cnt) -> assertThat(eventCounts.get(subj).intValue()).isEqualTo(cnt));

            List<AttemptItem> p2Items = attemptItemRepository.findByPartIdOrderByOrdAsc(p2.getId());
            Map<String, Integer> actualUnitsBySubject = new LinkedHashMap<>();
            Set<UUID> keptQuestionIds = new LinkedHashSet<>();
            for (AttemptItem item : p2Items) {
                keptQuestionIds.add(item.getQuestionId());
                if (item.getQuestionType() != QuestionType.QUESTION_SET) {
                    actualUnitsBySubject.merge(item.getComponentCode(), item.getUnitCount(), Integer::sum);
                }
            }
            assertThat(actualUnitsBySubject).isEqualTo(expectedCounts);
            assertThat(actualUnitsBySubject.values().stream().mapToInt(Integer::intValue).sum())
                    .isEqualTo(50);

            AttemptEvent openedEvent =
                    attemptEventRepository.findByAttemptIdOrderByAtAscIdAsc(attempt.getId()).stream()
                            .filter(
                                    e ->
                                            e.getType() == AttemptEventType.PART_OPENED
                                                    && p2.getId().equals(e.getPartId()))
                            .findFirst()
                            .orElseThrow();
            @SuppressWarnings("unchecked")
            List<String> droppedIds =
                    (List<String>) openedEvent.getData().get("droppedQuestionIds");
            assertThat(droppedIds).hasSize(1);
            UUID droppedId = UUID.fromString(droppedIds.getFirst());
            assertThat(keptQuestionIds).doesNotContain(droppedId);
            String rawEventJson = objectMapper.writeValueAsString(openedEvent.getData());
            assertThat(rawEventJson).doesNotContain("content").doesNotContain("answers");
        }

        // Verify 3-natural (LY, HOA, SINH) seed selects each of the 3 positions for the 16-question subject
        long[] seedsForEachIndex = findSeedsForThreeIndexes();
        for (int lowIdx = 0; lowIdx < 3; lowIdx++) {
            UUID userId = createStudent("hsa-3nat-seed-" + lowIdx);
            GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL", 2, List.of());
            Attempt attempt =
                    advanceToPartThreeAwaitingChoice(userId, "3nat-" + lowIdx, line.entitlementId());
            AttemptPart p2 =
                    attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).get(2);
            jdbc.update(
                    "UPDATE attempt.attempt_part SET seed = ? WHERE id = ?",
                    seedsForEachIndex[lowIdx],
                    p2.getId());

            MvcResult res =
                    performChoice(
                            userId,
                            attempt.getId(),
                            p2.getId(),
                            "3nat-choice-" + lowIdx,
                            null,
                            new ConfirmPartChoiceRequest("SCIENCE", List.of("SINH", "LY", "HOA")));
            assertThat(res.getResponse().getStatus()).isEqualTo(202);
            JsonNode resJson = json(res);
            assertThat(resJson.path("choice").has("seed")).isFalse();
            AttemptPart opened = awaitPartState(p2.getId(), PartState.OPEN);
            List<String> canonicalOrder = List.of("LY", "HOA", "SINH");
            assertThat(opened.getChoice().get("subjectCodes")).isEqualTo(canonicalOrder);
            String expectedTrimmed = canonicalOrder.get(lowIdx);

            Map<String, Integer> actualBySubject = new LinkedHashMap<>();
            for (AttemptItem item : attemptItemRepository.findByPartIdOrderByOrdAsc(p2.getId())) {
                if (item.getQuestionType() != QuestionType.QUESTION_SET) {
                    actualBySubject.merge(item.getComponentCode(), item.getUnitCount(), Integer::sum);
                }
            }
            assertThat(actualBySubject.get(expectedTrimmed)).isEqualTo(16);
        }
    }

    @Test
    void reloadKeepsSameFiftyQuestions() throws Exception {
        UUID userId = createStudent("reload-50");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL", 2, List.of());
        Attempt attempt = advanceToPartThreeAwaitingChoice(userId, "reload", line.entitlementId());
        AttemptPart p2 =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).get(2);

        assertThat(
                        performChoice(
                                        userId,
                                        attempt.getId(),
                                        p2.getId(),
                                        "reload-ch",
                                        null,
                                        new ConfirmPartChoiceRequest("ANH", null))
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);
        awaitPartState(p2.getId(), PartState.OPEN);

        JsonNode firstItems = json(performGetState(userId, attempt.getId())).path("parts").get(2).path("items");
        JsonNode secondItems = json(performGetState(userId, attempt.getId())).path("parts").get(2).path("items");

        assertThat(secondItems).isEqualTo(firstItems);
        int units = 0;
        for (JsonNode item : firstItems) {
            units += item.path("unitCount").asInt();
        }
        assertThat(units).isEqualTo(50);
    }

    @Test
    void qdaQd02SubjectOrder() throws Exception {
        UUID userId = createStudent("qda-qd02");
        GrantOrderResult.Line line = grant(userId, "DS-QDA-FULL", 2, List.of());
        Attempt attempt = advanceToPartThreeAwaitingChoice(userId, "qda-02", line.entitlementId());
        AttemptPart p2 =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).get(2);

        PublishedVersion version =
                publishedExamPort
                        .loadVersion(attempt.getExamId(), attempt.getExamVersionNo())
                        .orElseThrow();
        Combination qd02 =
                version.combinations().stream()
                        .filter(c -> "QD02".equals(c.code()))
                        .findFirst()
                        .orElseThrow();
        List<String> expectedCombOrder =
                qd02.subjects().stream()
                        .sorted(java.util.Comparator.comparingInt(CombinationSubject::position))
                        .map(CombinationSubject::code)
                        .toList();
        assertThat(expectedCombOrder).containsExactly("HOA", "SINH");

        MvcResult res =
                performChoice(
                        userId,
                        attempt.getId(),
                        p2.getId(),
                        "qda-qd02-ch",
                        null,
                        new ConfirmPartChoiceRequest("QD02", null));
        assertThat(res.getResponse().getStatus()).isEqualTo(202);
        awaitPartState(p2.getId(), PartState.OPEN);

        List<AttemptItem> p2Items = attemptItemRepository.findByPartIdOrderByOrdAsc(p2.getId());
        List<AttemptItem> numbered =
                p2Items.stream().filter(i -> i.getDisplayNo() != null).toList();
        int cumulativeUnits = 0;
        Set<String> firstHalf = new LinkedHashSet<>();
        Set<String> secondHalf = new LinkedHashSet<>();
        for (AttemptItem item : numbered) {
            int itemUnits = item.getUnitCount();
            assertThat(itemUnits).isPositive();
            int startUnit = cumulativeUnits + 1;
            int endUnit = cumulativeUnits + itemUnits;
            assertThat(startUnit <= 25 && endUnit > 25)
                    .as("No item should straddle the 25/26 unit boundary")
                    .isFalse();
            if (endUnit <= 25) {
                firstHalf.add(item.getComponentCode());
            } else {
                secondHalf.add(item.getComponentCode());
            }
            cumulativeUnits = endUnit;
        }
        assertThat(cumulativeUnits).isEqualTo(50);
        assertThat(firstHalf).containsExactly("HOA");
        assertThat(secondHalf).containsExactly("SINH");
    }

    @Test
    void partThreeExcludesIssuedQuestions() throws Exception {
        UUID userId = createStudent("exclude-issued");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = advanceToPartThreeAwaitingChoice(userId, "excl", line.entitlementId());
        AttemptPart p2 =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).get(2);

        Set<UUID> issuedBeforeP3 = new LinkedHashSet<>();
        for (AttemptItem item :
                attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attempt.getId())) {
            issuedBeforeP3.add(item.getQuestionId());
        }
        assertThat(issuedBeforeP3).isNotEmpty();

        AtomicReference<Set<UUID>> capturedExcludes = new AtomicReference<>();
        doAnswer(
                        inv -> {
                            List<String> partCodes = inv.getArgument(2);
                            if (partCodes.contains(p2.getPartCode())) {
                                @SuppressWarnings("unchecked")
                                Set<UUID> excl = inv.getArgument(5);
                                capturedExcludes.set(Set.copyOf(excl));
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
                                        "excl-ch",
                                        null,
                                        new ConfirmPartChoiceRequest("ANH", null))
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);
        awaitPartState(p2.getId(), PartState.OPEN);

        assertThat(capturedExcludes.get()).containsAll(issuedBeforeP3);
        List<UUID> p3QuestionIds =
                attemptItemRepository.findByPartIdOrderByOrdAsc(p2.getId()).stream()
                        .map(AttemptItem::getQuestionId)
                        .toList();
        assertThat(p3QuestionIds).doesNotContainAnyElementsOf(issuedBeforeP3);
    }

    @Test
    void singleOptionStillRequiresConfirm() throws Exception {
        // 1. Single granted option ["ANH"] on DS-HSA-FULL-MINI
        UUID u1 = createStudent("single-opt-hsa");
        GrantOrderResult.Line hsaLine = grant(u1, "DS-HSA-FULL-MINI", 2, List.of("ANH"));
        Attempt a1 = advanceToPartThreeAwaitingChoice(u1, "so-hsa", hsaLine.entitlementId());
        AttemptPart hsaP2 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a1.getId()).get(2);
        assertThat(hsaP2.getState()).isEqualTo(PartState.AWAITING_CHOICE);
        assertThat(attemptItemRepository.findByPartIdOrderByOrdAsc(hsaP2.getId())).isEmpty();
        assertThat(
                        json(performGetState(u1, a1.getId()))
                                .path("parts")
                                .get(2)
                                .path("state")
                                .asText())
                .isEqualTo("AWAITING_CHOICE");

        MvcResult badHsaChoice =
                performChoice(
                        u1,
                        a1.getId(),
                        hsaP2.getId(),
                        "so-hsa-bad",
                        null,
                        new ConfirmPartChoiceRequest("SCIENCE", List.of("LY", "HOA", "SINH")));
        assertThat(badHsaChoice.getResponse().getStatus()).isEqualTo(422);
        assertThat(json(badHsaChoice).path("code").asText()).isEqualTo("CHOICE_NOT_ALLOWED");

        assertThat(
                        performChoice(
                                        u1,
                                        a1.getId(),
                                        hsaP2.getId(),
                                        "so-hsa-ok",
                                        null,
                                        new ConfirmPartChoiceRequest("ANH", null))
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);
        awaitPartState(hsaP2.getId(), PartState.OPEN);
        assertThat(attemptItemRepository.findByPartIdOrderByOrdAsc(hsaP2.getId())).isNotEmpty();

        // 2. Single granted combination ["QD02"] on DS-QDA-FULL-MINI
        UUID u2 = createStudent("single-opt-qda");
        GrantOrderResult.Line qdaLine = grant(u2, "DS-QDA-FULL-MINI", 2, List.of("QD02"));
        Attempt a2 = advanceToPartThreeAwaitingChoice(u2, "so-qda", qdaLine.entitlementId());
        AttemptPart qdaP2 = attemptPartRepository.findByAttemptIdOrderByOrdAsc(a2.getId()).get(2);
        assertThat(qdaP2.getState()).isEqualTo(PartState.AWAITING_CHOICE);
        assertThat(attemptItemRepository.findByPartIdOrderByOrdAsc(qdaP2.getId())).isEmpty();

        MvcResult badQdaChoice =
                performChoice(
                        u2,
                        a2.getId(),
                        qdaP2.getId(),
                        "so-qda-bad",
                        null,
                        new ConfirmPartChoiceRequest("QD01", null));
        assertThat(badQdaChoice.getResponse().getStatus()).isEqualTo(422);
        assertThat(json(badQdaChoice).path("code").asText()).isEqualTo("CHOICE_NOT_ALLOWED");

        assertThat(
                        performChoice(
                                        u2,
                                        a2.getId(),
                                        qdaP2.getId(),
                                        "so-qda-ok",
                                        null,
                                        new ConfirmPartChoiceRequest("QD02", null))
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);
        awaitPartState(qdaP2.getId(), PartState.OPEN);
        assertThat(attemptItemRepository.findByPartIdOrderByOrdAsc(qdaP2.getId())).isNotEmpty();
    }

    @Test
    void partThreeRecordedInUsageLedger() throws Exception {
        UUID userId = createStudent("usage-ledger-p3");
        GrantOrderResult.Line line = grant(userId, "DS-HSA-FULL-MINI", 2, List.of());
        Attempt attempt = advanceToPartThreeAwaitingChoice(userId, "ledger", line.entitlementId());
        AttemptPart p2 =
                attemptPartRepository.findByAttemptIdOrderByOrdAsc(attempt.getId()).get(2);

        assertThat(
                        performChoice(
                                        userId,
                                        attempt.getId(),
                                        p2.getId(),
                                        "ledger-ch",
                                        null,
                                        new ConfirmPartChoiceRequest("ANH", null))
                                .getResponse()
                                .getStatus())
                .isEqualTo(202);
        awaitPartState(p2.getId(), PartState.OPEN);

        List<AttemptItem> rootItems =
                attemptItemRepository.findByPartIdOrderByOrdAsc(p2.getId()).stream()
                        .filter(i -> i.getParentItemId() == null)
                        .toList();
        assertThat(rootItems).isNotEmpty();
        for (AttemptItem root : rootItems) {
            Integer ledgerCount =
                    jdbc.queryForObject(
                            """
                            SELECT COUNT(*) FROM questionbank.question_usage_ledger
                            WHERE question_id = ? AND version_no = ? AND kind = 'ATTEMPT' AND exam_id = ?
                            """,
                            Integer.class,
                            root.getQuestionId(),
                            root.getQuestionVersionNo(),
                            attempt.getExamId());
            assertThat(ledgerCount).isEqualTo(1);
        }
    }

    private Attempt advanceToPartThreeAwaitingChoice(
            UUID userId, String keyPrefix, UUID entitlementId) throws Exception {
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

    private static long[] findSeedsForThreeIndexes() {
        long[] found = new long[3];
        boolean[] seen = new boolean[3];
        for (long seed = 1; seen[0] == false || seen[1] == false || seen[2] == false; seed++) {
            int idx = new Random(seed).nextInt(3);
            if (!seen[idx]) {
                seen[idx] = true;
                found[idx] = seed;
            }
        }
        return found;
    }
}
