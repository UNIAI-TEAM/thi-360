package vn.unicom.thi360.attempt.domain.grading;

import java.util.Locale;

/**
 * Resolves the {@link GradingPolicy} for an exam kind code (BS §17.5).
 *
 * <p>{@code HSCA} uses {@link GradingPolicy#halfCredit()}; all other exam kinds retain the
 * standard BE §8 proportional partial rule.
 */
public final class GradingPolicies {

    private static final String HSCA_KIND = "HSCA";

    private GradingPolicies() {}

    public static GradingPolicy forKind(String examKindCode) {
        if (examKindCode != null
                && HSCA_KIND.equals(examKindCode.trim().toUpperCase(Locale.ROOT))) {
            return GradingPolicy.halfCredit();
        }
        return GradingPolicy.standard();
    }
}
