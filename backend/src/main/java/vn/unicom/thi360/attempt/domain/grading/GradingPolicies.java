package vn.unicom.thi360.attempt.domain.grading;

/**
 * Resolves the {@link GradingPolicy} for an exam kind code.
 *
 * <p>In AT-4, all exam kinds use the standard BE §8 proportional partial rule (AC-40).
 */
public final class GradingPolicies {

    private GradingPolicies() {}

    public static GradingPolicy forKind(String examKindCode) {
        return GradingPolicy.standard();
    }
}
