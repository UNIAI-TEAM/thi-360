package vn.unicom.thi360.attempt.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EssayGradeTest {

    @Test
    void touchSaveDraftAndFinalizeClampBackwardSkewToClaimedAt() {
        Instant claimedAt = Instant.parse("2026-11-21T10:00:00.000Z");
        Instant skewedNow = claimedAt.minusMillis(5);

        EssayGrade touchGrade = EssayGrade.claim(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, claimedAt);
        touchGrade.touch(skewedNow);
        assertThat(touchGrade.getLastActivityAt()).isEqualTo(claimedAt);
        assertThat(touchGrade.getUpdatedAt()).isEqualTo(claimedAt);

        EssayGrade draftGrade = EssayGrade.claim(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, claimedAt);
        draftGrade.saveDraft(
                List.of(new EssayCriterionScore("c1", new BigDecimal("5.00"), "Nháp")),
                new BigDecimal("5.00"),
                "Nháp",
                skewedNow);
        assertThat(draftGrade.getLastActivityAt()).isEqualTo(claimedAt);
        assertThat(draftGrade.getUpdatedAt()).isEqualTo(claimedAt);

        EssayGrade finalizedGrade = EssayGrade.claim(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, claimedAt);
        finalizedGrade.finalizeGrade(
                List.of(new EssayCriterionScore("c1", new BigDecimal("10.00"), "Chốt")),
                new BigDecimal("10.00"),
                "Chốt",
                skewedNow);
        assertThat(finalizedGrade.getState()).isEqualTo(EssayGradeState.FINAL);
        assertThat(finalizedGrade.getLastActivityAt()).isEqualTo(claimedAt);
        assertThat(finalizedGrade.getFinalizedAt()).isEqualTo(claimedAt);
        assertThat(finalizedGrade.getUpdatedAt()).isEqualTo(claimedAt);

        Instant later = claimedAt.plusSeconds(10);
        draftGrade.touch(later);
        assertThat(draftGrade.getLastActivityAt()).isEqualTo(later);
    }
}
