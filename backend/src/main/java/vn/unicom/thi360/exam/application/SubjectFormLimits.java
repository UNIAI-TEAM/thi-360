package vn.unicom.thi360.exam.application;

import java.util.Optional;
import java.util.Set;

/**
 * Per exam kind and subject limits of the science subjects of HSA and QDA (FTM R-12). Codes are canonical catalog
 * codes; no alias is resolved. Every other kind and subject pair has no limit.
 */
public final class SubjectFormLimits {
    /** Set count in {@code [setCountMin, setCountMax]}, every set has {@code setSize} children, short answers at least {@code shortAnswerMin}. */
    public record Limit(int setCountMin, int setCountMax, int setSize, int shortAnswerMin) { }

    private static final Set<String> KINDS = Set.of("HSA", "QDA");
    private static final Limit WITH_SHORT_ANSWER = new Limit(1, 2, 3, 1);
    private static final Limit WITHOUT_SHORT_ANSWER = new Limit(1, 2, 3, 0);

    private SubjectFormLimits() { }

    public static Optional<Limit> of(String kindCode, String subjectCode) {
        if (kindCode == null || subjectCode == null || !KINDS.contains(kindCode)) return Optional.empty();
        return switch (subjectCode) {
            case "LY", "HOA", "SINH" -> Optional.of(WITH_SHORT_ANSWER);
            case "SU", "DIA" -> Optional.of(WITHOUT_SHORT_ANSWER);
            default -> Optional.empty();
        };
    }
}
