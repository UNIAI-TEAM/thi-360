package vn.unicom.thi360.attempt.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.attempt.api.AttemptPrepareResponse.AfterStartChoiceView;
import vn.unicom.thi360.attempt.api.AttemptPrepareResponse.ChoiceOptionView;
import vn.unicom.thi360.attempt.api.AttemptPrepareResponse.ChoiceSubjectView;
import vn.unicom.thi360.attempt.api.AttemptPrepareResponse.PreChoiceView;
import vn.unicom.thi360.attempt.api.AttemptPrepareResponse.PreparePartView;
import vn.unicom.thi360.attempt.api.ChoiceTiming;
import vn.unicom.thi360.attempt.api.StartAttemptRequest.PreChoiceInput;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.spi.PaperSolverPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Combination;
import vn.unicom.thi360.exam.spi.PublishedExamPort.CombinationSubject;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Option;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Part;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PartPath;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PathChoice;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Section;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingPath;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingPathPart;
import vn.unicom.thi360.shared.api.BusinessRuleException;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.NotFoundException;

/**
 * Resolves published serving paths, purchased option intersection, choice timings, and
 * version/part generation plans across all exam structures (LT §5.1–§5.2, §6.2, E24, E25, F2–F4).
 */
@Component
public class AttemptPathResolver {

    private final PublishedExamPort publishedExamPort;
    private final CatalogLookup catalogLookup;

    public AttemptPathResolver(PublishedExamPort publishedExamPort, CatalogLookup catalogLookup) {
        this.publishedExamPort = publishedExamPort;
        this.catalogLookup = catalogLookup;
    }

    public record PrepareStructure(
            PublishedVersion representativeVersion,
            Integer durationMinutes,
            List<PreparePartView> parts,
            PreChoiceView preChoice,
            List<AfterStartChoiceView> afterStartChoices) {}

    public record PlannedPart(
            String partCode,
            String partName,
            int ord,
            ChoiceTiming choiceTiming,
            Integer durationMinutes,
            Integer questionCount,
            BigDecimal maxPoints) {}

    public record ResolvedStartPlan(
            PublishedVersion version,
            ServingPath chosenPath,
            Integer examDurationMinutes,
            BigDecimal totalMaxScore,
            Map<String, Object> choices,
            List<String> partCodesToGenerate,
            List<PaperSolverPort.Selection> selections,
            List<PlannedPart> plannedParts,
            String pathDescription) {}

    public PrepareStructure buildPrepareStructure(
            ServingExam serving, List<String> grantedOptionCodes) {
        return buildPrepareStructure(serving, grantedOptionCodes, true);
    }

    public PrepareStructure buildPrepareStructure(
            ServingExam serving, List<String> grantedOptionCodes, boolean hasAvailableGrant) {
        PublishedVersion baseVersion = serving.version();
        List<ServingPath> allowedPaths = hasAvailableGrant
                ? intersectServingPaths(serving.servingPaths(), grantedOptionCodes)
                : List.of();
        Map<Integer, PublishedVersion> versionsByNo = loadAllowedVersions(serving, allowedPaths);
        PublishedVersion representative = allowedPaths.isEmpty()
                ? baseVersion
                : versionsByNo.getOrDefault(allowedPaths.getFirst().versionNo(), baseVersion);
        Map<String, CatalogItemView> subjectsByCode = subjectsByCode();

        List<Part> orderedParts = sortedParts(representative.parts());
        List<PreparePartView> partViews = new ArrayList<>(orderedParts.size());
        PreChoiceView preChoice = null;
        List<AfterStartChoiceView> afterStartChoices = new ArrayList<>();

        for (int ord = 0; ord < orderedParts.size(); ord++) {
            Part part = orderedParts.get(ord);
            ChoiceTiming timing = classifyPart(representative, part, allowedPaths, versionsByNo);
            boolean required = timing == ChoiceTiming.NONE;

            PartMetrics metrics = resolvePrepareMetrics(part, allowedPaths);
            partViews.add(
                    new PreparePartView(
                            part.id(),
                            ord,
                            part.name(),
                            required,
                            timing,
                            metrics.durationMinutes(),
                            metrics.questionCount(),
                            metrics.maxPoints()));

            if (timing == ChoiceTiming.PRE_START && preChoice == null) {
                List<ChoiceOptionView> options =
                        buildAllowedOptionsForPart(representative, part, allowedPaths, versionsByNo, subjectsByCode);
                String kind = resolveChoiceKind(options, part, representative);
                String preselectedCode = options.size() == 1 ? options.getFirst().code() : null;
                preChoice = new PreChoiceView(part.id(), kind, preselectedCode, options);
            } else if (timing == ChoiceTiming.AFTER_START) {
                List<ChoiceOptionView> options =
                        buildAllowedOptionsForPart(representative, part, allowedPaths, versionsByNo, subjectsByCode);
                String kind = resolveChoiceKind(options, part, representative);
                afterStartChoices.add(new AfterStartChoiceView(part.id(), ord, part.name(), kind, options));
            }
        }

        Integer durationMinutes = resolvePrepareDuration(representative, allowedPaths, partViews);
        return new PrepareStructure(representative, durationMinutes, partViews, preChoice, afterStartChoices);
    }

