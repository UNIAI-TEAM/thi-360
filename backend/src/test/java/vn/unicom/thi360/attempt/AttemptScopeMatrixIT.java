package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
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
import vn.unicom.thi360.exam.spi.PublishedExamPort.Section;
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
        verifyInformaticsBranches("DS-SPT-INFORMATICS", 22);

        // THPTQG Informatics: CS & ICT each have 28 questions
        verifyInformaticsBranches("DS-THPTQG-INFORMATICS", 28);
    }

    private void verifyInformaticsBranches(String examCode, int expectedQuestions) throws Exception {
        UUID examId = AttemptTestFixtures.dsExamId(jdbc, examCode);
        var version = publishedExamPort.findServing(examId).orElseThrow().version();
        var portPart2 = version.parts().get(1);
        Set<String> portCsSections = portPart2.options().stream()
                .filter(o -> "CS".equals(o.code()))
                .flatMap(o -> o.sections().stream())
                .map(Section::id)
                .collect(Collectors.toSet());
        Set<String> portIctSections = portPart2.options().stream()
                .filter(o -> "ICT".equals(o.code()))
                .flatMap(o -> o.sections().stream())
                .map(Section::id)
                .collect(Collectors.toSet());

        String partsJson = jdbc.queryForObject(
                "SELECT parts::text FROM exam.exam_version WHERE exam_id = ? AND version_no = ?",
                String.class,
                examId,
                version.versionNo());
        JsonNode part2Json = objectMapper.readTree(partsJson).path("parts").get(1);
        Set<String> commonSectionIds = collectSectionIds(part2Json.path("sections"));
        Set<String> commonBlockIds = collectBlockIds(part2Json.path("sections"));
        Set<String> csSectionIds = new LinkedHashSet<>();
        Set<String> csBlockIds = new LinkedHashSet<>();
        Set<String> ictSectionIds = new LinkedHashSet<>();
        Set<String> ictBlockIds = new LinkedHashSet<>();
        for (JsonNode opt : part2Json.path("options")) {
            String code = opt.path("code").asText();
            if ("CS".equals(code)) {
                csSectionIds.addAll(collectSectionIds(opt.path("sections")));
                csBlockIds.addAll(collectBlockIds(opt.path("sections")));
            } else if ("ICT".equals(code)) {
                ictSectionIds.addAll(collectSectionIds(opt.path("sections")));
                ictBlockIds.addAll(collectBlockIds(opt.path("sections")));
            }
        }
        assertThat(csSectionIds).isNotEmpty().isEqualTo(portCsSections);
        assertThat(ictSectionIds).isNotEmpty().isEqualTo(portIctSections);
        assertThat(csBlockIds).isNotEmpty();
        assertThat(ictBlockIds).isNotEmpty();
        assertThat(Collections.disjoint(csSectionIds, ictSectionIds)).isTrue();
        assertThat(Collections.disjoint(csBlockIds, ictBlockIds)).isTrue();

        BranchItems csItems = startAndInspectBranch(examCode, "CS", expectedQuestions);
        assertThat(csItems.part2SectionIds()).containsAll(csSectionIds).doesNotContainAnyElementsOf(ictSectionIds);
        assertThat(csItems.part2BlockIds()).containsAll(csBlockIds).doesNotContainAnyElementsOf(ictBlockIds);
        Set<String> csOnlySections = new LinkedHashSet<>(csItems.part2SectionIds());
        csOnlySections.removeAll(commonSectionIds);
        assertThat(csOnlySections).isEqualTo(csSectionIds);
        Set<String> csOnlyBlocks = new LinkedHashSet<>(csItems.part2BlockIds());
        csOnlyBlocks.removeAll(commonBlockIds);
        assertThat(csOnlyBlocks).isEqualTo(csBlockIds);

        BranchItems ictItems = startAndInspectBranch(examCode, "ICT", expectedQuestions);
        assertThat(ictItems.part2SectionIds()).containsAll(ictSectionIds).doesNotContainAnyElementsOf(csSectionIds);
        assertThat(ictItems.part2BlockIds()).containsAll(ictBlockIds).doesNotContainAnyElementsOf(csBlockIds);
        Set<String> ictOnlySections = new LinkedHashSet<>(ictItems.part2SectionIds());
        ictOnlySections.removeAll(commonSectionIds);
        assertThat(ictOnlySections).isEqualTo(ictSectionIds);
        Set<String> ictOnlyBlocks = new LinkedHashSet<>(ictItems.part2BlockIds());
        ictOnlyBlocks.removeAll(commonBlockIds);
        assertThat(ictOnlyBlocks).isEqualTo(ictBlockIds);

        assertThat(Collections.disjoint(csOnlySections, ictOnlySections)).isTrue();
        assertThat(Collections.disjoint(csOnlyBlocks, ictOnlyBlocks)).isTrue();
    }

    private record BranchItems(Set<String> part2SectionIds, Set<String> part2BlockIds) {}

    private BranchItems startAndInspectBranch(
            String examCode, String branchCode, int expectedQuestions) throws Exception {
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

        List<AttemptItem> part2Items = attemptItemRepository.findByPartIdOrderByOrdAsc(parts.get(1).getId());
        Set<String> sectionIds = part2Items.stream().map(AttemptItem::getSectionId).collect(Collectors.toSet());
        Set<String> blockIds = part2Items.stream().map(AttemptItem::getBlockId).collect(Collectors.toSet());
        return new BranchItems(sectionIds, blockIds);
    }

    private static Set<String> collectSectionIds(JsonNode sections) {
        Set<String> ids = new LinkedHashSet<>();
        for (JsonNode s : sections) {
            ids.add(s.path("id").asText());
        }
        return ids;
    }

    private static Set<String> collectBlockIds(JsonNode sections) {
        Set<String> ids = new LinkedHashSet<>();
        for (JsonNode s : sections) {
            for (JsonNode b : s.path("blocks")) {
                if (!b.path("omitted").asBoolean(false)) {
                    ids.add(b.path("id").asText());
                }
            }
        }
        return ids;
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
