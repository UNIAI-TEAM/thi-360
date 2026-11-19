package vn.unicom.thi360.commerce.storefront.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.storefront.api.AnswerTypeGroup;
import vn.unicom.thi360.commerce.storefront.api.DecimalRange;
import vn.unicom.thi360.commerce.storefront.api.DetailBenefits;
import vn.unicom.thi360.commerce.storefront.api.DetailChoiceGroup;
import vn.unicom.thi360.commerce.storefront.api.DetailChoiceOption;
import vn.unicom.thi360.commerce.storefront.api.DetailPart;
import vn.unicom.thi360.commerce.storefront.api.DetailScoring;
import vn.unicom.thi360.commerce.storefront.api.ExamKindRef;
import vn.unicom.thi360.commerce.storefront.api.IntRange;
import vn.unicom.thi360.commerce.storefront.api.LocalizedName;
import vn.unicom.thi360.commerce.storefront.api.OmittedEssayGroup;
import vn.unicom.thi360.commerce.storefront.api.ProductCard;
import vn.unicom.thi360.commerce.storefront.api.ProductDetail;
import vn.unicom.thi360.commerce.storefront.api.SubjectAnswerTypes;
import vn.unicom.thi360.commerce.storefront.api.SubjectRef;
import vn.unicom.thi360.commerce.storefront.domain.SaleState;
import vn.unicom.thi360.commerce.storefront.domain.StorefrontRow;
import vn.unicom.thi360.commerce.storefront.domain.StorefrontSearchText;
import vn.unicom.thi360.exam.spi.PublishedExamPort.BlockSummary;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Combination;
import vn.unicom.thi360.exam.spi.PublishedExamPort.CombinationSubject;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Listing;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Option;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Part;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PathChoice;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Section;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingPath;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingPathPart;

/**
 * Builds the storefront row of an exam from what {@code PublishedExamPort.findServing} returns (TM 10.1, E24): sale data
 * of the latest serving version, totals read from the serving paths (a range when the paths differ), the subjects,
 * parts and choices of every serving version (each path is read from the version that serves it), and the prebuilt card
 * and detail. It reads nothing but its arguments and the catalog, and never carries questions, answers, question
 * sources, witness papers or previews: the port has none.
 */
@Component
public class StorefrontProjector {

    public static final String COVER_URL_PREFIX = "/api/v1/public/storefront/covers/";
    private static final String EXAM_GROUP = "EXAM";
    private static final String SUBJECT_GROUP = "SUBJECT";
    private static final String COMBINATION = "COMBINATION";

    private final CatalogLookup catalog;
    private final ObjectMapper mapper;

    public StorefrontProjector(CatalogLookup catalog, ObjectMapper mapper) {
        this.catalog = catalog;
        this.mapper = mapper;
    }

