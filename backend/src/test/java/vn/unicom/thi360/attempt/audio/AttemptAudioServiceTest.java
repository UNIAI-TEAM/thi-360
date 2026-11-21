package vn.unicom.thi360.attempt.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import vn.unicom.thi360.attempt.application.AttemptAccessGate;
import vn.unicom.thi360.attempt.application.AttemptAssetService;
import vn.unicom.thi360.attempt.application.AttemptAudioFileCheck;
import vn.unicom.thi360.attempt.application.AttemptAudioProperties;
import vn.unicom.thi360.attempt.application.AttemptGuard;
import vn.unicom.thi360.attempt.application.AttemptLifecycleService;
import vn.unicom.thi360.attempt.application.AttemptPaperAssembler;
import vn.unicom.thi360.attempt.application.AttemptPathResolver;
import vn.unicom.thi360.attempt.application.AttemptQuestionSanitizer;
import vn.unicom.thi360.attempt.application.AttemptSessionChecker;
import vn.unicom.thi360.attempt.application.AttemptStartService;
import vn.unicom.thi360.attempt.application.AttemptStateService;
import vn.unicom.thi360.attempt.application.FinalAnswerWriter;
import vn.unicom.thi360.attempt.application.GenerationExecutor;
import vn.unicom.thi360.attempt.application.OverdueAttemptCloser;
import vn.unicom.thi360.attempt.application.PartTransitionHook;
import vn.unicom.thi360.attempt.infrastructure.AttemptAudioPlayRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptEventRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptPartRepository;
import vn.unicom.thi360.attempt.infrastructure.AttemptRepository;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.exam.spi.PaperSolverPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.questionbank.spi.QuestionAssetContentPort;
import vn.unicom.thi360.questionbank.spi.QuestionAttachmentPort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.shared.idempotency.IdempotentCommand;
import vn.unicom.thi360.shared.outbox.OutboxPublisher;

class AttemptAudioServiceTest {

    @Test
    void audioPropertiesApplySensibleDefaultsAndKeepExplicitValues() {
        AttemptAudioProperties defaults = new AttemptAudioProperties(null, null);
        assertThat(defaults.leaseDuration()).isEqualTo(Duration.ofSeconds(45));
        assertThat(defaults.seekTolerance()).isEqualTo(Duration.ofSeconds(3));

        AttemptAudioProperties fallbackInvalid =
                new AttemptAudioProperties(Duration.ZERO, Duration.ofMillis(-1));
        assertThat(fallbackInvalid.leaseDuration()).isEqualTo(Duration.ofSeconds(45));
        assertThat(fallbackInvalid.seekTolerance()).isEqualTo(Duration.ofSeconds(3));

        AttemptAudioProperties custom =
                new AttemptAudioProperties(Duration.ofSeconds(30), Duration.ZERO);
        assertThat(custom.leaseDuration()).isEqualTo(Duration.ofSeconds(30));
        assertThat(custom.seekTolerance()).isEqualTo(Duration.ZERO);
    }

    @Test
    void audioDomainRejectsInvalidTransitionsCleanly() {
        var now = java.time.Instant.parse("2026-11-21T00:00:00Z");
        var play =
                vn.unicom.thi360.attempt.domain.AttemptAudioPlay.createIdle(
                        java.util.UUID.randomUUID(),
                        java.util.UUID.randomUUID(),
                        java.util.UUID.randomUUID(),
                        java.util.UUID.randomUUID(),
                        30_000L,
                        now);
        assertThat(play.playsLeft()).isEqualTo(2);
        assertThatThrownBy(() -> play.resume(now, now.plusSeconds(45)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> play.recordProgress(100, 0, now, now.plusSeconds(45)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void productionConstructorsRejectNullAudioCollaborators() {
        Clock clock = Clock.systemUTC();
        assertThatThrownBy(
                        () ->
                                new AttemptAssetService(
                                        mock(AttemptAccessGate.class),
                                        mock(AttemptGuard.class),
                                        mock(AttemptPartRepository.class),
                                        mock(AttemptItemRepository.class),
                                        null,
                                        mock(QuestionSourcePort.class),
                                        mock(QuestionAssetContentPort.class),
                                        mock(PlatformTransactionManager.class),
                                        clock))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(
                        () ->
                                new AttemptStateService(
                                        mock(AttemptAccessGate.class),
                                        mock(AttemptGuard.class),
                                        mock(AttemptPartRepository.class),
                                        mock(AttemptItemRepository.class),
                                        null,
                                        mock(QuestionSourcePort.class),
                                        mock(QuestionAttachmentPort.class),
                                        mock(AttemptQuestionSanitizer.class),
                                        mock(EntityManager.class),
                                        clock))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(
                        () ->
                                new AttemptStartService(
                                        mock(AttemptAccessGate.class),
                                        mock(EntitlementPort.class),
                                        mock(PublishedExamPort.class),
                                        mock(PaperSolverPort.class),
                                        mock(QuestionSourcePort.class),
                                        mock(AttemptPathResolver.class),
                                        mock(AttemptPaperAssembler.class),
                                        mock(AttemptRepository.class),
                                        mock(AttemptPartRepository.class),
                                        mock(AttemptItemRepository.class),
                                        null,
                                        mock(AttemptEventRepository.class),
                                        mock(AttemptAudioFileCheck.class),
                                        mock(GenerationExecutor.class),
                                        mock(IdempotentCommand.class),
                                        mock(OverdueAttemptCloser.class),
                                        mock(EntityManager.class),
                                        mock(JdbcTemplate.class),
                                        mock(PlatformTransactionManager.class),
                                        clock))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(
                        () ->
                                new AttemptLifecycleService(
                                        mock(AttemptAccessGate.class),
                                        mock(AttemptRepository.class),
                                        mock(AttemptPartRepository.class),
                                        mock(AttemptItemRepository.class),
                                        mock(AttemptEventRepository.class),
                                        mock(PublishedExamPort.class),
                                        mock(EntitlementPort.class),
                                        mock(OutboxPublisher.class),
                                        mock(IdempotentCommand.class),
                                        mock(OverdueAttemptCloser.class),
                                        mock(AttemptSessionChecker.class),
                                        mock(FinalAnswerWriter.class),
                                        (ObjectProvider<PartTransitionHook>)
                                                mock(ObjectProvider.class),
                                        (AttemptAudioPlayRepository) null,
                                        mock(PlatformTransactionManager.class),
                                        clock))
                .isInstanceOf(NullPointerException.class);
    }
}
