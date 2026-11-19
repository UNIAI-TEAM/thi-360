package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import vn.unicom.thi360.exam.api.ExamListingCheckResult;
import vn.unicom.thi360.exam.application.ExamListingCheck;
import vn.unicom.thi360.exam.infrastructure.ExamListingProperties;

/** The rules of the listing check (TM 6.2, E7, E26) without a database. */
class ExamListingCheckTest {
    private static final UUID COVER = UUID.randomUUID();

    private static List<String> codes(long minimum, Long price) {
        return new ExamListingCheck(minimum)
                .evaluate(new ExamListingCheck.Input(price, 3, "Mo ta", COVER)).stream()
                .map(ExamListingCheckResult.Issue::code).toList();
    }

    @Test
    void minimumPriceBoundary() {
        assertThat(codes(5000, 0L)).containsExactly("LISTING_PRICE_ZERO");
        assertThat(codes(5000, 4999L)).containsExactly("LISTING_PRICE_BELOW_MIN");
        assertThat(codes(5000, 5000L)).isEmpty();
        assertThat(codes(5000, null)).containsExactly("LISTING_PRICE_MISSING");
        // The threshold follows the configured minimum (THI360_VNPAY_MIN_AMOUNT = 10000 in sandbox environments).
        assertThat(codes(10_000, 9999L)).containsExactly("LISTING_PRICE_BELOW_MIN");
        assertThat(codes(10_000, 10_000L)).isEmpty();
        assertThat(codes(10_000, 5000L)).containsExactly("LISTING_PRICE_BELOW_MIN");
        // The issue says which minimum it enforced.
        var issue = new ExamListingCheck(10_000).evaluate(new ExamListingCheck.Input(5000L, 3, "d", COVER)).getFirst();
        assertThat(issue.minPriceVnd()).isEqualTo(10_000L);
        assertThat(ExamListingCheck.blocks(issue)).isTrue();
    }

    @Test
    void minimumPriceComesFromTheConfiguredVariable() {
        var unset = new Binder(new MapConfigurationPropertySource(Map.of()))
                .bindOrCreate("thi360.exam.listing", ExamListingProperties.class);
        assertThat(unset.minPriceVnd()).as("code default (production)").isEqualTo(5000);
        var sandbox = new Binder(new MapConfigurationPropertySource(Map.of("thi360.exam.listing.min-price-vnd", "10000")))
                .bindOrCreate("thi360.exam.listing", ExamListingProperties.class);
        assertThat(sandbox.minPriceVnd()).isEqualTo(10_000);
        assertThat(codes(sandbox.minPriceVnd(), 9999L)).containsExactly("LISTING_PRICE_BELOW_MIN");
    }

    @Test
    void otherRulesAndTheCoverWarning() {
        var check = new ExamListingCheck(5000);
        var issues = check.evaluate(new ExamListingCheck.Input(50_000L, null, "  ", null));
        assertThat(issues).extracting(ExamListingCheckResult.Issue::code)
                .containsExactly("LISTING_ATTEMPTS_MISSING", "LISTING_DESCRIPTION_MISSING", "LISTING_COVER_MISSING");
        assertThat(issues).extracting(ExamListingCheckResult.Issue::severity).containsExactly("ERROR", "ERROR", "WARNING");
        assertThat(ExamListingCheck.group(issues).status()).isEqualTo("FAIL");
        var coverOnly = check.evaluate(new ExamListingCheck.Input(50_000L, 1, "Mo ta", null));
        assertThat(ExamListingCheck.group(coverOnly).status()).as("a missing cover only warns").isEqualTo("WARN");
        assertThat(ExamListingCheck.group(check.evaluate(new ExamListingCheck.Input(50_000L, 1, "Mo ta", COVER))).status())
                .isEqualTo("PASS");
    }
}