    /**
     * The row to store, or empty when the serving version cannot be sold (no listing, price, attempts, code or name).
     *
     * @param versions the serving versions by number (the latest included or not): one entry for every version a serving
     *                 path names, as {@code PublishedExamPort.loadVersion} returns them
     */
    public Optional<StorefrontRow> build(ServingExam serving, Map<Integer, PublishedVersion> versions) {
        PublishedVersion version = serving.version();
        Listing listing = version.listing();
        if (listing == null || listing.listPriceVnd() == null || listing.attemptsGranted() == null
                || version.code() == null || version.name() == null || version.scope() == null
                || version.kindCode() == null) {
            return Optional.empty();
        }
        Map<String, CatalogItemView> subjectsByCode = subjectsByCode();
        ExamKindRef kind = kindRef(version);
        List<ServingPath> paths = serving.servingPaths();
        List<VersionView> views = views(serving, versions);

        IntRange questions = intRange(paths.stream().map(ServingPath::questionCount).toList());
        IntRange minutes = intRange(paths.stream().map(ServingPath::durationMinutes).toList());
        DecimalRange points = decimalRange(paths.stream().map(ServingPath::maxPoints).toList());

        Set<UUID> subjectIds = new LinkedHashSet<>();
        if (version.subjectItemId() != null) {
            subjectIds.add(version.subjectItemId());
        }
        for (String code : subjectCodes(views)) {
            CatalogItemView item = subjectsByCode.get(key(code));
            if (item != null) {
                subjectIds.add(item.id());
            }
        }
        List<String> partCodes = new ArrayList<>();
        List<String> partNames = new ArrayList<>();
        for (VersionView view : views) {
            parts(view.version(), partCodes, partNames);
        }

        LocalizedName scopeLabel = scopeLabel(version, subjectsByCode, partNames);
        String coverUrl = listing.coverAssetId() == null ? null : COVER_URL_PREFIX + listing.coverAssetId();
        String buildMode = "REVIEW".equals(version.buildMode()) ? "REVIEW" : "STANDARD";
        long listPrice = listing.listPriceVnd();

        ProductCard card = new ProductCard(version.code(), version.name(), listing.summary(), coverUrl, kind,
                version.scope(), scopeLabel, buildMode, questions, minutes, listing.attemptsGranted(), listPrice,
                listPrice, null, version.versionNo());
        ProductDetail detail = new ProductDetail(version.code(), version.name(), listing.summary(), coverUrl, kind,
                version.scope(), scopeLabel, buildMode, questions, minutes, points, listing.attemptsGranted(),
                listPrice, listPrice, null, version.versionNo(),
                blankToNull(listing.description()) != null ? listing.description() : listing.summary(),
                listing.objectives(), listing.audience(), detailParts(views, paths), choices(views, subjectsByCode),
                new DetailScoring(version.timing(), version.maxScore(), version.referenceMax(),
                        version.practiceScoring()),
                version.omittedGroups().stream().map(g -> new OmittedEssayGroup(g.name(), g.points())).toList(),
                new DetailBenefits(listing.benefitAiExplanation(), listing.benefitStrengthWeakness(),
                        listing.benefitPractice(), listing.benefitNote()),
                listing.resultView());

        SaleState state = "DISCONTINUED".equals(serving.status()) ? SaleState.DISCONTINUED : SaleState.ON_SALE;
        StorefrontRow unhashed = new StorefrontRow(serving.examId(), version.code(), key(version.code()),
                version.versionNo(), state, version.name(), StorefrontSearchText.of(version.name(), version.code()),
                version.kindItemId(), version.kindCode(), version.scope(), version.subjectItemId(),
                List.copyOf(subjectIds), partCodes, partNames, questions == null ? null : questions.min(),
                questions == null ? null : questions.max(), minutes == null ? null : minutes.min(),
                minutes == null ? null : minutes.max(), points == null ? null : points.min(),
                points == null ? null : points.max(), buildMode, listPrice, listing.attemptsGranted(),
                listing.coverAssetId(), json(card), json(detail), "", version.publishedAt());
        return Optional.of(withHash(unhashed));
    }

    /** A serving version with the paths it serves (E24): what memberships and choices are read from (R1-5). */
    private record VersionView(PublishedVersion version, List<ServingPath> paths) { }

    /** Every distinct serving version, the latest first, each with its own paths. */
    private static List<VersionView> views(ServingExam serving, Map<Integer, PublishedVersion> versions) {
        PublishedVersion latest = serving.version();
        Map<Integer, List<ServingPath>> byVersion = new TreeMap<>(Comparator.reverseOrder());
        byVersion.computeIfAbsent(latest.versionNo(), v -> new ArrayList<>());
        for (ServingPath path : serving.servingPaths()) {
            byVersion.computeIfAbsent(path.versionNo(), v -> new ArrayList<>()).add(path);
        }
        List<VersionView> views = new ArrayList<>();
        for (Map.Entry<Integer, List<ServingPath>> entry : byVersion.entrySet()) {
            PublishedVersion version = entry.getKey() == latest.versionNo() ? latest : versions.get(entry.getKey());
            if (version == null) {
                throw new IllegalStateException(
                        "Serving version " + entry.getKey() + " of exam " + serving.examId() + " was not loaded");
            }
            views.add(new VersionView(version, List.copyOf(entry.getValue())));
        }
        return views;
    }

