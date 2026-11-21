package vn.unicom.thi360.attempt.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import vn.unicom.thi360.attempt.api.AttemptResultResponse;
import vn.unicom.thi360.attempt.api.AttemptResultResponse.VactComponentView;
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
import vn.unicom.thi360.questionbank.spi.QuestionAttachmentPort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.api.ForbiddenException;

/**
 * Unit tests for {@link AttemptResultService} and {@link AttemptSolutionAssetService} (R1-7, R1-8,
 * R1-18, R1-20, R1-40, R1-41, R1-48, R1-51).
 */
class AttemptResultServiceUnitTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    private AttemptAccessGate accessGate;
    private AttemptGuard attemptGuard;
    private AttemptPartRepository attemptPartRepository;
    private AttemptItemRepository attemptItemRepository;
    private AttemptEventRepository attemptEventRepository;
    private EntitlementPort entitlementPort;
    private CatalogLookup catalogLookup;
    private AttemptResultService service;

    @BeforeEach
    void setUp() {
        accessGate = mock(AttemptAccessGate.class);
        attemptGuard = mock(AttemptGuard.class);
        attemptPartRepository = mock(AttemptPartRepository.class);
        attemptItemRepository = mock(AttemptItemRepository.class);
        attemptEventRepository = mock(AttemptEventRepository.class);
        entitlementPort = mock(EntitlementPort.class);
        QuestionSourcePort questionSourcePort = mock(QuestionSourcePort.class);
        QuestionAttachmentPort questionAttachmentPort = mock(QuestionAttachmentPort.class);
        PublishedExamPort publishedExamPort = mock(PublishedExamPort.class);
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
