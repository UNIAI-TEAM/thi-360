package vn.unicom.thi360.exam.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Thao tác thuần trên {@link ExamStructureDocument}: gán/sinh lại id và khôi phục các trường
 * "thuộc mẫu" khi PUT cấu trúc (spec 5.2: {@code shuffle}, {@code groupOrder},
 * {@code answerTypeLocked}, {@code templateGroupCode} giữ giá trị của mẫu/bản hiện tại).
 */
public final class ExamStructureOps {

    private ExamStructureOps() {
    }

    /** Gán id mới (UUID v7 dạng chuỗi) cho mọi phần tử chưa có id; giữ id đã có. */
    public static ExamStructureDocument assignMissingIds(ExamStructureDocument doc) {
        return mapIds(doc, false);
    }

    /** Sinh lại toàn bộ id — dùng khi nhân bản bài thi. */
    public static ExamStructureDocument regenerateIds(ExamStructureDocument doc) {
        return mapIds(doc, true);
    }

    private static ExamStructureDocument mapIds(ExamStructureDocument doc, boolean regenerate) {
        List<ExamPart> parts = new ArrayList<>();
        for (ExamPart part : doc.parts()) {
            parts.add(mapPart(part, regenerate));
        }
        return new ExamStructureDocument(parts);
    }

    private static String id(String current, boolean regenerate) {
        return (regenerate || current == null || current.isBlank())
                ? UUID.randomUUID().toString() : current;
    }

    private static ExamPart mapPart(ExamPart part, boolean regenerate) {
        if (part == null) {
            return null;
        }
        List<ExamSection> sections = new ArrayList<>();
        for (ExamSection s : part.sections()) {
            sections.add(mapSection(s, regenerate));
        }
        List<ExamPartOption> options = new ArrayList<>();
        for (ExamPartOption o : part.options()) {
            options.add(mapOption(o, regenerate));
        }
        return new ExamPart(id(part.id(), regenerate), part.order(), part.name(),
                part.durationMinutes(), part.questionCount(), part.questionCountOnPaper(),
                part.questionCountRequired(), part.participation(), part.maxPoints(),
                sections, options);
    }

    private static ExamSection mapSection(ExamSection s, boolean regenerate) {
        if (s == null) {
            return null;
        }
        List<QuestionBlock> blocks = new ArrayList<>();
        for (QuestionBlock b : s.blocks()) {
            blocks.add(mapBlock(b, regenerate));
        }
        return new ExamSection(id(s.id(), regenerate), s.subjectCode(), s.questionCount(),
                s.knowledgeScope(), s.answerQuotas(), s.matrix(), s.groupOrder(), s.choiceGroup(),
                blocks);
    }


    private static ExamPartOption mapOption(ExamPartOption o, boolean regenerate) {
        if (o == null) {
            return null;
        }
        List<ExamSection> sections = new ArrayList<>();
        for (ExamSection s : o.sections()) {
            sections.add(mapSection(s, regenerate));
        }
        List<QuestionBlock> blocks = new ArrayList<>();
        for (QuestionBlock b : o.blocks()) {
            blocks.add(mapBlock(b, regenerate));
        }
        return new ExamPartOption(id(o.id(), regenerate), o.kind(), o.code(), o.name(),
                o.chooseSubjects(), o.countsByPick(), sections, blocks);
    }


    private static QuestionBlock mapBlock(QuestionBlock b, boolean regenerate) {
        if (b == null) {
            return null;
        }
        return new QuestionBlock(id(b.id(), regenerate), b.name(), b.questionType(),
                b.questionCount(), b.setCount(), b.setSize(), b.knowledgeScope(), b.matrix(),
                b.taskTypeCode(), b.childAnswerType(), b.templateGroupCode(), b.answerTypeLocked(),
                b.requiredPointsPerQuestion(), b.shuffle());
    }

    /** Restore template-owned fields from the corresponding server-side template nodes. */
    public static ExamStructureDocument restoreTemplateOwned(ExamStructureDocument incoming,
            ExamStructureDocument stored, ExamStructureDocument templateDoc) {
        if (templateDoc != null) {
            return restoreTemplated(incoming, templateDoc);
        }
        Map<String, ExamSection> storedSections = new HashMap<>();
        Map<String, QuestionBlock> storedBlocks = new HashMap<>();
        index(stored, storedSections, storedBlocks);

        List<ExamPart> parts = new ArrayList<>();
        for (ExamPart part : incoming.parts()) {
            if (part == null) {
                parts.add(null);
                continue;
            }
            List<ExamSection> sections = new ArrayList<>();
            for (ExamSection s : part.sections()) {
                sections.add(s == null ? null
                        : restoreSection(s, storedSections, storedBlocks));
            }
            List<ExamPartOption> options = new ArrayList<>();
            for (ExamPartOption o : part.options()) {
                if (o == null) {
                    options.add(null);
                    continue;
                }
                List<ExamSection> oSections = new ArrayList<>();
                for (ExamSection s : o.sections()) {
                    oSections.add(s == null ? null
                            : restoreSection(s, storedSections, storedBlocks));
                }
                List<QuestionBlock> oBlocks = new ArrayList<>();
                for (QuestionBlock b : o.blocks()) {
                    oBlocks.add(b == null ? null : restoreBlock(b, storedBlocks));
                }
                options.add(new ExamPartOption(o.id(), o.kind(), o.code(), o.name(),
                        o.chooseSubjects(), o.countsByPick(), oSections, oBlocks));
            }
            parts.add(new ExamPart(part.id(), part.order(), part.name(), part.durationMinutes(),
                    part.questionCount(), part.questionCountOnPaper(), part.questionCountRequired(),
                    part.participation(), part.maxPoints(), sections, options));
        }
        return new ExamStructureDocument(parts);
    }