    // ------------------------------------------------------------------ catalog

    private Map<String, CatalogItemView> subjectsByCode() {
        Map<String, CatalogItemView> byCode = new LinkedHashMap<>();
        for (CatalogItemView item : catalog.listGroupItems(SUBJECT_GROUP)) {
            byCode.putIfAbsent(key(item.code()), item);
        }
        return byCode;
    }

    private ExamKindRef kindRef(PublishedVersion version) {
        if (version.kindItemId() != null) {
            Optional<CatalogItemView> item = catalog.findItem(version.kindItemId());
            if (item.isPresent()) {
                return new ExamKindRef(version.kindCode(), item.get().nameVi(), item.get().nameEn());
            }
        }
        for (CatalogItemView item : catalog.listGroupItems(EXAM_GROUP)) {
            if (key(item.code()).equals(key(version.kindCode()))) {
                return new ExamKindRef(version.kindCode(), item.nameVi(), item.nameEn());
            }
        }
        return new ExamKindRef(version.kindCode(), version.kindCode(), version.kindCode());
    }

    private static SubjectRef subjectRef(String code, Map<String, CatalogItemView> byCode) {
        CatalogItemView item = byCode.get(key(code));
        return item == null ? new SubjectRef(code, code, code) : new SubjectRef(code, item.nameVi(), item.nameEn());
    }

    // ----------------------------------------------------------- subjects, parts

    /**
     * Subject codes of the serving paths, over every serving version (R1-5): fixed sections and pools, chosen options of
     * the paths that version serves, and the codes' subjects in order.
     */
    private static Set<String> subjectCodes(List<VersionView> views) {
        Set<String> codes = new LinkedHashSet<>();
        for (VersionView view : views) {
            Set<String> servedOptions = servedOptionIds(view.paths());
            for (Part part : view.version().parts()) {
                part.sections().forEach(s -> addCode(codes, s.subjectCode()));
                part.subjectPool().forEach(s -> addCode(codes, s.subjectCode()));
                for (Option option : part.options()) {
                    if (!servedOptions.contains(option.id())) {
                        continue;
                    }
                    if ("SUBJECT".equals(option.kind())) {
                        addCode(codes, option.code());
                    }
                    option.sections().forEach(s -> addCode(codes, s.subjectCode()));
                }
            }
            for (ServingPath path : view.paths()) {
                for (Combination combination : path.combinations()) {
                    combination.subjects().forEach(s -> addCode(codes, s.code()));
                }
            }
        }
        return codes;
    }

    private static void addCode(Set<String> codes, String code) {
        if (code != null && !code.isBlank()) {
            codes.add(code.trim());
        }
    }

    private static Set<String> servedOptionIds(List<ServingPath> paths) {
        Set<String> ids = new LinkedHashSet<>();
        for (ServingPath path : paths) {
            for (PathChoice choice : path.choices()) {
                ids.add(choice.optionId());
            }
        }
        return ids;
    }

    /**
     * Part code and name for the "Phần" filter (AD-78): only a PART exam has one, the code of its scope. A FULL exam has
     * no single part and is found through the format filter; a subject exam through the subject filter.
     */
    private static void parts(PublishedVersion version, List<String> codes, List<String> names) {
        List<Part> parts = version.parts();
        if ("PART".equals(version.scope()) && version.partCode() != null && !codes.contains(version.partCode())) {
            codes.add(version.partCode());
            names.add(parts.isEmpty() || parts.getFirst().name() == null ? version.partCode() : parts.getFirst().name());
        }
    }

