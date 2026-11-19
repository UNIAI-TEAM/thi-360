package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The sale listing of an exam: the working copy, the published copy and its check (TM 6.3). */
@Schema(name = "ExamListing")
public record ExamListingResponse(
        UUID examId,
        @Schema(description = "Exam revision; send it back as expectedRevision") long revision,
        boolean hasUnpublishedChanges,
        String summary,
        String objectives,
        String audience,
        @Schema(description = "List price in VND, tax and fees included", nullable = true) Long listPriceVnd,
        @Schema(description = "Attempts granted by one purchase, 1 to 100", nullable = true) Integer attemptsGranted,
        boolean benefitAiExplanation,
        boolean benefitStrengthWeakness,
        boolean benefitPractice,
        String benefitNote,
        @Schema(allowableValues = {"SCORE", "SCORE_AND_ANSWERS", "FULL_SOLUTION"}) String resultView,
        @Schema(description = "exam.description; lives on the exam row") String description,
        UUID coverAssetId,
        @Schema(description = "Where the admin web reads the draft cover") String coverUrl,
        Instant updatedAt,
        @Schema(description = "Listing of the latest version that still serves a path (E24); null when there is none "
                + "or that version predates the listing", nullable = true) Published published,
        @Schema(description = "Listing issues of the working copy, computed now") List<ExamListingCheckResult.Issue> checkIssues,
        @Schema(description = "Smallest price accepted at publish (THI360_VNPAY_MIN_AMOUNT)") long minPriceVnd) {

    @Schema(name = "ExamListingPublished")
    public record Published(int versionNo, Instant publishedAt, Sale listing) { }

    /** The listing as frozen in {@code exam_version.config.listing}. */
    @Schema(name = "ExamListingSale")
    public record Sale(
            String name,
            String description,
            UUID coverAssetId,
            String coverUrl,
            String summary,
            String objectives,
            String audience,
            Long listPriceVnd,
            Integer attemptsGranted,
            boolean benefitAiExplanation,
            boolean benefitStrengthWeakness,
            boolean benefitPractice,
            String benefitNote,
            @Schema(allowableValues = {"SCORE", "SCORE_AND_ANSWERS", "FULL_SOLUTION"}) String resultView) { }
}
