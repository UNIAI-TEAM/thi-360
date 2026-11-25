package vn.unicom.thi360.exam.application;

import java.util.ArrayList;
import java.util.List;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamPartOption;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.PositionRange;
import vn.unicom.thi360.exam.domain.QuestionBlock;

/** Adds response-only question ranges without changing the stored structure document. */
final class ExamStructurePresentation {
    private ExamStructurePresentation() {
    }

    static ExamStructureDocument withPositionRanges(ExamStructureDocument document) {
        List<ExamPart> parts = new ArrayList<>();
        for (ExamPart part : document.parts()) {
            parts.add(withPositionRanges(part));
        }
        return new ExamStructureDocument(parts, document.examQuotas());
    }

    private static ExamPart withPositionRanges(ExamPart part) {
        if (part == null) {
            return null;
        }
        Counter counter = new Counter();
        List<ExamSection> sections = part.sections().stream().map(section -> withPositionRanges(section, counter)).toList();
        List<ExamSection> subjectPool = part.subjectPool().stream()
                .map(section -> withPositionRanges(section, new Counter(counter.next))).toList();
        List<ExamPartOption> options = part.options().stream()
                .map(option -> withPositionRanges(option, new Counter(counter.next))).toList();
        return new ExamPart(part.id(), part.order(), part.name(), part.durationMinutes(), part.questionCount(),
                part.questionCountOnPaper(), part.questionCountRequired(), part.participation(), part.maxPoints(),
                sections, options, subjectPool, part.codeSource(), part.displaySegments());
    }

    private static ExamPartOption withPositionRanges(ExamPartOption option, Counter counter) {
        if (option == null) {
            return null;
        }
        List<ExamSection> sections = option.sections().stream()
                .map(section -> withPositionRanges(section, counter)).toList();
        List<QuestionBlock> blocks = option.blocks().stream()
                .map(block -> withPositionRange(block, counter, false)).toList();
        return new ExamPartOption(option.id(), option.kind(), option.code(), option.name(), option.chooseSubjects(),
                option.countsByPick(), sections, blocks, option.expertConfirmed());
    }

    private static ExamSection withPositionRanges(ExamSection section, Counter counter) {
        if (section == null) {
            return null;
        }
        boolean omitRange = section.isMixBlocks();
        List<QuestionBlock> blocks = section.blocks().stream()
                .map(block -> withPositionRange(block, counter, omitRange)).toList();
        return new ExamSection(section.id(), section.subjectCode(), section.questionCount(), section.knowledgeScope(),
                section.answerQuotas(), section.matrix(), section.choiceGroup(), blocks, section.topicQuotas(),
                section.audioRequirement(), section.mapidMode(), section.mapidRules(), section.mixBlocks());
    }

    private static QuestionBlock withPositionRange(QuestionBlock block, Counter counter, boolean omitRange) {
        if (block == null || Boolean.TRUE.equals(block.omitted())) {
            return block;
        }
        int count = block.questionCount() != null ? block.questionCount()
                : block.setCount() != null && block.setSize() != null
                        ? Math.multiplyExact(block.setCount(), block.setSize()) : 0;
        PositionRange range = count <= 0 ? null : counter.take(count);
        return new QuestionBlock(block.id(), block.name(), block.questionType(), block.questionCount(), block.setCount(),
                block.setSize(), block.knowledgeScope(), block.matrix(), block.childAnswerType(), block.templateGroupCode(),
                block.answerTypeLocked(), block.requiredPointsPerQuestion(), block.tagCodes(), block.expectedScoring(),
                block.shuffleUnits(), block.shuffleEditable(), block.slots(), block.childConditions(), block.itemShape(),
                block.answerFormats(), block.omitted(), block.topicQuotas(), omitRange ? null : range);
    }

    private static final class Counter {
        private int next;

        Counter() {
            this(1);
        }

        Counter(int next) {
            this.next = next;
        }

        PositionRange take(int count) {
            int from = next;
            next = Math.addExact(next, count);
            return new PositionRange(from, next - 1);
        }
    }
}
