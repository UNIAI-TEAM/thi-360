package vn.unicom.thi360.commerce.bundle.application;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.bundle.api.BundleCandidateResponse;
import vn.unicom.thi360.commerce.bundle.api.BundleItemView;
import vn.unicom.thi360.commerce.bundle.domain.BundleSnapshot.SnapshotItem;
import vn.unicom.thi360.commerce.storefront.api.DecimalRange;
import vn.unicom.thi360.commerce.storefront.api.IntRange;
import vn.unicom.thi360.commerce.storefront.api.LocalizedName;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Listing;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Option;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Part;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Section;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingPath;

/**
 * Shared projection/snapshot helper turning {@link PublishedExamPort.ServingExam} into bundle items, candidates,
 * snapshots, and subject sets without querying {@code exam} tables directly (RULES 4).
 */
@Component
public class BundleExamSnapshotSupport {

    private static final String EXAM_GROUP = "EXAM";
    private static final String SUBJECT_GROUP = "SUBJECT";

    private final CatalogLookup catalogLookup;

    public BundleExamSnapshotSupport(CatalogLookup catalogLookup) {
        this.catalogLookup = catalogLookup;
    }

    public boolean isOnSale(ServingExam serving) {
        if (serving == null || !"PUBLISHED".equals(serving.status()) || serving.version() == null) {
            return false;
        }
        PublishedVersion v = serving.version();
        Listing listing = v.listing();
        return listing != null
                && listing.listPriceVnd() != null
                && listing.attemptsGranted() != null
                && v.code() != null
                && v.name() != null
                && v.scope() != null
                && v.kindCode() != null;
    }

    public boolean matchesExamKind(PublishedVersion version, CatalogItemView kindItem) {
        if (version == null || kindItem == null) {
            return false;
        }
        if (version.kindItemId() != null && kindItem.id().equals(version.kindItemId())) {
            return true;
        }
        return version.kindCode() != null && kindItem.code().equalsIgnoreCase(version.kindCode().trim());
    }

    public boolean isAllowedScope(PublishedVersion version) {
        if (version == null || version.scope() == null) {
            return false;
        }
        return "PART".equals(version.scope()) || "SUBJECT".equals(version.scope());
    }

    public Optional<CatalogItemView> resolveExamKind(String rawExamKind) {
        if (rawExamKind == null || rawExamKind.isBlank()) {
            return Optional.empty();
        }
        String trimmed = rawExamKind.trim();
        try {
            UUID id = UUID.fromString(trimmed);
            return catalogLookup.findItem(id)
                    .filter(item -> EXAM_GROUP.equals(item.groupCode()));
        } catch (IllegalArgumentException ignored) {
            return catalogLookup.findActiveItem(EXAM_GROUP, trimmed)
                    .or(() -> catalogLookup.listGroupItems(EXAM_GROUP).stream()
                            .filter(item -> item.code().equalsIgnoreCase(trimmed))
                            .findFirst());
        }
    }

    public Map<UUID, CatalogItemView> examKindsById() {
        Map<UUID, CatalogItemView> byId = new LinkedHashMap<>();
        for (CatalogItemView item : catalogLookup.listGroupItems(EXAM_GROUP)) {
            byId.put(item.id(), item);
        }
        return byId;
    }

    public SubjectCatalog subjectCatalog() {
        Map<UUID, CatalogItemView> byId = new LinkedHashMap<>();
        Map<String, CatalogItemView> byCode = new LinkedHashMap<>();
        for (CatalogItemView item : catalogLookup.listGroupItems(SUBJECT_GROUP)) {
            byId.put(item.id(), item);
            byCode.putIfAbsent(normalizeKey(item.code()), item);
        }
        return new SubjectCatalog(byId, byCode);
    }

    public BundleCandidateResponse toCandidate(ServingExam serving, SubjectCatalog subjects) {
        PublishedVersion v = serving.version();
        ExamMetrics metrics = computeMetrics(serving);
        SubjectInfo subject = resolveSubject(v, subjects);
        String buildMode = "REVIEW".equals(v.buildMode()) ? "REVIEW" : "STANDARD";
        Long price = v.listing() != null ? v.listing().listPriceVnd() : null;
        return new BundleCandidateResponse(
                serving.examId(),
                v.code(),
                v.name(),
                v.scope(),
                v.partCode(),
                v.subjectItemId(),
                subject.code(),
                subject.name(),
                buildMode,
                metrics.questionCount(),
                metrics.durationMinutes(),
                metrics.maxPoints(),
                v.versionNo(),
                price);
    }

