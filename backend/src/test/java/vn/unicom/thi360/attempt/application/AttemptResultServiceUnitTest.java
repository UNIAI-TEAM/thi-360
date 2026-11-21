package vn.unicom.thi360.attempt.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import vn.unicom.thi360.attempt.api.AttemptResultResponse;
import vn.unicom.thi360.attempt.api.AttemptResultResponse.VactComponentView;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewClozeBlankResult;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewGradingDetail;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewItemView;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewMatchingPairResult;
import vn.unicom.thi360.attempt.api.AttemptReviewResponse.ReviewStatementResult;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptEventType;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptPart;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.EndReason;
import vn.unicom.thi360.attempt.domain.ItemOutcome;
import vn.unicom.thi360.attempt.domain.NavigationMode;
import vn.unicom.thi360.attempt.domain.PartCloseReason;
import vn.unicom.thi360.attempt.domain.grading.GradingPolicy;
import vn.unicom.thi360.attempt.domain.grading.ItemGradeResult;
import vn.unicom.thi360.attempt.domain.grading.ItemGrader;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator.VactComponentBreakdown;
import vn.unicom.thi360.attempt.domain.grading.ScoreAggregator.VactSubsectionBreakdown;
import vn.unicom.thi360.attempt.infrastructure.AttemptEventRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.catalog.spi.CatalogStatus;
import vn.unicom.thi360.commerce.spi.EntitlementBenefits;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Part;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Segment;
import vn.unicom.thi360.questionbank.spi.QuestionAssetContentPort;
import vn.unicom.thi360.questionbank.spi.QuestionAssetContentPort.AssetContent;
import vn.unicom.thi360.questionbank.spi.QuestionAssetContentPort.AssetKind;
import vn.unicom.thi360.questionbank.spi.QuestionAttachmentPort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Version;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.api.ForbiddenException;
import vn.unicom.thi360.shared.api.NotFoundException;

/**
 * Unit tests for {@link AttemptResultService} and {@link AttemptSolutionAssetService} (R1-7, R1-8,
 * R1-18, R1-20, R1-40, R1-41, R1-48, R1-51, R2-4, R2-6, R3-1).
 */
class AttemptResultServiceUnitTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AttemptAccessGate accessGate;
    private AttemptGuard attemptGuard;
    private AttemptPartRepository attemptPartRepository;
    private AttemptItemRepository attemptItemRepository;
    private AttemptEventRepository attemptEventRepository;
    private EntitlementPort entitlementPort;
    private QuestionSourcePort questionSourcePort;
    private QuestionAssetContentPort questionAssetContentPort;
    private PublishedExamPort publishedExamPort;
    private CatalogLookup catalogLookup;
    private AttemptResultService service;
    private AttemptSolutionAssetService solutionAssetService;

    @BeforeEach
    void setUp() {
        accessGate = mock(AttemptAccessGate.class);
        attemptGuard = mock(AttemptGuard.class);
        attemptPartRepository = mock(AttemptPartRepository.class);
        attemptItemRepository = mock(AttemptItemRepository.class);
        attemptEventRepository = mock(AttemptEventRepository.class);
        entitlementPort = mock(EntitlementPort.class);
        questionSourcePort = mock(QuestionSourcePort.class);
        QuestionAttachmentPort questionAttachmentPort = mock(QuestionAttachmentPort.class);
        questionAssetContentPort = mock(QuestionAssetContentPort.class);
        publishedExamPort = mock(PublishedExamPort.class);
        catalogLookup = mock(CatalogLookup.class);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage(
                "attempt.result.grading_error.status",
                Locale.forLanguageTag("vi"),
                "Bài làm đã được ghi nhận, kết quả đang được xử lý");
        messages.addMessage(
                "attempt.result.vact.subsection.logic_data",
                Locale.forLanguageTag("vi"),
                "Logic và phân tích số liệu");
        messages.addMessage(
                "attempt.result.vact.subsection.scientific_reasoning",
                Locale.forLanguageTag("vi"),
                "Suy luận khoa học");
        messages.addMessage(
                "attempt.result.vact.component.SCIENTIFIC_THINKING",
                Locale.forLanguageTag("vi"),
                "Tư duy khoa học");

        service = new AttemptResultService(
                accessGate,
                attemptGuard,
                attemptPartRepository,
                attemptItemRepository,
                attemptEventRepository,
                entitlementPort,
                questionSourcePort,
                questionAttachmentPort,
                publishedExamPort,
                catalogLookup,
                new AttemptQuestionSanitizer(),
                messages,
                txManager,
                new AttemptSegmentResultBuilder());

        solutionAssetService = new AttemptSolutionAssetService(
                accessGate,
                attemptGuard,
                attemptItemRepository,
                entitlementPort,
                questionSourcePort,
                questionAssetContentPort,
                txManager);
    }

    @Test
    void rewritesRelativeAbsoluteMixedCaseAndDataAssetSolutionUrls() {
        UUID attemptId = UUID.randomUUID();
        UUID id1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID id2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID id3 = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID id4 = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID id5 = UUID.fromString("55555555-5555-5555-5555-555555555555");

        String prefix = "/api/v1/me/attempts/" + attemptId + "/solution-assets/";
        String html = "<p>"
                + "<img src=\"/api/v1/admin/question-assets/" + id1 + "\">"
                + "<img src=\"https://admin.thi360.example/API/V1/ADMIN/QUESTION-ASSETS/" + id2 + "/content\">"
                + "<IMG data-asset-id=\"" + id3 + "\" src=\"placeholder.png\">"
                + "<a href=\"https://admin.thi360.example/api/v1/admin/question-assets/" + id4 + "/content\">Download</a>"
                + "<video poster=\"/api/v1/admin/question-assets/" + id5 + "\"></video>"
                + "Prose /api/v1/admin/question-assets/" + id1 + " kept"
                + "</p>";

        String rewritten = AttemptResultService.rewriteSolutionAssetUrls(attemptId, html);
        assertThat(rewritten).contains("<img src=\"" + prefix + id1 + "\">");
        assertThat(rewritten).contains("<img src=\"" + prefix + id2 + "\">");
        assertThat(rewritten).contains("<img data-asset-id=\"" + id3 + "\" src=\"" + prefix + id3 + "\">");
        assertThat(rewritten).contains("<a href=\"" + prefix + id4 + "\">Download</a>");
        assertThat(rewritten).contains("<video poster=\"" + prefix + id5 + "\"></video>");
        assertThat(rewritten).contains("Prose /api/v1/admin/question-assets/" + id1 + " kept");

        Set<UUID> extracted = new LinkedHashSet<>();
        AttemptSolutionAssetService.extractSolutionAssetIds(html, extracted);
        assertThat(extracted).containsExactlyInAnyOrder(id1, id2, id3, id4, id5);
    }

    @Test
    void vactReversedSubsectionOrderGetsDistinctLabels() {
        Locale prev = LocaleContextHolder.getLocale();
        LocaleContextHolder.setLocale(Locale.forLanguageTag("vi"));
        try {
            VactComponentBreakdown reversed = new VactComponentBreakdown(
                    "SCIENTIFIC_THINKING",
                    15,
                    0,
                    15,
                    0,
                    30,
                    new BigDecimal("50.00"),
                    List.of(
                            new VactSubsectionBreakdown("sec-18", 18, 10, 0, 8, 0, 18, new BigDecimal("55.56")),
                            new VactSubsectionBreakdown("sec-12", 12, 5, 0, 7, 0, 12, new BigDecimal("41.67"))));

            VactComponentView view = service.toVactComponentView(reversed, Map.of(), Locale.forLanguageTag("vi"));
            assertThat(view.correctRate()).isEqualByComparingTo("50.00");
            assertThat(view.subsections()).hasSize(2);
            assertThat(view.subsections().get(0).questionCount()).isEqualTo(18);
            assertThat(view.subsections().get(0).label()).isEqualTo("Suy luận khoa học");
            assertThat(view.subsections().get(0).correctRate()).isEqualByComparingTo("55.56");
            assertThat(view.subsections().get(1).questionCount()).isEqualTo(12);
            assertThat(view.subsections().get(1).label()).isEqualTo("Logic và phân tích số liệu");
            assertThat(view.subsections().get(1).correctRate()).isEqualByComparingTo("41.67");
        } finally {
            LocaleContextHolder.setLocale(prev);
        }
    }

    @Test
    void subjectFallbackUsesCatalogLookupByIdWithoutExtraFindItemCallWhenComponentCodeIsNull() {
        UUID userId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID partId = UUID.randomUUID();

        Attempt attempt = scoredHsaAttempt(attemptId, userId, subjectId);
        AttemptPart part = AttemptPart.createPending(
                partId, attemptId, "P1", "Vật lí", 0, 30, 1, new BigDecimal("10"), 1L);
        part.open(NOW, NOW.plusSeconds(1800));
        part.close(PartCloseReason.ATTEMPT_SUBMITTED, NOW.plusSeconds(100));

        // componentCode is null; only subjectItemId is set (R1-48)
        AttemptItem item = AttemptItem.create(
                UUID.randomUUID(), attemptId, partId, subjectId, "sec-1", null, "blk-1",
                null, 0, 1, 1, UUID.randomUUID(), 1, QuestionType.SINGLE_CHOICE, BigDecimal.ONE, List.of());
        item.recordGrading(BigDecimal.ONE, ItemOutcome.CORRECT, Map.of("exactFraction", "1"));

        when(accessGate.requireLearner()).thenReturn(userId);
        when(attemptGuard.requireOwnedAttempt(attemptId, userId)).thenReturn(attempt);
        when(entitlementPort.benefitsOf(attemptId))
                .thenReturn(new EntitlementBenefits(UUID.randomUUID(), attempt.getExamId(), Map.of(), "SCORE", List.of(), 1));
        when(attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId)).thenReturn(List.of(part));
        when(attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attemptId)).thenReturn(List.of(item));
        when(catalogLookup.listGroupItems("SUBJECT"))
                .thenReturn(List.of(new CatalogItemView(
                        subjectId,
                        UUID.randomUUID(),
                        "SUBJECT",
                        "LY",
                        "ly",
                        1,
                        "Vật lí",
                        "Physics",
                        CatalogStatus.ACTIVE,
                        CatalogStatus.ACTIVE,
                        true,
                        null,
                        (short) 0,
                        "LY")));

        Locale prev = LocaleContextHolder.getLocale();
        LocaleContextHolder.setLocale(Locale.forLanguageTag("vi"));
        try {
            AttemptResultResponse res = service.getResult(attemptId);
            assertThat(res.subjectCode()).isEqualTo("LY");
            assertThat(res.subjects()).hasSize(1);
            assertThat(res.subjects().getFirst().code()).isEqualTo("LY");
            assertThat(res.subjects().getFirst().name()).isEqualTo("Vật lí");
            verify(catalogLookup, never()).findItem(any());
        } finally {
            LocaleContextHolder.setLocale(prev);
        }
    }

    @Test
    void reviewRejectsNonAllowlistedResultViewsAndGradingErrorHidesScores() {
        UUID userId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        Attempt attempt = scoredHsaAttempt(attemptId, userId, null);

        when(accessGate.requireLearner()).thenReturn(userId);
        when(attemptGuard.requireOwnedAttempt(attemptId, userId)).thenReturn(attempt);

        for (String disallowed : new String[] {"SCORE", "UNKNOWN_TIER", null}) {
            if (disallowed == null) {
                ReflectionTestUtils.setField(attempt, "resultView", null);
                when(entitlementPort.benefitsOf(attemptId))
                        .thenReturn(new EntitlementBenefits(UUID.randomUUID(), attempt.getExamId(), Map.of(), null, List.of(), 1));
            } else {
                ReflectionTestUtils.setField(attempt, "resultView", disallowed);
                when(entitlementPort.benefitsOf(attemptId))
                        .thenReturn(new EntitlementBenefits(UUID.randomUUID(), attempt.getExamId(), Map.of(), disallowed, List.of(), 1));
            }
            assertThatThrownBy(() -> service.getReview(attemptId, null))
                    .isInstanceOf(ForbiddenException.class)
                    .satisfies(ex -> assertThat(((ForbiddenException) ex).getCode()).isEqualTo("REVIEW_NOT_INCLUDED"));
        }

        // R1-51: Attempt.markGradingError clears rawScore/displayScore and getResult hides scores on GRADING_ERROR
        ReflectionTestUtils.setField(attempt, "state", AttemptState.GRADING);
        ReflectionTestUtils.setField(attempt, "regrading", true);
        ReflectionTestUtils.setField(attempt, "rawScore", new BigDecimal("25.00"));
        ReflectionTestUtils.setField(attempt, "displayScore", new BigDecimal("25.00"));
        attempt.markGradingError("Internal DB error", NOW);

        assertThat(attempt.getState()).isEqualTo(AttemptState.GRADING_ERROR);
        assertThat(attempt.getRawScore()).isNull();
        assertThat(attempt.getDisplayScore()).isNull();

        when(entitlementPort.benefitsOf(attemptId))
                .thenReturn(new EntitlementBenefits(UUID.randomUUID(), attempt.getExamId(), Map.of(), "FULL_SOLUTION", List.of(), 1));
        when(attemptEventRepository.existsByAttemptIdAndType(attemptId, AttemptEventType.COMPENSATED)).thenReturn(false);
        when(attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId)).thenReturn(List.of());
        when(catalogLookup.listGroupItems("SUBJECT")).thenReturn(List.of());

        Locale prev = LocaleContextHolder.getLocale();
        LocaleContextHolder.setLocale(Locale.forLanguageTag("vi"));
        try {
            AttemptResultResponse errRes = service.getResult(attemptId);
            assertThat(errRes.state()).isEqualTo(AttemptState.GRADING_ERROR);
            assertThat(errRes.statusMessage()).isEqualTo("Bài làm đã được ghi nhận, kết quả đang được xử lý");
            assertThat(errRes.rawScore()).isNull();
            assertThat(errRes.displayScore()).isNull();
            assertThat(errRes.reviewAvailable()).isFalse();
        } finally {
            LocaleContextHolder.setLocale(prev);
        }
    }

    @Test
    void entitlementGrantResultViewOverridesDivergentAttemptResultView() {
        // R2-4 + R3-1: entitlement_grant.result_view overrides divergent attempt.result_view='FULL_SOLUTION'
        UUID userId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID partId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        Attempt attempt = scoredHsaAttempt(attemptId, userId, null);
        ReflectionTestUtils.setField(attempt, "resultView", "FULL_SOLUTION");

        AttemptPart part = AttemptPart.createPending(
                partId, attemptId, "P1", "Phần 1", 0, 30, 1, new BigDecimal("10.00"), 1L);
        part.open(NOW, NOW.plusSeconds(1800));
        part.close(PartCloseReason.ATTEMPT_SUBMITTED, NOW.plusSeconds(100));

        AttemptItem item = AttemptItem.create(
                UUID.randomUUID(), attemptId, partId, null, "sec-1", null, "blk-1",
                null, 0, 1, 1, questionId, 1, QuestionType.SINGLE_CHOICE, new BigDecimal("10.00"), List.of());
        item.recordGrading(new BigDecimal("10.00"), ItemOutcome.CORRECT, Map.of("exactFraction", "1"));

        JsonNode solutionSnap = MAPPER.createObjectNode()
                .put("solution", "<p>Lời giải <img data-asset-id=\"" + assetId + "\"></p>");
        AssetContent stubbedAsset = new AssetContent(
                assetId, AssetKind.IMAGE, "solution.png", "image/png", "PNG-BYTES".getBytes(StandardCharsets.UTF_8));

        when(accessGate.requireLearner()).thenReturn(userId);
        when(attemptGuard.requireOwnedAttempt(attemptId, userId)).thenReturn(attempt);
        when(attemptEventRepository.existsByAttemptIdAndType(attemptId, AttemptEventType.COMPENSATED))
                .thenReturn(false);
        when(attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId)).thenReturn(List.of(part));
        when(attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attemptId)).thenReturn(List.of(item));
        when(questionSourcePort.loadVersions(List.of(new VersionKey(questionId, 1))))
                .thenReturn(List.of(new Version(new VersionKey(questionId, 1), solutionSnap)));
        when(questionAssetContentPort.loadContent(assetId)).thenReturn(Optional.of(stubbedAsset));
        when(catalogLookup.listGroupItems("SUBJECT")).thenReturn(List.of());

        Locale prev = LocaleContextHolder.getLocale();
        LocaleContextHolder.setLocale(Locale.forLanguageTag("vi"));
        try {
            for (String grantView : List.of("SCORE", "UNKNOWN_TIER")) {
                when(entitlementPort.benefitsOf(attemptId))
                        .thenReturn(new EntitlementBenefits(
                                UUID.randomUUID(), attempt.getExamId(), Map.of(), grantView, List.of(), 1));

                AttemptResultResponse res = service.getResult(attemptId);
                assertThat(res.resultView()).isEqualTo(grantView);
                assertThat(res.reviewAvailable()).isFalse();

                assertThatThrownBy(() -> service.getReview(attemptId, null))
                        .isInstanceOf(ForbiddenException.class)
                        .satisfies(ex -> assertThat(((ForbiddenException) ex).getCode())
                                .isEqualTo("REVIEW_NOT_INCLUDED"));
            }
        } finally {
            LocaleContextHolder.setLocale(prev);
        }

        for (String grantView : List.of("SCORE", "SCORE_AND_ANSWERS")) {
            when(entitlementPort.benefitsOf(attemptId))
                    .thenReturn(new EntitlementBenefits(
                            UUID.randomUUID(), attempt.getExamId(), Map.of(), grantView, List.of(), 1));

            assertThatThrownBy(() -> solutionAssetService.loadSolutionAsset(attemptId, assetId))
                    .isInstanceOf(NotFoundException.class)
                    .satisfies(ex -> assertThat(((NotFoundException) ex).getCode()).isEqualTo("NOT_FOUND"));
        }
        verifyNoInteractions(questionAssetContentPort);

        when(entitlementPort.benefitsOf(attemptId))
                .thenReturn(new EntitlementBenefits(
                        UUID.randomUUID(), attempt.getExamId(), Map.of(), "FULL_SOLUTION", List.of(), 1));
        assertThat(solutionAssetService.loadSolutionAsset(attemptId, assetId)).isSameAs(stubbedAsset);
        verify(questionAssetContentPort).loadContent(assetId);
    }

    @Test
    void projectGradingDetailCoversAllNonEssayTypesOmitsExactFractionAndHidesEssayFeedback() throws Exception {
        // R2-6: verify ReviewGradingDetail projection across CLOZE, TRUE_FALSE, MATCHING, MULTIPLE_CHOICE,
        // SHORT_ANSWER, QUESTION_SET (+ SINGLE_CHOICE child), exactFraction omission, and essayFeedback == null
        UUID userId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID partId = UUID.randomUUID();

        Attempt attempt = scoredHsaAttempt(attemptId, userId, null);
        ReflectionTestUtils.setField(attempt, "resultView", "SCORE_AND_ANSWERS");
        AttemptPart part = AttemptPart.createPending(
                partId, attemptId, "P1", "Phần 1", 0, 60, 6, new BigDecimal("8.00"), 1L);
        part.open(NOW, NOW.plusSeconds(3600));
        part.close(PartCloseReason.ATTEMPT_SUBMITTED, NOW.plusSeconds(100));

        UUID clozeQ = UUID.randomUUID();
        UUID tfQ = UUID.randomUUID();
        UUID matchQ = UUID.randomUUID();
        UUID mcQ = UUID.randomUUID();
        UUID saQ = UUID.randomUUID();
        UUID setQ = UUID.randomUUID();
        UUID scQ = UUID.randomUUID();

        JsonNode clozeSnap = MAPPER.readTree("""
                {
                  "content": "<p>Điền</p>",
                  "body": {
                    "passage": "<p>Thủ đô [[b1]], cố đô [[b2]]</p>",
                    "blanks": [
                      {"id": "b1", "points": 1.0, "answers": ["Hà Nội"]},
                      {"id": "b2", "points": 1.0, "answers": ["Huế"]}
                    ]
                  }
                }
                """);
        JsonNode tfSnap = MAPPER.readTree("""
                {
                  "content": "<p>Đúng sai</p>",
                  "body": {
                    "scoringMethod": "COUNT_TABLE",
                    "scoreTable": [0.1, 0.25, 0.5, 1.0],
                    "statements": [
                      {"id": "s1", "content": "S1", "answer": true},
                      {"id": "s2", "content": "S2", "answer": false},
                      {"id": "s3", "content": "S3", "answer": true},
                      {"id": "s4", "content": "S4", "answer": false}
                    ]
                  }
                }
                """);
        JsonNode matchSnap = MAPPER.readTree("""
                {
                  "content": "<p>Ghép đôi</p>",
                  "body": {
                    "reuseRight": false,
                    "left": [{"id": "l1", "content": "L1"}, {"id": "l2", "content": "L2"}],
                    "right": [{"id": "r1", "content": "R1"}, {"id": "r2", "content": "R2"}, {"id": "r3", "content": "R3"}],
                    "pairs": [
                      {"leftId": "l1", "rightId": "r1", "points": 1.0},
                      {"leftId": "l2", "rightId": "r2", "points": 1.0}
                    ]
                  }
                }
                """);
        JsonNode mcSnap = MAPPER.readTree("""
                {
                  "content": "<p>Nhiều lựa chọn</p>",
                  "body": {
                    "scoringRule": "PARTIAL",
                    "options": [
                      {"id": "o1", "content": "O1", "correct": true},
                      {"id": "o2", "content": "O2", "correct": true},
                      {"id": "o3", "content": "O3", "correct": false}
                    ]
                  }
                }
                """);
        JsonNode saSnap = MAPPER.readTree("""
                {
                  "content": "<p>Trả lời ngắn</p>",
                  "body": {
                    "inputHint": "Nhập số",
                    "answers": ["42"]
                  }
                }
                """);
        JsonNode setSnap = MAPPER.readTree("""
                {
                  "content": "<p>Câu chùm</p>",
                  "body": {
                    "passage": "<p>Ngữ liệu</p>",
                    "blankNumberStart": 1
                  }
                }
                """);
        JsonNode scSnap = MAPPER.readTree("""
                {
                  "content": "<p>Câu con</p>",
                  "body": {
                    "options": [
                      {"id": "sc1", "content": "Đúng", "correct": true},
                      {"id": "sc2", "content": "Sai", "correct": false}
                    ]
                  }
                }
                """);

        AttemptItem clozeItem = AttemptItem.create(
                UUID.randomUUID(), attemptId, partId, null, "sec-1", null, "blk-1",
                null, 0, 1, 2, clozeQ, 1, QuestionType.CLOZE, new BigDecimal("2.00"), List.of());
        AnswerValue clozeAns = new AnswerValue.Cloze(Map.of("b1", "Hà Nội", "b2", "Đà Nẵng"));
        clozeItem.recordAnswer(clozeAns, 1L, NOW);
        ItemGradeResult clozeRes = ItemGrader.grade(
                QuestionType.CLOZE, clozeSnap, clozeAns, clozeItem.getPointsMax(), GradingPolicy.standard());
        clozeItem.recordGrading(clozeRes.earnedPoints(), clozeRes.outcome(), clozeRes.gradingDetail());

        AttemptItem tfItem = AttemptItem.create(
                UUID.randomUUID(), attemptId, partId, null, "sec-1", null, "blk-1",
                null, 1, 2, 1, tfQ, 1, QuestionType.TRUE_FALSE, new BigDecimal("1.00"), List.of());
        AnswerValue tfAns = new AnswerValue.TrueFalse(Map.of("s1", true, "s2", false, "s3", false));
        tfItem.recordAnswer(tfAns, 1L, NOW);
        ItemGradeResult tfRes = ItemGrader.grade(
                QuestionType.TRUE_FALSE, tfSnap, tfAns, tfItem.getPointsMax(), GradingPolicy.standard());
        tfItem.recordGrading(tfRes.earnedPoints(), tfRes.outcome(), tfRes.gradingDetail());

        AttemptItem matchItem = AttemptItem.create(
                UUID.randomUUID(), attemptId, partId, null, "sec-1", null, "blk-1",
                null, 2, 3, 1, matchQ, 1, QuestionType.MATCHING, new BigDecimal("2.00"), List.of("r1", "r2", "r3"));
        AnswerValue matchAns = new AnswerValue.Matching(Map.of("l1", "r1", "l2", "r3"));
        matchItem.recordAnswer(matchAns, 1L, NOW);
        ItemGradeResult matchRes = ItemGrader.grade(
                QuestionType.MATCHING, matchSnap, matchAns, matchItem.getPointsMax(), GradingPolicy.standard());
        matchItem.recordGrading(matchRes.earnedPoints(), matchRes.outcome(), matchRes.gradingDetail());

        AttemptItem mcItem = AttemptItem.create(
                UUID.randomUUID(), attemptId, partId, null, "sec-1", null, "blk-1",
                null, 3, 4, 1, mcQ, 1, QuestionType.MULTIPLE_CHOICE, new BigDecimal("1.00"), List.of("o1", "o2", "o3"));
        AnswerValue mcAns = new AnswerValue.MultipleChoice(List.of("o1"));
        mcItem.recordAnswer(mcAns, 1L, NOW);
        ItemGradeResult mcRes = ItemGrader.grade(
                QuestionType.MULTIPLE_CHOICE, mcSnap, mcAns, mcItem.getPointsMax(), GradingPolicy.standard());
        mcItem.recordGrading(mcRes.earnedPoints(), mcRes.outcome(), mcRes.gradingDetail());

        AttemptItem saItem = AttemptItem.create(
                UUID.randomUUID(), attemptId, partId, null, "sec-1", null, "blk-1",
                null, 4, 5, 1, saQ, 1, QuestionType.SHORT_ANSWER, new BigDecimal("1.00"), List.of());
        AnswerValue saAns = new AnswerValue.ShortAnswer(" 42 ");
        saItem.recordAnswer(saAns, 1L, NOW);
        ItemGradeResult saRes = ItemGrader.grade(
                QuestionType.SHORT_ANSWER, saSnap, saAns, saItem.getPointsMax(), GradingPolicy.standard());
        saItem.recordGrading(saRes.earnedPoints(), saRes.outcome(), saRes.gradingDetail());

        AttemptItem setItem = AttemptItem.create(
                UUID.randomUUID(), attemptId, partId, null, "sec-1", null, "blk-1",
                null, 5, null, 0, setQ, 1, QuestionType.QUESTION_SET, new BigDecimal("1.00"), List.of());
        AttemptItem scChild = AttemptItem.create(
                UUID.randomUUID(), attemptId, partId, null, "sec-1", null, "blk-1",
                setItem.getId(), 6, 6, 1, scQ, 1, QuestionType.SINGLE_CHOICE, new BigDecimal("1.00"), List.of("sc1", "sc2"));
        AnswerValue scAns = new AnswerValue.SingleChoice("sc1");
        scChild.recordAnswer(scAns, 1L, NOW);
        ItemGradeResult scRes = ItemGrader.grade(
                QuestionType.SINGLE_CHOICE, scSnap, scAns, scChild.getPointsMax(), GradingPolicy.standard());
        scChild.recordGrading(scRes.earnedPoints(), scRes.outcome(), scRes.gradingDetail());
        ItemGradeResult setRes = ItemGrader.rollUpQuestionSet(List.of(scRes), setItem.getPointsMax());
        setItem.recordGrading(setRes.earnedPoints(), setRes.outcome(), setRes.gradingDetail());

        when(accessGate.requireLearner()).thenReturn(userId);
        when(attemptGuard.requireOwnedAttempt(attemptId, userId)).thenReturn(attempt);
        when(entitlementPort.benefitsOf(attemptId))
                .thenReturn(new EntitlementBenefits(
                        UUID.randomUUID(), attempt.getExamId(), Map.of(), "SCORE_AND_ANSWERS", List.of(), 1));
        when(attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId)).thenReturn(List.of(part));
        when(attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attemptId))
                .thenReturn(List.of(clozeItem, tfItem, matchItem, mcItem, saItem, setItem, scChild));
        when(questionSourcePort.loadVersions(anyList())).thenReturn(List.of(
                new Version(new VersionKey(clozeQ, 1), clozeSnap),
                new Version(new VersionKey(tfQ, 1), tfSnap),
                new Version(new VersionKey(matchQ, 1), matchSnap),
                new Version(new VersionKey(mcQ, 1), mcSnap),
                new Version(new VersionKey(saQ, 1), saSnap),
                new Version(new VersionKey(setQ, 1), setSnap),
                new Version(new VersionKey(scQ, 1), scSnap)));

        AttemptReviewResponse review = service.getReview(attemptId, null);
        assertThat(review.items()).hasSize(7);
        assertThat(review.items()).allSatisfy(item -> assertThat(item.essayFeedback()).isNull());

        String serialized = MAPPER.writeValueAsString(review);
        assertThat(serialized).doesNotContain("exactFraction");

        ReviewGradingDetail clozeDetail = itemViewById(review, clozeItem.getId()).gradingDetail();
        assertThat(clozeDetail.correctUnitCount()).isEqualTo(1);
        assertThat(clozeDetail.wrongUnitCount()).isEqualTo(1);
        assertThat(clozeDetail.blankUnitCount()).isZero();
        assertThat(clozeDetail.blankResults()).containsExactly(
                new ReviewClozeBlankResult("b1", ItemOutcome.CORRECT, new BigDecimal("1.0000"), new BigDecimal("1.0000")),
                new ReviewClozeBlankResult("b2", ItemOutcome.WRONG, new BigDecimal("0.0000"), new BigDecimal("1.0000")));

        ReviewGradingDetail tfDetail = itemViewById(review, tfItem.getId()).gradingDetail();
        assertThat(tfDetail.scoringMethod()).isEqualTo("COUNT_TABLE");
        assertThat(tfDetail.matchedCount()).isEqualTo(2);
        assertThat(tfDetail.statementCount()).isEqualTo(4);
        assertThat(tfDetail.statementResults()).containsExactly(
                new ReviewStatementResult("s1", true, true),
                new ReviewStatementResult("s2", true, true),
                new ReviewStatementResult("s3", true, false),
                new ReviewStatementResult("s4", false, false));

        ReviewGradingDetail matchDetail = itemViewById(review, matchItem.getId()).gradingDetail();
        assertThat(matchDetail.reuseRight()).isFalse();
        assertThat(matchDetail.matchedCount()).isEqualTo(1);
        assertThat(matchDetail.pairCount()).isEqualTo(2);
        assertThat(matchDetail.pairResults()).containsExactly(
                new ReviewMatchingPairResult("l1", true, new BigDecimal("1.0000")),
                new ReviewMatchingPairResult("l2", false, new BigDecimal("0.0000")));

        ReviewGradingDetail mcDetail = itemViewById(review, mcItem.getId()).gradingDetail();
        assertThat(mcDetail.scoringRule()).isEqualTo("PARTIAL");
        assertThat(mcDetail.selectedCount()).isEqualTo(1);
        assertThat(mcDetail.correctSelectedCount()).isEqualTo(1);
        assertThat(mcDetail.wrongSelectedCount()).isZero();
        assertThat(mcDetail.totalExpectedCount()).isEqualTo(2);

        ReviewGradingDetail saDetail = itemViewById(review, saItem.getId()).gradingDetail();
        assertThat(saDetail.normalizedMatch()).isTrue();

        ReviewGradingDetail setDetail = itemViewById(review, setItem.getId()).gradingDetail();
        assertThat(setDetail.itemType()).isEqualTo("QUESTION_SET");
        assertThat(setDetail.childCount()).isEqualTo(1);

        ReviewGradingDetail scDetail = itemViewById(review, scChild.getId()).gradingDetail();
        assertThat(scDetail.selectedOptionId()).isEqualTo("sc1");
    }

    @Test
    void resultIncludesSegmentsFromPublishedSnapshotAndOmitsWhenAbsent() {
        UUID userId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID partId = UUID.randomUUID();

        Attempt attempt = scoredHsaAttempt(attemptId, userId, null);
        AttemptPart part = AttemptPart.createPending(
                partId, attemptId, "p1", "Phần 1", 0, 60, 2, new BigDecimal("12.00"), 1L);
        part.open(NOW, NOW.plusSeconds(3600));
        part.close(PartCloseReason.ATTEMPT_SUBMITTED, NOW.plusSeconds(100));

        AttemptItem i1 = AttemptItem.create(
                UUID.randomUUID(), attemptId, partId, null, "sec-1", null, "blk-1",
                null, 1, 1, 1, UUID.randomUUID(), 1, QuestionType.SINGLE_CHOICE, new BigDecimal("6.00"), List.of());
        i1.recordGrading(new BigDecimal("6.0000"), ItemOutcome.CORRECT, Map.of("exactFraction", "6"));
        AttemptItem i2 = AttemptItem.create(
                UUID.randomUUID(), attemptId, partId, null, "sec-1", null, "blk-1",
                null, 2, 2, 1, UUID.randomUUID(), 1, QuestionType.SINGLE_CHOICE, new BigDecimal("6.00"), List.of());
        i2.recordGrading(BigDecimal.ZERO, ItemOutcome.BLANK, Map.of("exactFraction", "0"));

        PublishedVersion versionWithSegments = new PublishedVersion(
                attempt.getExamId(), attempt.getExamVersionNo(), "SERVING", "PUBLISHED", NOW,
                "VSAT", "V-SAT", UUID.randomUUID(), "VSAT", "SUBJECT", null, null,
                "STANDARD", "SHARED", 60, new BigDecimal("12.00"), null, "RAW", null,
                List.of(new Part("p1", 1, "Part 1", 60, 2, 2, 2, "ALL", null, new BigDecimal("12.00"),
                        List.of(
                                new Segment("P1", "Phần I", 1, 1, 1, new BigDecimal("6.00")),
                                new Segment("P2", "Phần II", 2, 2, 1, new BigDecimal("6.00"))),
                        List.of(), List.of(), List.of(), List.of(), List.of())),
                List.of(), List.of(), List.of(), List.of(), null);

        when(accessGate.requireLearner()).thenReturn(userId);
        when(attemptGuard.requireOwnedAttempt(attemptId, userId)).thenReturn(attempt);
        when(entitlementPort.benefitsOf(attemptId))
                .thenReturn(new EntitlementBenefits(UUID.randomUUID(), attempt.getExamId(), Map.of(), "SCORE", List.of(), 1));
        when(attemptPartRepository.findByAttemptIdOrderByOrdAsc(attemptId)).thenReturn(List.of(part));
        when(attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attemptId)).thenReturn(List.of(i1, i2));
        when(publishedExamPort.loadVersion(attempt.getExamId(), attempt.getExamVersionNo()))
                .thenReturn(Optional.of(versionWithSegments));
        when(catalogLookup.listGroupItems("SUBJECT")).thenReturn(List.of());

        Locale prev = LocaleContextHolder.getLocale();
        LocaleContextHolder.setLocale(Locale.forLanguageTag("vi"));
        try {
            AttemptResultResponse withSegs = service.getResult(attemptId);
            assertThat(withSegs.segments()).hasSize(2);
            assertThat(withSegs.segments().get(0).code()).isEqualTo("P1");
            assertThat(withSegs.segments().get(0).score()).isEqualByComparingTo("6.00");
            assertThat(withSegs.segments().get(1).code()).isEqualTo("P2");
            assertThat(withSegs.segments().get(1).score()).isEqualByComparingTo("0.00");

            when(publishedExamPort.loadVersion(attempt.getExamId(), attempt.getExamVersionNo()))
                    .thenReturn(Optional.empty());
            AttemptResultResponse withoutSegs = service.getResult(attemptId);
            assertThat(withoutSegs.segments()).isNull();
        } finally {
            LocaleContextHolder.setLocale(prev);
        }
    }

    private static ReviewItemView itemViewById(AttemptReviewResponse review, UUID itemId) {
        return review.items().stream()
                .filter(i -> i.itemId().equals(itemId))
                .findFirst()
                .orElseThrow();
    }

    private static Attempt scoredHsaAttempt(UUID attemptId, UUID userId, UUID subjectId) {
        Attempt attempt = Attempt.start(
                attemptId,
                userId,
                UUID.randomUUID(),
                1,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "HSA Mini",
                UUID.randomUUID(),
                "HSA",
                "SUBJECT",
                null,
                subjectId,
                AttemptTiming.SHARED,
                NavigationMode.SEQUENTIAL,
                Map.of(),
                1L,
                UUID.randomUUID(),
                "SCORE",
                "RAW",
                null,
                new BigDecimal("10.00"),
                null,
                NOW);
        attempt.open(1, NOW, NOW.plusSeconds(1800));
        attempt.close(EndReason.SUBMITTED, NOW.plusSeconds(100), NOW.plusSeconds(100));
        attempt.applyGradingResult(false, new BigDecimal("10.00"), BigDecimal.ONE, BigDecimal.ONE, Map.of(), NOW.plusSeconds(105), NOW.plusSeconds(105));
        return attempt;
    }
}
