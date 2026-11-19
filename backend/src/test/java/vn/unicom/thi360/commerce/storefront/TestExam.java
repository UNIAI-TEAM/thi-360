package vn.unicom.thi360.commerce.storefront;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import vn.unicom.thi360.exam.spi.PublishedExamPort.BlockSummary;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Combination;
import vn.unicom.thi360.exam.spi.PublishedExamPort.CombinationSubject;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Listing;
import vn.unicom.thi360.exam.spi.PublishedExamPort.OmittedGroup;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Option;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Part;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PartPath;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PathChoice;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Section;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingPath;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingPathPart;

/**
 * Builds what {@code PublishedExamPort.findServing} returns, with a random product code per test (plan CM-1, test data).
 * The default exam is a FULL HSA exam with one part of 50 questions in 75 minutes on one path.
 */
final class TestExam {

    UUID examId = UUID.randomUUID();
    String code = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    /** Order of the default part: random, so its part code {@code P<order>} is unique to this exam in the shared database. */
    final int partOrder = 5000 + ThreadLocalRandom.current().nextInt(100_000);
    String name = "Bai thi thu " + code;
    String kindCode = "HSA";
    UUID kindItemId;
    String scope = "FULL";
    String partCode;
    UUID subjectItemId;
    String buildMode = "STANDARD";
    String timing = "PER_PART";
    int versionNo = 1;
    String status = "PUBLISHED";
    Long listPrice = 199_000L;
    Integer attempts = 3;
    UUID coverAssetId;
    String summary = "Tom tat";
    List<Part> parts = new ArrayList<>(List.of(part("p1", partOrder, "Phan " + partOrder, "TOAN", 50, 75, 50)));
    List<ServingPath> paths = new ArrayList<>(List.of(path("default", 1, 50, 50, 75, "p1", "Phan " + partOrder)));
    List<Combination> combinations = new ArrayList<>();
    List<OmittedGroup> omitted = new ArrayList<>();
    Instant publishedAt = Instant.parse("2026-11-01T00:00:00Z");

    /** The same exam (same id and code) as another version, for re-serving it after a publish. */
    TestExam asVersion(int versionNo, String name, Long listPrice) {
        TestExam copy = new TestExam();
        copy.examId = examId;
        copy.code = code;
        copy.kindCode = kindCode;
        copy.kindItemId = kindItemId;
        copy.scope = scope;
        copy.partCode = partCode;
        copy.subjectItemId = subjectItemId;
        copy.buildMode = buildMode;
        copy.timing = timing;
        copy.status = status;
        copy.attempts = attempts;
        copy.coverAssetId = coverAssetId;
        copy.parts = new ArrayList<>(parts);
        copy.paths = new ArrayList<>(paths);
        copy.combinations = new ArrayList<>(combinations);
        copy.omitted = new ArrayList<>(omitted);
        copy.publishedAt = publishedAt;
        copy.versionNo = versionNo;
        copy.name = name;
        copy.listPrice = listPrice;
        return copy;
    }

    /** The part code the storefront derives for the default part of a FULL exam. */
    String defaultPartCode() {
        return "P" + partOrder;
    }

    TestExam kind(String code, UUID itemId) {
        this.kindCode = code;
        this.kindItemId = itemId;
        return this;
    }

    TestExam named(String name) {
        this.name = name;
        return this;
    }

    TestExam scope(String scope, String partCode, UUID subjectItemId) {
        this.scope = scope;
        this.partCode = partCode;
        this.subjectItemId = subjectItemId;
        return this;
    }

    TestExam review() {
        this.buildMode = "REVIEW";
        return this;
    }

    TestExam price(Long listPrice) {
        this.listPrice = listPrice;
        return this;
    }

    TestExam version(int versionNo) {
        this.versionNo = versionNo;
        return this;
    }

    TestExam discontinued() {
        this.status = "DISCONTINUED";
        return this;
    }

    TestExam published(Instant at) {
        this.publishedAt = at;
        return this;
    }

    TestExam cover(UUID assetId) {
        this.coverAssetId = assetId;
        return this;
    }