    public SnapshotItem toSnapshotItem(ServingExam serving, int position, SubjectCatalog subjects) {
        PublishedVersion v = serving.version();
        ExamMetrics metrics = computeMetrics(serving);
        SubjectInfo subject = resolveSubject(v, subjects);
        String buildMode = "REVIEW".equals(v.buildMode()) ? "REVIEW" : "STANDARD";
        return new SnapshotItem(
                serving.examId(),
                v.code(),
                v.name(),
                v.scope(),
                v.partCode(),
                v.subjectItemId(),
                subject.code(),
                subject.name(),
                buildMode,
                v.versionNo(),
                metrics.questionCount(),
                metrics.durationMinutes(),
                metrics.maxPoints(),
                position);
    }

    public BundleItemView toItemView(
            UUID examId,
            int position,
            ServingExam serving,
            SnapshotItem snapshotFallback,
            SubjectCatalog subjects) {
        if (serving != null && serving.version() != null) {
            PublishedVersion v = serving.version();
            ExamMetrics metrics = computeMetrics(serving);
            SubjectInfo subject = resolveSubject(v, subjects);
            String buildMode = "REVIEW".equals(v.buildMode()) ? "REVIEW" : "STANDARD";
            String saleState = isOnSale(serving) ? "ON_SALE"
                    : ("DISCONTINUED".equals(serving.status()) ? "DISCONTINUED" : "UNAVAILABLE");
            return new BundleItemView(
                    examId,
                    v.code(),
                    v.name(),
                    v.scope(),
                    v.partCode(),
                    v.subjectItemId(),
                    subject.code(),
                    subject.name(),
                    buildMode,
                    metrics.questionCount(),
                    metrics.durationMinutes(),
                    metrics.maxPoints(),
                    v.versionNo(),
                    saleState,
                    position);
        }
        if (snapshotFallback != null) {
            return new BundleItemView(
                    examId,
                    snapshotFallback.code(),
                    snapshotFallback.name(),
                    snapshotFallback.scope(),
                    snapshotFallback.partCode(),
                    snapshotFallback.subjectItemId(),
                    snapshotFallback.subjectCode(),
                    snapshotFallback.subjectName(),
                    snapshotFallback.buildMode(),
                    snapshotFallback.questionCount(),
                    snapshotFallback.durationMinutes(),
                    snapshotFallback.maxPoints(),
                    snapshotFallback.versionNo(),
                    "UNAVAILABLE",
                    position);
        }
        return new BundleItemView(
                examId, null, null, null, null, null, null, null, null, null, null, null, null,
                "UNAVAILABLE", position);
    }

    public ExamMetrics computeMetrics(ServingExam serving) {
        List<ServingPath> paths = serving.servingPaths();
        IntRange questions = intRange(paths.stream().map(ServingPath::questionCount).toList());
        IntRange minutes = intRange(paths.stream().map(ServingPath::durationMinutes).toList());
        DecimalRange points = decimalRange(paths.stream().map(ServingPath::maxPoints).toList());

        PublishedVersion v = serving.version();
        if (questions == null && v != null && !v.parts().isEmpty()) {
            int sum = 0;
            boolean any = false;
            for (Part p : v.parts()) {
                if (p.questionCount() != null) {
                    sum += p.questionCount();
                    any = true;
                }
            }
            if (any) {
                questions = new IntRange(sum, sum);
            }
        }
        if (minutes == null && v != null) {
            if (v.durationMinutes() != null) {
                minutes = new IntRange(v.durationMinutes(), v.durationMinutes());
            } else if (!v.parts().isEmpty()) {
                int sum = 0;
                boolean any = false;
                for (Part p : v.parts()) {
                    if (p.durationMinutes() != null) {
                        sum += p.durationMinutes();
                        any = true;
                    }
                }
                if (any) {
                    minutes = new IntRange(sum, sum);
                }
            }
        }
        if (points == null && v != null) {
            if (v.maxScore() != null) {
                points = new DecimalRange(v.maxScore(), v.maxScore());
            } else if (!v.parts().isEmpty()) {
                BigDecimal sum = BigDecimal.ZERO;
                boolean any = false;
                for (Part p : v.parts()) {
                    if (p.maxPoints() != null) {
                        sum = sum.add(p.maxPoints());
                        any = true;
                    }
                }
                if (any) {
                    points = new DecimalRange(sum, sum);
                }
            }
        }
        return new ExamMetrics(questions, minutes, points);
    }

