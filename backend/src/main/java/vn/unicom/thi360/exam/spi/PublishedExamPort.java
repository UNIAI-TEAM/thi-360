package vn.unicom.thi360.exam.spi;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read gate to what is published (TM 3, EX-1): for the storefront (commerce), bundles and the attempt module. It
 * reads the frozen {@code exam_version} snapshot, never the working copy or a current template, so what it returns
 * does not move after publish. It never carries questions, answers, question sources or witness papers.
 */
public interface PublishedExamPort {

    /**
     * The serving state of an exam: the latest version that still serves a path (E24) with its listing, and the
     * serving paths of EVERY serving version, each with the version that serves it (BCA and Informatics can keep older
     * versions serving: one {@code ServingPath} per path, {@code versionNo} of the version that serves it) and its
     * totals, per-part totals and combinations taken from that version, so {@code loadVersion} is not needed for them.
     * {@code status} and the versions are read from one database snapshot. Empty for an exam that was never published.
     * A discontinued exam stays readable: {@code status} is {@code DISCONTINUED} and the last serving version is returned.
     */
    Optional<ServingExam> findServing(UUID examId);

    /** One version as it was published; empty when the exam has no such version. */
    Optional<PublishedVersion> loadVersion(UUID examId, int versionNo);

    /** Every exam that is on sale or was discontinued (never a draft), for rebuilding the storefront projection. */
    List<UUID> listPublishedExamIds();

    /** The cover image of an asset; empty when the asset or its object is gone. */
    Optional<CoverImage> openCover(UUID assetId);

    record CoverImage(String contentType, byte[] content) { }

    record ServingExam(UUID examId, String status, PublishedVersion version, List<ServingPath> servingPaths) { }

    /**
     * A serving path as the version that serves it froze it (R2-4): its choices, the totals of the whole path
     * ({@code questionCount}, {@code maxPoints}, {@code durationMinutes}; null when that version's snapshot could not
     * total them, a PER_PART path sums its parts' minutes, a SHARED one carries the exam's minutes), the same per part,
     * and the combination codes the path stands for with their subjects. One {@code findServing} call is enough for
     * AC-21, whichever serving version a path stays on.
     */
    record ServingPath(String pathId, int versionNo, List<PathChoice> choices, Integer questionCount,
                       BigDecimal maxPoints, Integer durationMinutes, List<ServingPathPart> parts,
                       List<Combination> combinations) { }

    /** What one part contributes to a serving path, from the version that serves it. */
    record ServingPathPart(String partId, String name, Integer questionCount, BigDecimal maxPoints,
                           Integer durationMinutes) { }

    record PathChoice(String partId, String optionId, String kind, String code, String name) { }

    /**
     * @param status {@code PUBLISHED} or {@code DISCONTINUED}
     * @param buildMode {@code STANDARD} or {@code REVIEW} (a review exam is sold with a label, BS 2)
     * @param kindCode code of the exam kind of the catalog (HSA, QDA, HSCA, ...)
     * @param listing the sale listing frozen at publish; null for a version published before the listing existed
     */
    record PublishedVersion(UUID examId, int versionNo, String state, String status, Instant publishedAt,
                            String code, String name, UUID kindItemId, String kindCode, String scope, String partCode,
                            UUID subjectItemId, String buildMode, String timing, Integer durationMinutes,
                            BigDecimal maxScore, BigDecimal referenceMax, String practiceScoring, Listing listing,
                            List<Part> parts, List<Combination> combinations, List<OmittedGroup> omittedGroups,
                            List<String> publishedPathIds, List<String> servingPathIds) { }

    record Listing(String summary, String objectives, String audience, Long listPriceVnd, Integer attemptsGranted,
                   boolean benefitAiExplanation, boolean benefitStrengthWeakness, boolean benefitPractice,
                   String benefitNote, String resultView, String description, UUID coverAssetId) { }

    /**
     * A part of the version. {@code paths} holds, per serving path, the questions, maximum points and minutes the part
     * contributes (null when the snapshot could not total them). {@code blocks} summarizes the frozen question blocks:
     * one entry per block of the part's sections, subject pool and options (TM 10.1 answer types, AD-77); empty for a
     * version published before the snapshot stored them.
     */
    record Part(String id, Integer order, String name, Integer durationMinutes, Integer questionCount,
                Integer questionCountOnPaper, Integer questionCountRequired, String participationMode, Integer choose,
                BigDecimal maxPoints, List<Segment> displaySegments, List<Section> sections, List<Section> subjectPool,
                List<Option> options, List<PartPath> paths, List<BlockSummary> blocks) { }

    /**
     * One frozen question block, reduced to what the storefront shows (TM 10.1): the answer type and how many
     * questions it carries (a set block counts its child questions), plus {@code childAnswerType} when the block is a
     * set of one child type. Never a question, an answer or a source.
     */
    record BlockSummary(String questionType, int questionCount, String childAnswerType) { }

    /** A display segment (BS 17.3): a heading before position {@code fromPosition}, no clock or score of its own. */
    record Segment(String code, String name, Integer fromPosition, Integer toPosition, Integer commandCount,
                   BigDecimal maxPoints) { }

    /** @param audioRequirement {@code ANY}, {@code REQUIRED} or {@code FORBIDDEN} (BS 17.2) */
    record Section(String id, String subjectCode, Integer questionCount, String audioRequirement) { }

    record Option(String id, String kind, String code, String name, Integer chooseSubjects,
                  List<Integer> countsByPick, List<Section> sections) { }

    record PartPath(String pathId, Integer questionCount, BigDecimal maxPoints, Integer durationMinutes) { }

    /** A combination code of the catalog frozen at publish (2B 9), with its subjects in order. */
    record Combination(String partId, String code, String name, String branch, Integer versionNo,
                       List<CombinationSubject> subjects) { }

    record CombinationSubject(String code, String name, int position) { }

    /** An essay group left out of the exam (2B K1): its name and the points the part would have carried. */
    record OmittedGroup(String name, BigDecimal points) { }
}