    TestExam withParts(List<Part> parts, List<ServingPath> paths) {
        this.parts = new ArrayList<>(parts);
        this.paths = new ArrayList<>(paths);
        return this;
    }

    /** The frozen question blocks of the default part (AD-77); empty is what a pre-block version returns. */
    TestExam withBlocks(List<BlockSummary> blocks) {
        Part base = parts.getFirst();
        parts.set(0, new Part(base.id(), base.order(), base.name(), base.durationMinutes(), base.questionCount(),
                base.questionCountOnPaper(), base.questionCountRequired(), base.participationMode(), base.choose(),
                base.maxPoints(), base.displaySegments(), base.sections(), base.subjectPool(), base.options(),
                base.paths(), List.copyOf(blocks)));
        return this;
    }

    TestExam withCombinations(List<Combination> combinations) {
        this.combinations = new ArrayList<>(combinations);
        return this;
    }

    ServingExam build() {
        Listing listing = new Listing(summary, "Muc tieu", "Doi tuong", listPrice, attempts, true, false, true,
                "Ghi chu", "SCORE_AND_ANSWERS", "Mo ta", coverAssetId);
        List<String> pathIds = paths.stream().map(ServingPath::pathId).toList();
        PublishedVersion version = new PublishedVersion(examId, versionNo, "SERVING", status, publishedAt, code, name,
                kindItemId, kindCode, scope, partCode, subjectItemId, buildMode, timing, null, new BigDecimal("150"),
                null, "LINEAR", listing, List.copyOf(parts), List.copyOf(combinations), List.copyOf(omitted), pathIds,
                pathIds);
        return new ServingExam(examId, status, version, List.copyOf(paths));
    }

    // ----------------------------------------------------------------- pieces

    /** A part with one fixed section of one subject and one path entry. */
    static Part part(String id, int order, String name, String subjectCode, int questions, int minutes, int points) {
        return new Part(id, order, name, minutes, questions, questions, questions, "ALL", null,
                BigDecimal.valueOf(points), List.of(), List.of(new Section("s-" + id, subjectCode, questions, "ANY")),
                List.of(), List.of(), List.of(new PartPath("default", questions, BigDecimal.valueOf(points), minutes)),
                List.of());
    }

    /** A part whose participation is a choice of options. */
    static Part choicePart(String id, int order, String name, int questions, int minutes, List<Option> options) {
        return new Part(id, order, name, minutes, questions, questions, questions, "CHOICE", 1,
                BigDecimal.valueOf(questions), List.of(), List.of(), List.of(), options, List.of(), List.of());
    }

    static Option option(String id, String kind, String code, String name, Integer chooseSubjects,
            List<Integer> countsByPick, List<Section> sections) {
        return new Option(id, kind, code, name, chooseSubjects, countsByPick, sections);
    }

    static Section section(String subjectCode, int questions) {
        return new Section("sec-" + subjectCode, subjectCode, questions, "ANY");
    }

    /** A one-part path with the totals given. */
    static ServingPath path(String pathId, int versionNo, Integer questions, Integer points, Integer minutes,
            String partId, String partName) {
        return new ServingPath(pathId, versionNo, List.of(), questions,
                points == null ? null : BigDecimal.valueOf(points), minutes,
                List.of(new ServingPathPart(partId, partName, questions,
                        points == null ? null : BigDecimal.valueOf(points), minutes)), List.of());
    }

    /** A path that stands for one chosen option of one part. */
    static ServingPath optionPath(String pathId, int versionNo, Integer questions, Integer points, Integer minutes,
            String partId, String partName, PathChoice choice) {
        return new ServingPath(pathId, versionNo, List.of(choice), questions,
                points == null ? null : BigDecimal.valueOf(points), minutes,
                List.of(new ServingPathPart(partId, partName, questions,
                        points == null ? null : BigDecimal.valueOf(points), minutes)), List.of());
    }

    static Combination combination(String partId, String code, String name, String branch, String... subjectCodes) {
        List<CombinationSubject> subjects = new ArrayList<>();
        for (int i = 0; i < subjectCodes.length; i++) {
            subjects.add(new CombinationSubject(subjectCodes[i], subjectCodes[i], i));
        }
        return new Combination(partId, code, name, branch, 1, subjects);
    }
}
