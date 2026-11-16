package vn.unicom.thi360.exam.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Khôi phục các trường thuộc mẫu theo định danh nút: nút giữ được id của bản lưu thì nhận
     * lại đúng giá trị mẫu của chính nó (bản lưu đã mang nguồn mẫu), kể cả khi anh em bị xóa
     * hoặc xếp lại thứ tự; nút mới (id không có trong bản lưu) không nhận giá trị mẫu nào khi
     * đề có mẫu, còn đề không mẫu thì giữ nguyên giá trị client gửi.
     */
    public static ExamStructureDocument restoreTemplateOwned(ExamStructureDocument incoming,
            ExamStructureDocument stored, ExamStructureDocument templateDoc) {
        boolean templateOwned = templateDoc != null;
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
                        : restoreSection(s, storedSections, storedBlocks, templateOwned));
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
                            : restoreSection(s, storedSections, storedBlocks, templateOwned));
                }
                List<QuestionBlock> oBlocks = new ArrayList<>();
                for (QuestionBlock b : o.blocks()) {
                    oBlocks.add(b == null ? null : restoreBlock(b, storedBlocks, templateOwned));
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

    private static ExamSection restoreSection(ExamSection s, Map<String, ExamSection> storedSections,
            Map<String, QuestionBlock> storedBlocks, boolean templateOwned) {
        ExamSection stored = s.id() == null ? null : storedSections.get(s.id());
        GroupOrder groupOrder = stored != null ? stored.groupOrder()
                : templateOwned ? null : s.groupOrder();
        List<QuestionBlock> blocks = new ArrayList<>();
        for (QuestionBlock b : s.blocks()) {
            blocks.add(b == null ? null : restoreBlock(b, storedBlocks, templateOwned));
        }
        return new ExamSection(s.id(), s.subjectCode(), s.questionCount(), s.knowledgeScope(),
                s.answerQuotas(), s.matrix(), groupOrder, s.choiceGroup(), blocks);
    }

    private static QuestionBlock restoreBlock(QuestionBlock b, Map<String, QuestionBlock> storedBlocks,
            boolean templateOwned) {
        if (b == null) {
            return null;
        }
        QuestionBlock stored = b.id() == null ? null : storedBlocks.get(b.id());
        if (stored == null && !templateOwned) {
            return b;
        }
        return new QuestionBlock(b.id(), b.name(), b.questionType(), b.questionCount(),
                b.setCount(), b.setSize(), b.knowledgeScope(), b.matrix(), b.taskTypeCode(),
                b.childAnswerType(), stored == null ? null : stored.templateGroupCode(),
                stored != null && Boolean.TRUE.equals(stored.answerTypeLocked()), b.requiredPointsPerQuestion(),
                stored == null ? null : stored.shuffle());
    }
}
