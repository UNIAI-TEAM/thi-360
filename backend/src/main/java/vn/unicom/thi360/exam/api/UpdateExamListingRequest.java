package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Replaces the working listing (TM 6.3). Saving a draft checks types and limits only; an incomplete listing is
 * accepted and blocked later, at publish.
 */
@Schema(name = "UpdateExamListingRequest")
public record UpdateExamListingRequest(
        @Schema(description = "At most 300 characters; blank clears") String summary,
        @Schema(description = "At most 2.000 characters; blank clears") String objectives,
        @Schema(description = "At most 2.000 characters; blank clears") String audience,
        @Schema(description = "VND, 0 or more; null clears") Long listPriceVnd,
        @Schema(description = "1 to 100; null clears") Integer attemptsGranted,
        @Schema(description = "null counts as false") Boolean benefitAiExplanation,
        @Schema(description = "null counts as false") Boolean benefitStrengthWeakness,
        @Schema(description = "null counts as false") Boolean benefitPractice,
        @Schema(description = "At most 1.000 characters; blank clears") String benefitNote,
        @Schema(description = "SCORE, SCORE_AND_ANSWERS or FULL_SOLUTION; null keeps SCORE_AND_ANSWERS",
                allowableValues = {"SCORE", "SCORE_AND_ANSWERS", "FULL_SOLUTION"}) String resultView,
        @Schema(description = "exam.description, at most 4.000 characters; blank clears") String description,
        @Schema(description = "A cover uploaded for this exam; null clears") UUID coverAssetId,
        @Schema(description = "Revision being edited; a different one gives 412 EXAM_REVISION_CONFLICT",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long expectedRevision) { }
