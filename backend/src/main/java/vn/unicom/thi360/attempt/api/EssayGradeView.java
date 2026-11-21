package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.attempt.domain.EssayCriterionScore;
import vn.unicom.thi360.attempt.domain.EssayGradeState;

@Schema(name = "EssayGradeView", description = "Bản chấm tự luận (nháp hoặc chính thức) kèm phiên bản khóa lạc quan")
public record EssayGradeView(
        UUID id,
        UUID attemptItemId,
        UUID graderId,
        String graderName,
        List<EssayCriterionScore> criteria,
        BigDecimal points,
        String comment,
        Integer gradingGuideVersion,
        EssayGradeState state,
        Instant claimedAt,
        Instant lastActivityAt,
        Instant finalizedAt,
        String reopenedReason,
        UUID supersededBy,
        Long version,
        boolean claimExpired) {}
