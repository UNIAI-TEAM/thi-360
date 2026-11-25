package vn.unicom.thi360.exam.devdata;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamPartOption;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.OptionKind;
import vn.unicom.thi360.exam.domain.QuestionBlock;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Cuts a template structure down to a "mini" paper for E2E: one question per single-question group, one set per set group,
 * the same parts, sections, groups, options and paths. Distribution constraints (matrices, topic quotas, exam quotas) are
 * dropped, because they describe the full paper. The result is deterministic and idempotent: shrinking a shrunk structure
 * changes nothing, which is what lets the builder resume.
 *
 * <p>All paths of a choice part must carry the same number of questions and points, so a plan may raise a subject's
 * units ({@code sectionUnits}, by subject code) to match the longest path (HSA Tieng Anh against three science subjects).
 */
final class MiniStructure {
    static final int PART_MINUTES = 5;

    private MiniStructure() {
    }

    /** The mini document and its totals. */
    record Result(ExamStructureDocument document, int questionCount, BigDecimal maxPoints, int minutes) { }

    static Result shrink(ExamStructureDocument source, Map<String, Integer> sectionUnits) {
        List<ExamPart> parts = new ArrayList<>();
        int count = 0;
        BigDecimal points = BigDecimal.ZERO;
        int minutes = 0;
        for (ExamPart part : source.parts()) {
            PartResult shrunk = shrinkPart(part, sectionUnits);
            parts.add(shrunk.part);
            count += shrunk.count;
            points = points.add(shrunk.points);
            minutes += shrunk.part.durationMinutes() == null ? 0 : shrunk.part.durationMinutes();
        }
        return new Result(new ExamStructureDocument(parts, List.of()), count, points, minutes);
    }

    private record PartResult(ExamPart part, int count, BigDecimal points) { }

    private record SectionResult(ExamSection section, int units, BigDecimal points) { }

    private record BlocksResult(List<QuestionBlock> blocks, int units, BigDecimal points) { }

