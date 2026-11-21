package vn.unicom.thi360.attempt.application;

import java.util.Locale;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.attempt.domain.NavigationMode;

/**
 * Policy governing per-exam-kind attempt navigation rules (BS §4, LT L7/F7/F19, AT-8).
 *
 * <p>THPTQG attempts run in {@link NavigationMode#FREE}: all required parts open on start with a
 * shared deadline, manual part finishing is rejected ({@code 409 PART_FINISH_NOT_ALLOWED}), and
 * submission is permitted from any part. All other exam kinds stay {@link NavigationMode#SEQUENTIAL}.
 */
@Component
public class AttemptNavigationPolicy {

    public static final String FREE_NAVIGATION_EXAM_KIND = "THPTQG";

    private static final AttemptNavigationPolicy DEFAULT = new AttemptNavigationPolicy();

    public static AttemptNavigationPolicy defaultPolicy() {
        return DEFAULT;
    }

    /** Static convenience helper resolving the navigation mode for an exam kind code. */
    public static NavigationMode resolveModeForExamKind(String examKindCode) {
        if (examKindCode == null) {
            return NavigationMode.SEQUENTIAL;
        }
        String normalized = examKindCode.trim().toUpperCase(Locale.ROOT);
        return FREE_NAVIGATION_EXAM_KIND.equals(normalized)
                ? NavigationMode.FREE
                : NavigationMode.SEQUENTIAL;
    }

    /** Resolves the snapshotted {@link NavigationMode} for an exam kind code at attempt start. */
    public NavigationMode resolveForExamKind(String examKindCode) {
        return resolveModeForExamKind(examKindCode);
    }

    /** Returns {@code true} when all non-after-start parts should open immediately on start. */
    public boolean opensAllPartsOnStart(NavigationMode mode) {
        return mode == NavigationMode.FREE;
    }

    /** Returns {@code true} when manual per-part finishing is allowed for the attempt mode. */
    public boolean allowsPartFinish(NavigationMode mode) {
        return mode == NavigationMode.SEQUENTIAL;
    }

    /** Returns {@code true} when submission requires all prior parts finished and last part open. */
    public boolean requiresLastPartForSubmit(NavigationMode mode) {
        return mode == NavigationMode.SEQUENTIAL;
    }
}