    public Set<UUID> collectSubjectIds(ServingExam serving, SubjectCatalog subjects) {
        Set<UUID> ids = new LinkedHashSet<>();
        PublishedVersion v = serving.version();
        if (v == null) {
            return ids;
        }
        if (v.subjectItemId() != null) {
            ids.add(v.subjectItemId());
        }
        Set<String> codes = new LinkedHashSet<>();
        for (Part part : v.parts()) {
            for (Section s : part.sections()) {
                addCode(codes, s.subjectCode());
            }
            for (Section s : part.subjectPool()) {
                addCode(codes, s.subjectCode());
            }
            for (Option o : part.options()) {
                if ("SUBJECT".equals(o.kind())) {
                    addCode(codes, o.code());
                }
                for (Section s : o.sections()) {
                    addCode(codes, s.subjectCode());
                }
            }
        }
        for (ServingPath path : serving.servingPaths()) {
            path.combinations().forEach(c -> c.subjects().forEach(cs -> addCode(codes, cs.code())));
        }
        for (String code : codes) {
            CatalogItemView item = subjects.byCode().get(normalizeKey(code));
            if (item != null) {
                ids.add(item.id());
            }
        }
        return ids;
    }

    public SubjectInfo resolveSubject(PublishedVersion version, SubjectCatalog subjects) {
        if (version == null) {
            return new SubjectInfo(null, null);
        }
        if (version.subjectItemId() != null) {
            CatalogItemView item = subjects.byId().get(version.subjectItemId());
            if (item == null) {
                item = catalogLookup.findItem(version.subjectItemId()).orElse(null);
            }
            if (item != null) {
                return new SubjectInfo(item.code(), new LocalizedName(item.nameVi(), item.nameEn()));
            }
        }
        Set<String> partSubjectCodes = new LinkedHashSet<>();
        for (Part part : version.parts()) {
            for (Section section : part.sections()) {
                addCode(partSubjectCodes, section.subjectCode());
            }
        }
        if (partSubjectCodes.size() == 1) {
            String onlyCode = partSubjectCodes.iterator().next();
            CatalogItemView item = subjects.byCode().get(normalizeKey(onlyCode));
            if (item != null) {
                return new SubjectInfo(item.code(), new LocalizedName(item.nameVi(), item.nameEn()));
            }
            return new SubjectInfo(onlyCode, new LocalizedName(onlyCode, onlyCode));
        }
        return new SubjectInfo(null, null);
    }

    private static void addCode(Set<String> target, String code) {
        if (code != null && !code.isBlank()) {
            target.add(code.trim());
        }
    }

    private static String normalizeKey(String code) {
        return code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
    }

    private static IntRange intRange(List<Integer> values) {
        Integer min = null;
        Integer max = null;
        for (Integer value : values) {
            if (value == null) {
                continue;
            }
            min = min == null || value < min ? value : min;
            max = max == null || value > max ? value : max;
        }
        return min == null ? null : new IntRange(min, max);
    }

    private static DecimalRange decimalRange(List<BigDecimal> values) {
        BigDecimal min = null;
        BigDecimal max = null;
        for (BigDecimal value : values) {
            if (value == null) {
                continue;
            }
            min = min == null || value.compareTo(min) < 0 ? value : min;
            max = max == null || value.compareTo(max) > 0 ? value : max;
        }
        return min == null ? null : new DecimalRange(min, max);
    }

    public record ExamMetrics(IntRange questionCount, IntRange durationMinutes, DecimalRange maxPoints) {
    }

    public record SubjectInfo(String code, LocalizedName name) {
    }

    public record SubjectCatalog(Map<UUID, CatalogItemView> byId, Map<String, CatalogItemView> byCode) {
    }
}