    private static PartResult shrinkPart(ExamPart part, Map<String, Integer> plan) {
        List<SectionResult> fixed = sections(part.sections(), plan);
        List<SectionResult> pool = sections(part.subjectPool(), plan);
        int fixedUnits = fixed.stream().mapToInt(SectionResult::units).sum();
        BigDecimal fixedPoints = fixed.stream().map(SectionResult::points).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ExamPartOption> options = new ArrayList<>();
        int pathUnits = -1;
        BigDecimal pathPoints = null;
        int onPaperExtra = 0;
        for (ExamPartOption option : part.options()) {
            List<SectionResult> optionSections = sections(option.sections(), plan);
            BlocksResult optionBlocks = blocks(option.blocks(), null, false);
            int units = optionSections.stream().mapToInt(SectionResult::units).sum() + optionBlocks.units;
            BigDecimal pts = optionSections.stream().map(SectionResult::points).reduce(optionBlocks.points, BigDecimal::add);
            List<Integer> countsByPick = option.countsByPick();
            if (option.kind() == OptionKind.SUBJECT_CHOICE) {
                int picks = option.chooseSubjects() == null ? 3 : option.chooseSubjects();
                int each = optionSections.stream().mapToInt(SectionResult::units).max().orElse(0);
                for (SectionResult section : optionSections) {
                    if (section.units != each) {
                        throw new IllegalStateException("Mini plan: the subjects of choice option " + option.code()
                                + " differ in size; set sectionUnits for the subjects of " + part.name());
                    }
                }
                int max = countsByPick == null ? each : countsByPick.stream().mapToInt(Integer::intValue).max().orElse(each);
                List<Integer> shrunk = new ArrayList<>();
                int drop = 0;
                for (Integer original : countsByPick == null ? List.<Integer>of() : countsByPick) {
                    shrunk.add(each - (max - original));
                    drop += max - original;
                }
                countsByPick = shrunk.isEmpty() ? null : shrunk;
                BigDecimal rate = optionSections.isEmpty() ? BigDecimal.ZERO
                        : optionSections.getFirst().points.divide(BigDecimal.valueOf(Math.max(1, each)), 6,
                                java.math.RoundingMode.HALF_UP);
                units = picks * each - drop;
                pts = optionSections.getFirst().points.multiply(BigDecimal.valueOf(picks))
                        .subtract(rate.multiply(BigDecimal.valueOf(drop)));
                onPaperExtra = drop;
            }
            if (pathUnits >= 0 && (pathUnits != units || pts.compareTo(pathPoints) != 0)) {
                throw new IllegalStateException("Mini plan: the paths of part '" + part.name() + "' differ: " + pathUnits
                        + " questions / " + pathPoints + " points against " + units + " / " + pts + " (option "
                        + option.code() + "); set sectionUnits so that they match");
            }
            pathUnits = units;
            pathPoints = pts;
            options.add(new ExamPartOption(option.id(), option.kind(), option.code(), option.name(),
                    option.chooseSubjects(), countsByPick,
                    optionSections.stream().map(SectionResult::section).toList(), optionBlocks.blocks,
                    option.expertConfirmed()));
        }
        int count = fixedUnits;
        BigDecimal points = fixedPoints;
        if (!options.isEmpty()) {
            count += pathUnits;
            points = points.add(pathPoints);
        }
        if (!pool.isEmpty()) {
            // A code path takes one or more subjects of the pool; the template says how many questions the part has
            // and its first subject says how many of them each subject holds, so the part keeps the same ratio.
            SectionResult first = pool.getFirst();
            int originalFirst = part.subjectPool().getFirst().questionCount() == null ? 1
                    : part.subjectPool().getFirst().questionCount();
            int originalPart = part.questionCount() == null ? originalFirst : part.questionCount();
            int factor = Math.max(1, originalPart / Math.max(1, originalFirst));
            count += first.units * factor;
            points = points.add(first.points.multiply(BigDecimal.valueOf(factor)));
        }
        Integer onPaper = part.questionCountOnPaper();
        Integer required = part.questionCountRequired();
        if (onPaper != null) {
            boolean branches = part.options().stream().anyMatch(o -> o.kind() == OptionKind.BRANCH);
            if (branches) {
                int all = fixedUnits + options.stream().mapToInt(o -> blockUnits(o.blocks())).sum();
                onPaper = all;
            } else {
                onPaper = count + onPaperExtra;
            }
        }
        if (required != null) {
            required = count;
        }
        Integer minutes = part.durationMinutes() == null ? null : Math.min(part.durationMinutes(), PART_MINUTES);
        ExamPart shrunk = new ExamPart(part.id(), part.order(), part.name(), minutes, count, onPaper, required,
                part.participation(), points, fixed.stream().map(SectionResult::section).toList(), options,
                pool.stream().map(SectionResult::section).toList(), part.codeSource(), part.displaySegments());
        return new PartResult(shrunk, count, points);
    }

    private static int blockUnits(List<QuestionBlock> blocks) {
        int units = 0;
        for (QuestionBlock block : blocks) {
            units += fixed(block);
        }
        return units;
    }

    private static List<SectionResult> sections(List<ExamSection> sections, Map<String, Integer> plan) {
        List<SectionResult> result = new ArrayList<>();
        for (ExamSection section : sections) {
            result.add(section(section, plan.get(section.subjectCode())));
        }
        return result;
    }

    private static SectionResult section(ExamSection section, Integer targetUnits) {
        BlocksResult blocks = blocks(section.blocks(), section.questionCount(), true);
        List<QuestionBlock> list = blocks.blocks;
        int units = blocks.units;
        BigDecimal points = blocks.points;
        if (targetUnits != null && targetUnits > units) {
            int extra = targetUnits - units;
            list = new ArrayList<>(list);
            for (int i = 0; i < list.size(); i++) {
                QuestionBlock block = list.get(i);
                if (!Boolean.TRUE.equals(block.omitted()) && !isSet(block) && block.questionCount() != null
                        && block.questionType() == QuestionType.SINGLE_CHOICE) {
                    list.set(i, withCount(block, block.questionCount() + extra));
                    BigDecimal rate = block.requiredPointsPerQuestion() == null ? BigDecimal.ZERO
                            : block.requiredPointsPerQuestion();
                    points = points.add(rate.multiply(BigDecimal.valueOf(extra)));
                    units = targetUnits;
                    break;
                }
            }
            if (units != targetUnits) {
                throw new IllegalStateException("Mini plan: cannot raise subject " + section.subjectCode() + " to "
                        + targetUnits + " questions: no single-choice group to grow");
            }
        }
        ExamSection shrunk = new ExamSection(section.id(), section.subjectCode(), units, section.knowledgeScope(), null,
                null, section.choiceGroup(), list, List.of(), section.audioRequirement(), null, null,
                section.mixBlocks());
        return new SectionResult(shrunk, units, points);
    }