    public ResolvedStartPlan resolveStartPlan(
            ServingExam serving, List<String> grantedOptionCodes, PreChoiceInput preChoice) {
        PublishedVersion baseVersion = serving.version();
        List<ServingPath> allowedPaths = intersectServingPaths(serving.servingPaths(), grantedOptionCodes);
        boolean examHasChoices = hasAnyChoicePart(baseVersion, serving.servingPaths());
        if (allowedPaths.isEmpty()) {
            throw new ConflictException("NO_SERVING_OPTION", null);
        }

        Map<Integer, PublishedVersion> versionsByNo = loadAllowedVersions(serving, allowedPaths);
        PublishedVersion representative =
                versionsByNo.getOrDefault(allowedPaths.getFirst().versionNo(), baseVersion);
        Part preChoicePart = findPreChoicePart(representative, allowedPaths, versionsByNo);

        ServingPath chosenPath;
        PublishedVersion chosenVersion;
        List<PaperSolverPort.Selection> selections = new ArrayList<>();
        Map<String, Object> choicesMap = new LinkedHashMap<>();

        if (preChoicePart != null) {
            if (preChoice == null || isBlank(preChoice.optionCode())) {
                throw new BusinessRuleException("CHOICE_NOT_ALLOWED", null);
            }
            String requestedCode = normalizeCode(preChoice.optionCode());
            chosenPath = findPathForChoice(allowedPaths, preChoicePart.id(), requestedCode)
                    .orElseThrow(() -> new BusinessRuleException("CHOICE_NOT_ALLOWED", null));
            chosenVersion = versionsByNo.get(chosenPath.versionNo());
            Part chosenPart = findPartById(chosenVersion, preChoicePart.id())
                    .orElseThrow(() -> new BusinessRuleException("CHOICE_NOT_ALLOWED", null));

            Optional<Option> optionMatch = findOptionByCode(chosenPart, requestedCode);
            if (optionMatch.isPresent()) {
                Option option = optionMatch.get();
                if ("SUBJECT_CHOICE".equals(option.kind())) {
                    List<String> selectedSubjects =
                            validateAndCanonicalizeSubjects(option, preChoice.subjectCodes());
                    selections.add(
                            PaperSolverPort.Selection.subjects(
                                    chosenPart.id(), option.code(), selectedSubjects));
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("optionCode", option.code());
                    entry.put("subjectCodes", selectedSubjects);
                    choicesMap.put(chosenPart.id(), entry);
                } else {
                    rejectUnexpectedSubjects(preChoice.subjectCodes());
                    selections.add(PaperSolverPort.Selection.option(chosenPart.id(), option.code()));
                    choicesMap.put(chosenPart.id(), Map.of("optionCode", option.code()));
                }
            } else {
                Combination combination = findCombinationByCode(chosenVersion, chosenPart.id(), requestedCode)
                        .orElseThrow(() -> new BusinessRuleException("CHOICE_NOT_ALLOWED", null));
                rejectUnexpectedSubjects(preChoice.subjectCodes());
                selections.add(PaperSolverPort.Selection.option(chosenPart.id(), combination.code()));
                choicesMap.put(chosenPart.id(), Map.of("optionCode", combination.code()));
            }
        } else {
            if (hasNonEmptyPreChoice(preChoice)) {
                throw new BusinessRuleException("CHOICE_NOT_ALLOWED", null);
            }
            if (examHasChoices && allowedPaths.isEmpty()) {
                throw new ConflictException("NO_SERVING_OPTION", null);
            }
            chosenPath = allowedPaths.getFirst();
            chosenVersion = versionsByNo.get(chosenPath.versionNo());
        }

        List<Part> orderedParts = sortedParts(chosenVersion.parts());
        List<String> partCodesToGenerate = new ArrayList<>();
        List<PlannedPart> plannedParts = new ArrayList<>(orderedParts.size());
        BigDecimal totalMax = BigDecimal.ZERO;
        int partDurationSum = 0;
        boolean hasAnyPartDuration = false;

        for (int ord = 0; ord < orderedParts.size(); ord++) {
            Part part = orderedParts.get(ord);
            ChoiceTiming timing = classifyPart(chosenVersion, part, List.of(chosenPath), Map.of(chosenVersion.versionNo(), chosenVersion));
            PartMetrics metrics = resolvePartMetricsForPath(part, chosenPath);
            if (timing != ChoiceTiming.AFTER_START) {
                partCodesToGenerate.add(part.id());
            }
            if (metrics.maxPoints() != null) {
                totalMax = totalMax.add(metrics.maxPoints());
            }
            if (metrics.durationMinutes() != null) {
                partDurationSum += metrics.durationMinutes();
                hasAnyPartDuration = true;
            }
            plannedParts.add(
                    new PlannedPart(
                            part.id(),
                            part.name(),
                            ord,
                            timing,
                            metrics.durationMinutes(),
                            metrics.questionCount(),
                            metrics.maxPoints()));
        }

        boolean allPartsHaveMax = !plannedParts.isEmpty()
                && plannedParts.stream().allMatch(p -> p.maxPoints() != null);
        BigDecimal resolvedMaxScore =
                allPartsHaveMax
                        ? totalMax
                        : (chosenPath.maxPoints() != null ? chosenPath.maxPoints() : chosenVersion.maxScore());
        Integer examDurationMinutes =
                chosenVersion.durationMinutes() != null
                        ? chosenVersion.durationMinutes()
                        : (chosenPath.durationMinutes() != null
                                ? chosenPath.durationMinutes()
                                : (hasAnyPartDuration ? partDurationSum : null));

        return new ResolvedStartPlan(
                chosenVersion,
                chosenPath,
                examDurationMinutes,
                resolvedMaxScore,
                choicesMap,
                List.copyOf(partCodesToGenerate),
                List.copyOf(selections),
                List.copyOf(plannedParts),
                chosenPath.pathId());
    }