    private static LocalizedName scopeLabel(PublishedVersion version, Map<String, CatalogItemView> subjectsByCode,
            List<String> partNames) {
        if ("PART".equals(version.scope())) {
            String name = partNames.isEmpty() ? version.partCode() : partNames.getFirst();
            return name == null ? null : new LocalizedName(name, name);
        }
        if ("SUBJECT".equals(version.scope()) && version.subjectItemId() != null) {
            for (CatalogItemView item : subjectsByCode.values()) {
                if (item.id().equals(version.subjectItemId())) {
                    return new LocalizedName(item.nameVi(), item.nameEn());
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------- detail

    private static List<DetailPart> detailParts(List<VersionView> views, List<ServingPath> paths) {
        // Every part of every serving version, once by id; the newest version that has the part defines it (R1-5).
        List<Part> ordered = new ArrayList<>();
        Set<String> seenIds = new LinkedHashSet<>();
        for (VersionView view : views) {
            for (Part part : view.version().parts()) {
                if (part.id() == null || seenIds.add(part.id())) {
                    ordered.add(part);
                }
            }
        }
        ordered.sort(Comparator.comparing(Part::order, Comparator.nullsLast(Comparator.naturalOrder())));
        List<DetailPart> result = new ArrayList<>();
        int position = 0;
        for (Part part : ordered) {
            position++;
            List<ServingPathPart> served = new ArrayList<>();
            for (ServingPath path : paths) {
                for (ServingPathPart candidate : path.parts()) {
                    if (part.id() != null && part.id().equals(candidate.partId())) {
                        served.add(candidate);
                    }
                }
            }
            IntRange questions = served.isEmpty() ? single(part.questionCount())
                    : intRange(served.stream().map(ServingPathPart::questionCount).toList());
            IntRange minutes = served.isEmpty() ? single(part.durationMinutes())
                    : intRange(served.stream().map(ServingPathPart::durationMinutes).toList());
            DecimalRange points = served.isEmpty() ? singleDecimal(part.maxPoints())
                    : decimalRange(served.stream().map(ServingPathPart::maxPoints).toList());
            result.add(new DetailPart(part.order() != null ? part.order() : position, part.name(), questions, minutes,
                    points, !"CHOICE".equals(part.participationMode()), fixedAnswerTypes(part)));
        }
        return result;
    }

    /**
     * Answer-type rows of the blocks given (TM 10.1): merged by type and child type, counts summed. A block left out of
     * the exam (omitted) and a block with no type (a review exam's "any") say nothing the learner answers.
     */
    private static List<AnswerTypeGroup> answerTypes(List<BlockSummary> blocks) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, BlockSummary> first = new LinkedHashMap<>();
        for (BlockSummary block : blocks) {
            if (block.omitted() || block.questionType() == null) {
                continue;
            }
            String key = block.questionType() + "|" + (block.childAnswerType() == null ? "" : block.childAnswerType());
            counts.merge(key, block.questionCount(), Integer::sum);
            first.putIfAbsent(key, block);
        }
        List<AnswerTypeGroup> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            BlockSummary block = first.get(entry.getKey());
            result.add(new AnswerTypeGroup(block.questionType(), entry.getValue(), block.childAnswerType()));
        }
        return result;
    }

    /**
     * What every learner answers in the part: the blocks of its fixed sections. Exclusive options and the candidates of a
     * subject pool are never added up here (AD-78); they are reported per choice, see {@link #optionOf}.
     */
    private static List<AnswerTypeGroup> fixedAnswerTypes(Part part) {
        Set<String> poolIds = new LinkedHashSet<>();
        part.subjectPool().forEach(s -> poolIds.add(s.id()));
        return answerTypes(part.blocks().stream()
                .filter(b -> b.optionId() == null && !poolIds.contains(b.sectionId())).toList());
    }

    /** The blocks of the subject-pool sections of the part that belong to this subject, per subject (AD-78). */
    private static List<AnswerTypeGroup> poolAnswerTypes(Part part, String subjectCode) {
        Set<String> sectionIds = new LinkedHashSet<>();
        for (Section section : part.subjectPool()) {
            if (section.subjectCode() != null && key(section.subjectCode()).equals(key(subjectCode))) {
                sectionIds.add(section.id());
            }
        }
        return answerTypes(part.blocks().stream()
                .filter(b -> b.optionId() == null && sectionIds.contains(b.sectionId())).toList());
    }

    /** Accumulates the options of one part over every serving version. */
    private static final class ChoiceGroupBuilder {
        final String partName;
        final Map<String, DetailChoiceOption> options = new LinkedHashMap<>();

        ChoiceGroupBuilder(String partName) {
            this.partName = partName;
        }

        void add(String id, DetailChoiceOption option) {
            options.merge(id, option, StorefrontProjector::mergeChoice);
        }
    }

    /**
     * The choices of the serving paths, per part: the options a path stands for and the catalog codes (E25), read from
     * the version that serves each path and united over every serving version (R1-5).
     */
    private static List<DetailChoiceGroup> choices(List<VersionView> views,
            Map<String, CatalogItemView> subjectsByCode) {
        Map<String, ChoiceGroupBuilder> groups = new LinkedHashMap<>();
        for (VersionView view : views) {
            for (Part part : view.version().parts()) {
                ChoiceGroupBuilder group = groups.computeIfAbsent(
                        part.id() == null ? "name:" + part.name() : part.id(), k -> new ChoiceGroupBuilder(part.name()));
                for (ServingPath path : view.paths()) {
                    for (PathChoice choice : path.choices()) {
                        if (!Objects.equals(part.id(), choice.partId()) || COMBINATION.equals(choice.kind())) {
                            continue;
                        }
                        Option option = part.options().stream().filter(o -> choice.optionId().equals(o.id()))
                                .findFirst().orElse(null);
                        group.add("option:" + choice.optionId(), optionOf(choice, option, part, subjectsByCode));
                    }
                }
                for (ServingPath path : view.paths()) {
                    for (Combination combination : path.combinations()) {
                        if (Objects.equals(part.id(), combination.partId())) {
                            List<CombinationSubject> ordered = combination.subjects().stream()
                                    .sorted(Comparator.comparingInt(s -> s.position())).toList();
                            List<SubjectAnswerTypes> perSubject = new ArrayList<>();
                            for (CombinationSubject subject : ordered) {
                                List<AnswerTypeGroup> types = poolAnswerTypes(part, subject.code());
                                if (!types.isEmpty()) {
                                    perSubject.add(new SubjectAnswerTypes(subjectRef(subject.code(), subjectsByCode), types));
                                }
                            }
                            group.add("combination:" + combination.code(), new DetailChoiceOption(COMBINATION,
                                    combination.code(), combination.name() == null ? combination.code() : combination.name(),
                                    combination.branch(), null, null,
                                    ordered.stream().map(s -> subjectRef(s.code(), subjectsByCode)).toList(),
                                    List.of(), perSubject));
                        }
                    }
                }
            }
        }
        List<DetailChoiceGroup> result = new ArrayList<>();
        for (ChoiceGroupBuilder group : groups.values()) {
            if (!group.options.isEmpty()) {
                result.add(new DetailChoiceGroup(group.partName, List.copyOf(group.options.values())));
            }
        }
        return result;
    }

    /** Newest metadata wins; retain subjects and their types that exist only in an older serving version. */
    private static DetailChoiceOption mergeChoice(DetailChoiceOption newest, DetailChoiceOption older) {
        Map<String, SubjectRef> subjects = new LinkedHashMap<>();
        newest.subjects().forEach(s -> subjects.putIfAbsent(key(s.code()), s));
        older.subjects().forEach(s -> subjects.putIfAbsent(key(s.code()), s));
        Map<String, SubjectAnswerTypes> types = new LinkedHashMap<>();
        newest.subjectAnswerTypes().forEach(s -> types.putIfAbsent(key(s.subject().code()), s));
        older.subjectAnswerTypes().forEach(s -> types.putIfAbsent(key(s.subject().code()), s));
        return new DetailChoiceOption(newest.kind(), newest.code(), newest.name(), newest.branch(),
                newest.chooseSubjects(), newest.countsByPick(), List.copyOf(subjects.values()),
                newest.answerTypes(), List.copyOf(types.values()));
    }

    /**
     * One option a path stands for, with the answer types of that option alone: the groups the option itself carries, and
     * per subject the groups of that subject's sections. The subjects of one option are alternatives or a pick (HSA
     * science: 3 of 5), so they are never added up (AD-78).
     */
    private static DetailChoiceOption optionOf(PathChoice choice, Option option, Part part,
            Map<String, CatalogItemView> subjectsByCode) {
        List<SubjectRef> subjects = new ArrayList<>();
        Set<String> codes = new LinkedHashSet<>();
        List<AnswerTypeGroup> ownTypes = List.of();
        List<SubjectAnswerTypes> perSubject = new ArrayList<>();
        if (option != null) {
            option.sections().forEach(s -> addCode(codes, s.subjectCode()));
            if (codes.isEmpty() && "SUBJECT".equals(option.kind())) {
                addCode(codes, option.code());
            }
            List<BlockSummary> mine = part.blocks().stream().filter(b -> option.id().equals(b.optionId())).toList();
            Set<String> subjectSectionIds = new LinkedHashSet<>();
            Map<String, List<BlockSummary>> bySubject = new LinkedHashMap<>();
            Map<String, String> subjectCodeOf = new LinkedHashMap<>();
            for (Section section : option.sections()) {
                if (section.subjectCode() == null || section.subjectCode().isBlank()) {
                    continue;
                }
                subjectSectionIds.add(section.id());
                String subjectKey = key(section.subjectCode());
                subjectCodeOf.putIfAbsent(subjectKey, section.subjectCode().trim());
                bySubject.computeIfAbsent(subjectKey, k -> new ArrayList<>());
            }
            for (BlockSummary block : mine) {
                if (block.sectionId() != null && subjectSectionIds.contains(block.sectionId())) {
                    option.sections().stream().filter(s -> block.sectionId().equals(s.id())).findFirst()
                            .ifPresent(s -> bySubject.get(key(s.subjectCode())).add(block));
                }
            }
            ownTypes = answerTypes(mine.stream()
                    .filter(b -> b.sectionId() == null || !subjectSectionIds.contains(b.sectionId())).toList());
            for (Map.Entry<String, List<BlockSummary>> entry : bySubject.entrySet()) {
                List<AnswerTypeGroup> types = answerTypes(entry.getValue());
                if (!types.isEmpty()) {
                    perSubject.add(new SubjectAnswerTypes(subjectRef(subjectCodeOf.get(entry.getKey()), subjectsByCode),
                            types));
                }
            }
        }
        codes.forEach(code -> subjects.add(subjectRef(code, subjectsByCode)));
        return new DetailChoiceOption(choice.kind(), choice.code(), choice.name() == null ? choice.code() : choice.name(),
                null, option == null ? null : option.chooseSubjects(), option == null ? null : option.countsByPick(),
                subjects, ownTypes, perSubject);
    }

    // ------------------------------------------------------------------- ranges

    private static IntRange single(Integer value) {
        return value == null ? null : new IntRange(value, value);
    }

    private static DecimalRange singleDecimal(BigDecimal value) {
        return value == null ? null : new DecimalRange(value, value);
    }

    /** Smallest and largest of the known values; null when none is known. */
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

    // -------------------------------------------------------------------- misc

    private static String key(String code) {
        return code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise storefront model", e);
        }
    }

    /** SHA-256 over the JSON of every field the projection writes (the hash itself blank). */
    private StorefrontRow withHash(StorefrontRow row) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        String hash = HexFormat.of().formatHex(digest.digest(json(row).getBytes(StandardCharsets.UTF_8)));
        return new StorefrontRow(row.examId(), row.code(), row.codeNormalized(), row.versionNo(), row.saleState(),
                row.name(), row.searchText(), row.examItemId(), row.kindCode(), row.scope(), row.scopeItemId(),
                row.subjectItemIds(), row.partCodes(), row.partNames(), row.questionCount(), row.questionCountMax(),
                row.durationMinutes(), row.durationMinutesMax(), row.maxPoints(), row.maxPointsMax(), row.buildMode(),
                row.listPriceVnd(), row.attemptsGranted(), row.coverAssetId(), row.card(), row.detail(), hash,
                row.publishedAt());
    }
}