    private static void index(ExamStructureDocument doc, Map<String, ExamSection> sections,
            Map<String, QuestionBlock> blocks) {
        if (doc == null) {
            return;
        }
        for (ExamPart part : doc.parts()) {
            if (part == null) {
                continue;
            }
            for (ExamSection s : part.sections()) {
                if (s == null) {
                    continue;
                }
                sections.putIfAbsent(s.id(), s);
                s.blocks().forEach(b -> {
                    if (b != null) {
                        blocks.putIfAbsent(b.id(), b);
                    }
                });
            }
            for (ExamPartOption o : part.options()) {
                if (o == null) {
                    continue;
                }
                for (ExamSection s : o.sections()) {
                    if (s == null) {
                        continue;
                    }
                    sections.putIfAbsent(s.id(), s);
                    s.blocks().forEach(b -> {
                        if (b != null) {
                            blocks.putIfAbsent(b.id(), b);
                        }
                    });
                }
                o.blocks().forEach(b -> {
                    if (b != null) {
                        blocks.putIfAbsent(b.id(), b);
                    }
                });
            }
        }
    }

    private static ExamStructureDocument restoreTemplated(ExamStructureDocument incoming,
            ExamStructureDocument template) {
        List<ExamPart> parts = new ArrayList<>();
        for (int i = 0; i < incoming.parts().size(); i++) {
            ExamPart part = incoming.parts().get(i);
            if (part == null) {
                parts.add(null);
                continue;
            }
            ExamPart source = at(template.parts(), i);
            List<ExamSection> sections = restoreSections(part.sections(),
                    source == null ? List.of() : source.sections());
            List<ExamPartOption> options = new ArrayList<>();
            for (int j = 0; j < part.options().size(); j++) {
                ExamPartOption option = part.options().get(j);
                if (option == null) {
                    options.add(null);
                    continue;
                }
                ExamPartOption match = source == null ? null : at(source.options(), j);
                if (match != null && (match.kind() != option.kind()
                        || !Objects.equals(match.code(), option.code()))) {
                    match = null;
                }
                options.add(new ExamPartOption(option.id(), option.kind(), option.code(), option.name(),
                        option.chooseSubjects(), option.countsByPick(),
                        restoreSections(option.sections(), match == null ? List.of() : match.sections()),
                        restoreBlocks(option.blocks(), match == null ? List.of() : match.blocks())));
            }
            parts.add(new ExamPart(part.id(), part.order(), part.name(), part.durationMinutes(),
                    part.questionCount(), part.questionCountOnPaper(), part.questionCountRequired(),
                    part.participation(), part.maxPoints(), sections, options));
        }
        return new ExamStructureDocument(parts);
    }

    private static List<ExamSection> restoreSections(List<ExamSection> sections,
            List<ExamSection> templateSections) {
        List<ExamSection> result = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            ExamSection section = sections.get(i);
            if (section == null) {
                result.add(null);
                continue;
            }
            ExamSection source = at(templateSections, i);
            if (source != null && !Objects.equals(source.subjectCode(), section.subjectCode())) {
                source = null;
            }
            result.add(new ExamSection(section.id(), section.subjectCode(), section.questionCount(),
                    section.knowledgeScope(), section.answerQuotas(), section.matrix(),
                    source == null ? null : source.groupOrder(), section.choiceGroup(),
                    restoreBlocks(section.blocks(), source == null ? List.of() : source.blocks())));
        }
        return result;
    }

    private static List<QuestionBlock> restoreBlocks(List<QuestionBlock> blocks,
            List<QuestionBlock> templateBlocks) {
        List<QuestionBlock> result = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            QuestionBlock block = blocks.get(i);
            if (block == null) {
                result.add(null);
                continue;
            }
            QuestionBlock source = at(templateBlocks, i);
            result.add(new QuestionBlock(block.id(), block.name(), block.questionType(), block.questionCount(),
                    block.setCount(), block.setSize(), block.knowledgeScope(), block.matrix(),
                    block.taskTypeCode(), block.childAnswerType(),
                    source == null ? null : source.templateGroupCode(),
                    source == null ? false : source.answerTypeLocked(),
                    block.requiredPointsPerQuestion(), source == null ? null : source.shuffle()));
        }
        return result;
    }

    private static <T> T at(List<T> values, int index) {
        return index < values.size() ? values.get(index) : null;
    }


    private static ExamSection restoreSection(ExamSection s, Map<String, ExamSection> storedSections,
            Map<String, QuestionBlock> storedBlocks) {
        ExamSection stored = s.id() == null ? null : storedSections.get(s.id());
        GroupOrder groupOrder = stored != null ? stored.groupOrder() : s.groupOrder();
        List<QuestionBlock> blocks = new ArrayList<>();
        for (QuestionBlock b : s.blocks()) {
            blocks.add(b == null ? null : restoreBlock(b, storedBlocks));
        }
        return new ExamSection(s.id(), s.subjectCode(), s.questionCount(), s.knowledgeScope(),
                s.answerQuotas(), s.matrix(), groupOrder, s.choiceGroup(), blocks);
    }

    private static QuestionBlock restoreBlock(QuestionBlock b, Map<String, QuestionBlock> storedBlocks) {
        if (b == null) {
            return null;
        }
        QuestionBlock stored = b.id() == null ? null : storedBlocks.get(b.id());
        if (stored != null) {
            return new QuestionBlock(b.id(), b.name(), b.questionType(), b.questionCount(),
                    b.setCount(), b.setSize(), b.knowledgeScope(), b.matrix(), b.taskTypeCode(),
                    b.childAnswerType(), stored.templateGroupCode(), stored.answerTypeLocked(),
                    b.requiredPointsPerQuestion(), stored.shuffle());
        }
        return b;
    }
}