    private Map<Integer, PublishedVersion> loadAllowedVersions(
            ServingExam serving, List<ServingPath> paths) {
        Map<Integer, PublishedVersion> byVersion = new HashMap<>();
        PublishedVersion base = serving.version();
        byVersion.put(base.versionNo(), base);
        for (ServingPath path : paths) {
            byVersion.computeIfAbsent(
                    path.versionNo(),
                    vNo -> publishedExamPort
                            .loadVersion(serving.examId(), vNo)
                            .orElseThrow(() -> new NotFoundException("VERSION_NOT_FOUND", null)));
        }
        return byVersion;
    }

    private List<ServingPath> intersectServingPaths(
            List<ServingPath> servingPaths, List<String> grantedOptionCodes) {
        if (servingPaths == null || servingPaths.isEmpty()) {
            return List.of();
        }
        if (grantedOptionCodes == null || grantedOptionCodes.isEmpty()) {
            return List.copyOf(servingPaths);
        }
        Set<String> granted = new LinkedHashSet<>();
        for (String code : grantedOptionCodes) {
            String normalized = normalizeCode(code);
            if (normalized != null) {
                granted.add(normalized);
            }
        }
        if (granted.isEmpty()) {
            return List.of();
        }
        List<ServingPath> matched = new ArrayList<>();
        for (ServingPath path : servingPaths) {
            if (path.choices() == null || path.choices().isEmpty()) {
                matched.add(path);
                continue;
            }
            boolean allGranted = path.choices().stream()
                    .map(PathChoice::code)
                    .map(AttemptPathResolver::normalizeCode)
                    .allMatch(c -> c != null && granted.contains(c));
            if (allGranted) {
                matched.add(path);
            }
        }
        return List.copyOf(matched);
    }

