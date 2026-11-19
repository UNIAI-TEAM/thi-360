package vn.unicom.thi360.exam.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.exam.api.ExamErrorCodes;
import vn.unicom.thi360.exam.api.ExamListingCheckResult;

/**
 * The rules of the "Thông tin bán" check (TM 6.2). Pure: it sees the values, not the database. A missing cover is a
 * warning; every other issue blocks publishing. The order of the blocking codes is the order publish reports them.
 */
public final class ExamListingCheck {
    private final long minPriceVnd;

    public ExamListingCheck(long minPriceVnd) {
        this.minPriceVnd = minPriceVnd;
    }

    /** What the rules read: the listing columns, and the exam's own description and cover. */
    public record Input(Long listPriceVnd, Integer attemptsGranted, String description, UUID coverAssetId) { }

    public List<ExamListingCheckResult.Issue> evaluate(Input input) {
        List<ExamListingCheckResult.Issue> issues = new ArrayList<>();
        Long price = input.listPriceVnd();
        if (price == null) {
            issues.add(error(ExamErrorCodes.LISTING_PRICE_MISSING, "listPriceVnd", null));
        } else if (price == 0) {
            issues.add(error(ExamErrorCodes.LISTING_PRICE_ZERO, "listPriceVnd", null));
        } else if (price < minPriceVnd) {
            issues.add(error(ExamErrorCodes.LISTING_PRICE_BELOW_MIN, "listPriceVnd", minPriceVnd));
        }
        if (input.attemptsGranted() == null) {
            issues.add(error(ExamErrorCodes.LISTING_ATTEMPTS_MISSING, "attemptsGranted", null));
        }
        if (input.description() == null || input.description().isBlank()) {
            issues.add(error(ExamErrorCodes.LISTING_DESCRIPTION_MISSING, "description", null));
        }
        if (input.coverAssetId() == null) {
            issues.add(new ExamListingCheckResult.Issue(key(ExamErrorCodes.LISTING_COVER_MISSING),
                    ExamErrorCodes.LISTING_COVER_MISSING, "WARNING", ExamListingCheckResult.GROUP, "coverAssetId", null));
        }
        return List.copyOf(issues);
    }

    public static boolean blocks(ExamListingCheckResult.Issue issue) {
        return "ERROR".equals(issue.severity());
    }

    public static ExamListingCheckResult group(List<ExamListingCheckResult.Issue> issues) {
        boolean error = issues.stream().anyMatch(ExamListingCheck::blocks);
        return new ExamListingCheckResult(ExamListingCheckResult.GROUP,
                error ? "FAIL" : issues.isEmpty() ? "PASS" : "WARN", issues);
    }

    private static ExamListingCheckResult.Issue error(String code, String field, Long minPriceVnd) {
        return new ExamListingCheckResult.Issue(key(code), code, "ERROR", ExamListingCheckResult.GROUP, field, minPriceVnd);
    }

    private static String key(String code) {
        return ExamListingCheckResult.GROUP + ":" + code;
    }
}
