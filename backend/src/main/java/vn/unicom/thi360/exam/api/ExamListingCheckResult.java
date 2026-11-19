package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * The "Thông tin bán" group of the exam check (TM 6.2). It is computed when read from the working listing, never
 * stored in {@code exam_check_run.result}, and is not one of the eight configuration groups.
 */
@Schema(name = "ExamListingCheckGroup")
public record ExamListingCheckResult(
        @Schema(allowableValues = {"LISTING"}) String group,
        @Schema(allowableValues = {"PASS", "WARN", "FAIL"}) String status,
        List<Issue> issues) {

    public static final String GROUP = "LISTING";

    @Schema(name = "ExamListingCheckIssue")
    public record Issue(
            String key,
            @Schema(description = "LISTING_PRICE_MISSING, LISTING_PRICE_ZERO, LISTING_PRICE_BELOW_MIN, "
                    + "LISTING_ATTEMPTS_MISSING, LISTING_DESCRIPTION_MISSING or LISTING_COVER_MISSING")
            String code,
            @Schema(allowableValues = {"ERROR", "WARNING"}) String severity,
            String group,
            @Schema(description = "Listing field the issue is about") String field,
            @Schema(description = "Minimum price in VND, only on LISTING_PRICE_BELOW_MIN", nullable = true)
            Long minPriceVnd) { }
}