    private static boolean hasAnyChoicePart(PublishedVersion baseVersion, List<ServingPath> allPaths) {
        for (ServingPath p : allPaths) {
            if (p.choices() != null && !p.choices().isEmpty()) {
                return true;
            }
        }
        for (Part part : baseVersion.parts()) {
            if ((part.options() != null && !part.options().isEmpty())
                    || baseVersion.combinations().stream().anyMatch(c -> Objects.equals(part.id(), c.partId()))) {
                return true;
            }
        }
        return false;
    }

    private static Part findPreChoicePart(
            PublishedVersion baseVersion,
            List<ServingPath> allowedPaths,
            Map<Integer, PublishedVersion> versionsByNo) {
        for (Part part : sortedParts(baseVersion.parts())) {
            if (classifyPart(baseVersion, part, allowedPaths, versionsByNo) == ChoiceTiming.PRE_START) {
                return part;
            }
        }
        return null;
    }

    public static boolean isAfterStartChoicePart(PublishedVersion version, String partCode) {
        if (version == null || partCode == null) {
            return false;
        }
        return findPartById(version, partCode)
                .map(
                        part ->
                                classifyPart(
                                                version,
                                                part,
                                                List.of(),
                                                Map.of(version.versionNo(), version))
                                        == ChoiceTiming.AFTER_START)
                .orElse(false);
    }

    static ChoiceTiming classifyPart(
            PublishedVersion baseVersion,
            Part part,
            List<ServingPath> allowedPaths,
            Map<Integer, PublishedVersion> versionsByNo) {
        boolean partHasChoices = hasChoicesInPartOrPaths(baseVersion, part, allowedPaths, versionsByNo);
        if (!partHasChoices) {
            return ChoiceTiming.NONE;
        }
        boolean isAfterStart =
                "FULL".equals(baseVersion.scope())
                        && ("HSA".equals(baseVersion.kindCode()) || "QDA".equals(baseVersion.kindCode()));
        return isAfterStart ? ChoiceTiming.AFTER_START : ChoiceTiming.PRE_START;
    }

