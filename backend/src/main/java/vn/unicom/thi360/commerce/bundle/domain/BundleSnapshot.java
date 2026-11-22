package vn.unicom.thi360.commerce.bundle.domain;

import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.commerce.storefront.api.DecimalRange;
import vn.unicom.thi360.commerce.storefront.api.IntRange;
import vn.unicom.thi360.commerce.storefront.api.LocalizedName;

/**
 * Immutable snapshot stored in {@code commerce.bundle_version.snapshot} at bundle publish time (TM 16.1).
 */
public record BundleSnapshot(
        String code,
        UUID examKindItemId,
        String name,
        String summary,
        String description,
        long listPriceVnd,
        int attemptsPerExam,
        BundleBenefits benefits,
        String resultView,
        List<SnapshotItem> items) {

    public BundleSnapshot {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public BundleSnapshot(
            String name,
            String summary,
            String description,
            long listPriceVnd,
            int attemptsPerExam,
            BundleBenefits benefits,
            String resultView,
            List<SnapshotItem> items) {
        this(null, null, name, summary, description, listPriceVnd, attemptsPerExam, benefits, resultView, items);
    }

    public record BundleBenefits(
            boolean aiExplanation,
            boolean strengthWeakness,
            boolean practice,
            String note) {
    }

    public record SnapshotItem(
            UUID examId,
            String code,
            String name,
            String scope,
            String partCode,
            UUID subjectItemId,
            String subjectCode,
            LocalizedName subjectName,
            String buildMode,
            int versionNo,
            IntRange questionCount,
            IntRange durationMinutes,
            DecimalRange maxPoints,
            int position) {
    }
}