    /** {@code sectionCount} is the section's own count, to size a remainder group; null for option-level blocks. */
    private static BlocksResult blocks(List<QuestionBlock> blocks, Integer sectionCount, boolean allowRemainder) {
        int fixedOriginal = 0;
        for (QuestionBlock block : blocks) {
            if (!Boolean.TRUE.equals(block.omitted()) && (block.questionCount() != null || isSet(block))) {
                fixedOriginal += fixed(block);
            }
        }
        List<QuestionBlock> result = new ArrayList<>();
        int units = 0;
        BigDecimal points = BigDecimal.ZERO;
        for (QuestionBlock block : blocks) {
            if (Boolean.TRUE.equals(block.omitted())) {
                result.add(block);
                continue;
            }
            QuestionBlock shrunk;
            int count;
            if (isSet(block)) {
                int setSize = block.setSize() == null ? 0 : block.setSize();
                int setCount = block.setCount() == null ? 0 : block.setCount();
                if (setCount > 0 && setSize > 0) {
                    shrunk = copy(block, block.questionCount() == null ? null : setSize, 1, setSize,
                            block.slots().isEmpty() ? block.slots() : block.slots().subList(0, 1));
                    count = setSize;
                } else {
                    // A flexible set group: any mix of whole sets that fills the count. The smallest mini is one set.
                    int flexible = block.questionCount() == null ? 0 : Math.min(block.questionCount(), 3);
                    shrunk = copy(block, flexible, block.setCount(), block.setSize(), block.slots());
                    count = flexible;
                }
            } else if (block.questionCount() == null) {
                if (!allowRemainder || sectionCount == null) {
                    shrunk = block;
                    count = 0;
                } else {
                    int remainder = Math.max(0, sectionCount - fixedOriginal);
                    shrunk = copy(block, null, null, null, block.slots());
                    count = Math.min(remainder, 1);
                }
            } else {
                count = Math.min(block.questionCount(), 1);
                shrunk = copy(block, count, block.setCount(), block.setSize(), block.slots());
            }
            result.add(shrunk);
            units += count;
            if (block.requiredPointsPerQuestion() != null) {
                points = points.add(block.requiredPointsPerQuestion().multiply(BigDecimal.valueOf(count)));
            }
        }
        return new BlocksResult(result, units, points);
    }

    private static QuestionBlock withCount(QuestionBlock block, int count) {
        return copy(block, count, block.setCount(), block.setSize(), block.slots());
    }

    private static QuestionBlock copy(QuestionBlock b, Integer questionCount, Integer setCount, Integer setSize,
            List<vn.unicom.thi360.exam.domain.BlockSlot> slots) {
        return new QuestionBlock(b.id(), b.name(), b.questionType(), questionCount, setCount, setSize,
                b.knowledgeScope(), null, b.childAnswerType(), b.templateGroupCode(), b.answerTypeLocked(),
                b.requiredPointsPerQuestion(), b.tagCodes(), b.expectedScoring(), b.shuffleUnits(), b.shuffleEditable(),
                slots, b.childConditions(), b.itemShape(), b.answerFormats(), b.omitted(), List.of(), null);
    }

    private static boolean isSet(QuestionBlock block) {
        return block.questionType() == QuestionType.QUESTION_SET || block.questionType() == QuestionType.CLOZE;
    }

    private static int fixed(QuestionBlock block) {
        if (Boolean.TRUE.equals(block.omitted())) {
            return 0;
        }
        if (block.questionCount() != null) {
            return block.questionCount();
        }
        return block.setCount() == null || block.setSize() == null ? 0 : block.setCount() * block.setSize();
    }
}