    private static boolean hasChoicesInPartOrPaths(
            PublishedVersion baseVersion,
            Part part,
            List<ServingPath> allowedPaths,
            Map<Integer, PublishedVersion> versionsByNo) {
        if (part.options() != null && !part.options().isEmpty()) {
            return true;
        }
        if (baseVersion.combinations() != null
                && baseVersion.combinations().stream().anyMatch(c -> Objects.equals(part.id(), c.partId()))) {
            return true;
        }
        for (ServingPath path : allowedPaths) {
            if (path.choices() != null
                    && path.choices().stream().anyMatch(c -> Objects.equals(part.id(), c.partId()))) {
                return true;
            }
            PublishedVersion pv = versionsByNo.get(path.versionNo());
            if (pv != null) {
                if (pv.combinations() != null
                        && pv.combinations().stream().anyMatch(c -> Objects.equals(part.id(), c.partId()))) {
                    return true;
                }
                Optional<Part> pvPart = findPartById(pv, part.id());
                if (pvPart.isPresent()
                        && pvPart.get().options() != null
                        && !pvPart.get().options().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<ChoiceOptionView> buildAllowedOptionsForPart(
            PublishedVersion baseVersion,
            Part basePart,
            List<ServingPath> allowedPaths,
            Map<Integer, PublishedVersion> versionsByNo,
            Map<String, CatalogItemView> subjectsByCode) {
        Map<String, List<ServingPath>> allowedPathsByCode = new LinkedHashMap<>();
        for (ServingPath path : allowedPaths) {
            if (path.choices() == null) {
                continue;
            }
            Set<String> seenCodesInPath = new LinkedHashSet<>();
            for (PathChoice choice : path.choices()) {
                if (Objects.equals(basePart.id(), choice.partId())) {
                    String norm = normalizeCode(choice.code());
                    if (norm != null && seenCodesInPath.add(norm)) {
                        allowedPathsByCode
                                .computeIfAbsent(norm, k -> new ArrayList<>())
                                .add(path);
                    }
                }
            }
        }

        List<ChoiceOptionView> views = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        // Option-based parts (HSA P3 SCIENCE/ANH, HSA PART SCIENCE, SPT/THPTQG CS/ICT)
        collectOptionsFromVersionPart(
                basePart, allowedPathsByCode, versionsByNo, subjectsByCode, views, seen);
        for (ServingPath path : allowedPaths) {
            PublishedVersion pv = versionsByNo.get(path.versionNo());
            if (pv != null && pv.versionNo() != baseVersion.versionNo()) {
                findPartById(pv, basePart.id())
                        .ifPresent(
                                p ->
                                        collectOptionsFromVersionPart(
                                                p,
                                                allowedPathsByCode,
                                                versionsByNo,
                                                subjectsByCode,
                                                views,
                                                seen));
            }
        }
        if (!views.isEmpty()) {
            return List.copyOf(views);
        }

        // Combination-based parts (QDA FULL/PART, BCA FULL/PART)
        collectCombinationsFromVersion(
                baseVersion,
                basePart.id(),
                allowedPathsByCode,
                versionsByNo,
                subjectsByCode,
                views,
                seen);
        for (ServingPath path : allowedPaths) {
            PublishedVersion pv = versionsByNo.get(path.versionNo());
            if (pv != null && pv.versionNo() != baseVersion.versionNo()) {
                collectCombinationsFromVersion(
                        pv,
                        basePart.id(),
                        allowedPathsByCode,
                        versionsByNo,
                        subjectsByCode,
                        views,
                        seen);
            }
        }
        return List.copyOf(views);
    }

    private void collectOptionsFromVersionPart(
            Part part,
            Map<String, List<ServingPath>> allowedPathsByCode,
            Map<Integer, PublishedVersion> versionsByNo,
            Map<String, CatalogItemView> subjectsByCode,
            List<ChoiceOptionView> views,
            Set<String> seen) {
        if (part.options() == null) {
            return;
        }
        for (Option option : part.options()) {
            String norm = normalizeCode(option.code());
            List<ServingPath> matchingPaths = norm == null ? null : allowedPathsByCode.get(norm);
            if (matchingPaths == null || matchingPaths.isEmpty() || !seen.add(norm)) {
                continue;
            }
            List<ChoiceSubjectView> subjects = new ArrayList<>();
            Set<String> seenSubjects = new LinkedHashSet<>();
            if (option.sections() != null) {
                for (Section section : option.sections()) {
                    String subjNorm = normalizeCode(section.subjectCode());
                    if (subjNorm != null && seenSubjects.add(subjNorm)) {
                        subjects.add(toSubjectView(section.subjectCode(), null, subjectsByCode));
                    }
                }
            }
            OptionMetrics metrics = resolveOptionPathMetrics(matchingPaths, versionsByNo);
            views.add(
                    new ChoiceOptionView(
                            option.code(),
                            option.name(),
                            option.kind(),
                            option.chooseSubjects(),
                            option.countsByPick(),
                            subjects,
                            metrics.questionCount(),
                            metrics.durationMinutes(),
                            metrics.maxPoints()));
        }
    }

    private void collectCombinationsFromVersion(
            PublishedVersion version,
            String partId,
            Map<String, List<ServingPath>> allowedPathsByCode,
            Map<Integer, PublishedVersion> versionsByNo,
            Map<String, CatalogItemView> subjectsByCode,
            List<ChoiceOptionView> views,
            Set<String> seen) {
        if (version.combinations() == null) {
            return;
        }
        for (Combination comb : version.combinations()) {
            if (!Objects.equals(partId, comb.partId())) {
                continue;
            }
            String norm = normalizeCode(comb.code());
            List<ServingPath> matchingPaths = norm == null ? null : allowedPathsByCode.get(norm);
            if (matchingPaths == null || matchingPaths.isEmpty() || !seen.add(norm)) {
                continue;
            }
            List<ChoiceSubjectView> subjects = new ArrayList<>();
            if (comb.subjects() != null) {
                comb.subjects().stream()
                        .sorted(Comparator.comparingInt(CombinationSubject::position))
                        .forEach(cs -> subjects.add(toSubjectView(cs.code(), cs.name(), subjectsByCode)));
            }
            OptionMetrics metrics = resolveOptionPathMetrics(matchingPaths, versionsByNo);
            views.add(
                    new ChoiceOptionView(
                            comb.code(),
                            comb.name(),
                            "COMBINATION",
                            null,
                            List.of(),
                            subjects,
                            metrics.questionCount(),
                            metrics.durationMinutes(),
                            metrics.maxPoints()));
        }
    }

    private record OptionMetrics(
            Integer questionCount, Integer durationMinutes, BigDecimal maxPoints) {}

    private static OptionMetrics resolveOptionPathMetrics(
            List<ServingPath> paths, Map<Integer, PublishedVersion> versionsByNo) {
        if (paths == null || paths.isEmpty()) {
            return new OptionMetrics(null, null, null);
        }
        boolean qCountConsistent = true;
        Integer consensusQCount = null;
        boolean durationConsistent = true;
        Integer consensusDuration = null;
        boolean pointsConsistent = true;
        BigDecimal consensusPoints = null;

        for (int i = 0; i < paths.size(); i++) {
            ServingPath path = paths.get(i);
            PublishedVersion version =
                    versionsByNo == null ? null : versionsByNo.get(path.versionNo());

            Integer qCount = resolvePathQuestionCount(path);
            if (qCount == null) {
                qCountConsistent = false;
            } else if (i == 0) {
                consensusQCount = qCount;
            } else if (qCountConsistent && !Objects.equals(consensusQCount, qCount)) {
                qCountConsistent = false;
            }

            Integer duration = resolvePathDurationMinutes(path, version);
            if (duration == null) {
                durationConsistent = false;
            } else if (i == 0) {
                consensusDuration = duration;
            } else if (durationConsistent && !Objects.equals(consensusDuration, duration)) {
                durationConsistent = false;
            }

            BigDecimal maxPoints = resolvePathMaxPoints(path, version);
            if (maxPoints == null) {
                pointsConsistent = false;
            } else if (i == 0) {
                consensusPoints = maxPoints;
            } else if (pointsConsistent && consensusPoints.compareTo(maxPoints) != 0) {
                pointsConsistent = false;
            }

            if (!qCountConsistent && !durationConsistent && !pointsConsistent) {
                return new OptionMetrics(null, null, null);
            }
        }

        return new OptionMetrics(
                qCountConsistent ? consensusQCount : null,
                durationConsistent ? consensusDuration : null,
                pointsConsistent ? consensusPoints : null);
    }

    private static Integer resolvePathQuestionCount(ServingPath path) {
        if (path.questionCount() != null) {
            return path.questionCount();
        }
        if (path.parts() == null || path.parts().isEmpty()) {
            return null;
        }
        int sum = 0;
        for (ServingPathPart part : path.parts()) {
            if (part.questionCount() == null) {
                return null;
            }
            sum += part.questionCount();
        }
        return sum;
    }

    private static Integer resolvePathDurationMinutes(ServingPath path, PublishedVersion version) {
        if (path.durationMinutes() != null) {
            return path.durationMinutes();
        }
        if (version != null && version.durationMinutes() != null) {
            return version.durationMinutes();
        }
        if (path.parts() == null || path.parts().isEmpty()) {
            return null;
        }
        int sum = 0;
        for (ServingPathPart part : path.parts()) {
            if (part.durationMinutes() == null) {
                return null;
            }
            sum += part.durationMinutes();
        }
        return sum;
    }

    private static BigDecimal resolvePathMaxPoints(ServingPath path, PublishedVersion version) {
        if (path.maxPoints() != null) {
            return path.maxPoints();
        }
        if (path.parts() != null && !path.parts().isEmpty()) {
            BigDecimal sum = BigDecimal.ZERO;
            boolean allPartsHaveMax = true;
            for (ServingPathPart part : path.parts()) {
                if (part.maxPoints() == null) {
                    allPartsHaveMax = false;
                    break;
                }
                sum = sum.add(part.maxPoints());
            }
            if (allPartsHaveMax) {
                return sum;
            }
        }
        return version != null ? version.maxScore() : null;
    }

    private static ChoiceSubjectView toSubjectView(
            String rawCode, String fallbackNameVi, Map<String, CatalogItemView> subjectsByCode) {
        String norm = normalizeCode(rawCode);
        CatalogItemView item = norm == null ? null : subjectsByCode.get(norm);
        if (item != null) {
            return new ChoiceSubjectView(rawCode, item.nameVi(), item.nameEn());
        }
        return new ChoiceSubjectView(rawCode, fallbackNameVi, null);
    }

    private Map<String, CatalogItemView> subjectsByCode() {
        Map<String, CatalogItemView> map = new HashMap<>();
        for (CatalogItemView item : catalogLookup.listGroupItems("SUBJECT")) {
            String key = normalizeCode(item.code());
            if (key != null) {
                map.putIfAbsent(key, item);
            }
        }
        return map;
    }

    private static String resolveChoiceKind(
            List<ChoiceOptionView> options, Part part, PublishedVersion version) {
        if (!options.isEmpty() && options.getFirst().kind() != null) {
            return options.getFirst().kind();
        }
        if (part.options() != null && !part.options().isEmpty() && part.options().getFirst().kind() != null) {
            return part.options().getFirst().kind();
        }
        if (version.combinations() != null
                && version.combinations().stream().anyMatch(c -> Objects.equals(part.id(), c.partId()))) {
            return "COMBINATION";
        }
        return null;
    }

    private record PartMetrics(Integer questionCount, BigDecimal maxPoints, Integer durationMinutes) {}

    private static PartMetrics resolvePrepareMetrics(Part part, List<ServingPath> allowedPaths) {
        for (ServingPath sp : allowedPaths) {
            ServingPathPart match = findServingPathPart(sp, part.id());
            if (match != null) {
                Integer qCount = match.questionCount();
                BigDecimal maxPts = match.maxPoints();
                Integer mins = match.durationMinutes() != null ? match.durationMinutes() : part.durationMinutes();
                return new PartMetrics(qCount, maxPts, mins);
            }
        }
        if (part.paths() != null && !part.paths().isEmpty()) {
            PartPath pp = part.paths().getFirst();
            Integer mins = pp.durationMinutes() != null ? pp.durationMinutes() : part.durationMinutes();
            return new PartMetrics(pp.questionCount(), pp.maxPoints(), mins);
        }
        Integer qCount =
                part.questionCountRequired() != null
                        ? part.questionCountRequired()
                        : (part.questionCountOnPaper() != null ? part.questionCountOnPaper() : part.questionCount());
        return new PartMetrics(qCount, part.maxPoints(), part.durationMinutes());
    }

    private static PartMetrics resolvePartMetricsForPath(Part part, ServingPath chosenPath) {
        ServingPathPart spp = findServingPathPart(chosenPath, part.id());
        if (spp != null) {
            Integer mins = spp.durationMinutes() != null ? spp.durationMinutes() : part.durationMinutes();
            return new PartMetrics(spp.questionCount(), spp.maxPoints(), mins);
        }
        if (part.paths() != null) {
            for (PartPath pp : part.paths()) {
                if (Objects.equals(pp.pathId(), chosenPath.pathId())) {
                    Integer mins = pp.durationMinutes() != null ? pp.durationMinutes() : part.durationMinutes();
                    return new PartMetrics(pp.questionCount(), pp.maxPoints(), mins);
                }
            }
            if (!part.paths().isEmpty()) {
                PartPath pp = part.paths().getFirst();
                Integer mins = pp.durationMinutes() != null ? pp.durationMinutes() : part.durationMinutes();
                return new PartMetrics(pp.questionCount(), pp.maxPoints(), mins);
            }
        }
        Integer qCount =
                part.questionCountRequired() != null
                        ? part.questionCountRequired()
                        : (part.questionCountOnPaper() != null ? part.questionCountOnPaper() : part.questionCount());
        return new PartMetrics(qCount, part.maxPoints(), part.durationMinutes());
    }

    private static ServingPathPart findServingPathPart(ServingPath path, String partId) {
        if (path.parts() == null) {
            return null;
        }
        for (ServingPathPart spp : path.parts()) {
            if (Objects.equals(partId, spp.partId())) {
                return spp;
            }
        }
        return null;
    }

    private static Integer resolvePrepareDuration(
            PublishedVersion version, List<ServingPath> allowedPaths, List<PreparePartView> partViews) {
        if (version.durationMinutes() != null) {
            return version.durationMinutes();
        }
        if (!allowedPaths.isEmpty() && allowedPaths.getFirst().durationMinutes() != null) {
            return allowedPaths.getFirst().durationMinutes();
        }
        int sum = 0;
        boolean any = false;
        for (PreparePartView pv : partViews) {
            if (pv.durationMinutes() != null) {
                sum += pv.durationMinutes();
                any = true;
            }
        }
        return any ? sum : null;
    }

    private static Optional<ServingPath> findPathForChoice(
            List<ServingPath> allowedPaths, String partId, String requestedNormalizedCode) {
        for (ServingPath path : allowedPaths) {
            if (path.choices() == null) {
                continue;
            }
            for (PathChoice choice : path.choices()) {
                if (Objects.equals(partId, choice.partId())
                        && Objects.equals(requestedNormalizedCode, normalizeCode(choice.code()))) {
                    return Optional.of(path);
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Part> findPartById(PublishedVersion version, String partId) {
        return version.parts().stream().filter(p -> Objects.equals(partId, p.id())).findFirst();
    }

    private static Optional<Option> findOptionByCode(Part part, String requestedNormalizedCode) {
        if (part.options() == null) {
            return Optional.empty();
        }
        return part.options().stream()
                .filter(o -> Objects.equals(requestedNormalizedCode, normalizeCode(o.code())))
                .findFirst();
    }

    private static Optional<Combination> findCombinationByCode(
            PublishedVersion version, String partId, String requestedNormalizedCode) {
        if (version.combinations() == null) {
            return Optional.empty();
        }
        return version.combinations().stream()
                .filter(
                        c ->
                                Objects.equals(partId, c.partId())
                                        && Objects.equals(requestedNormalizedCode, normalizeCode(c.code())))
                .findFirst();
    }

    private static List<String> validateAndCanonicalizeSubjects(
            Option option, List<String> rawSubjectCodes) {
        int expected = option.chooseSubjects() == null ? 0 : option.chooseSubjects();
        if (rawSubjectCodes == null || rawSubjectCodes.size() != expected || expected <= 0) {
            throw new BusinessRuleException("SUBJECTS_INVALID", null);
        }
        Map<String, String> canonicalByNorm = new LinkedHashMap<>();
        if (option.sections() != null) {
            for (Section s : option.sections()) {
                String norm = normalizeCode(s.subjectCode());
                if (norm != null) {
                    canonicalByNorm.putIfAbsent(norm, s.subjectCode());
                }
            }
        }
        List<String> result = new ArrayList<>(expected);
        Set<String> seen = new LinkedHashSet<>();
        for (String raw : rawSubjectCodes) {
            String norm = normalizeCode(raw);
            if (norm == null || !canonicalByNorm.containsKey(norm) || !seen.add(norm)) {
                throw new BusinessRuleException("SUBJECTS_INVALID", null);
            }
            result.add(canonicalByNorm.get(norm));
        }
        return List.copyOf(result);
    }

    private static void rejectUnexpectedSubjects(List<String> subjectCodes) {
        if (subjectCodes != null && !subjectCodes.isEmpty()) {
            throw new BusinessRuleException("SUBJECTS_INVALID", null);
        }
    }

    private static boolean hasNonEmptyPreChoice(PreChoiceInput preChoice) {
        if (preChoice == null) {
            return false;
        }
        if (!isBlank(preChoice.optionCode())) {
            return true;
        }
        return preChoice.subjectCodes() != null && !preChoice.subjectCodes().isEmpty();
    }

    private static List<Part> sortedParts(List<Part> parts) {
        if (parts == null || parts.isEmpty()) {
            return List.of();
        }
        return parts.stream()
                .sorted(Comparator.comparing(Part::order, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private static String normalizeCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isBlank(String raw) {
        return raw == null || raw.isBlank();
    }
}
