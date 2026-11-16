package vn.unicom.thi360.exam.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
        List<QuestionBlock> blocks = new ArrayList<>();
        for (QuestionBlock b : s.blocks()) {
            blocks.add(mapBlock(b, regenerate));
        }
        return new ExamSection(id(s.id(), regenerate), s.subjectCode(), s.questionCount(),
                s.knowledgeScope(), s.answerQuotas(), s.matrix(), s.groupOrder(), s.choiceGroup(),
                blocks);
    }

    private static ExamPartOption mapOption(ExamPartOption o, boolean regenerate) {
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
        return new QuestionBlock(id(b.id(), regenerate), b.name(), b.questionType(),
                b.questionCount(), b.setCount(), b.setSize(), b.knowledgeScope(), b.matrix(),
                b.taskTypeCode(), b.childAnswerType(), b.templateGroupCode(), b.answerTypeLocked(),
                b.requiredPointsPerQuestion(), b.shuffle());
    }

    /**
     * Khôi phục trường thuộc mẫu trên tài liệu PUT vào (đã gán id):
     * <ul>
     *   <li>Khối/đoạn còn id khớp bản lưu → giữ {@code shuffle}, {@code answerTypeLocked},
     *       {@code templateGroupCode}, {@code groupOrder} của bản lưu;</li>
     *   <li>Khối mới khai báo {@code templateGroupCode} trùng khối nhóm trong mẫu của bài →
     *       lấy {@code shuffle}/{@code answerTypeLocked} của mẫu;</li>
     *   <li>Còn lại giữ giá trị client gửi.</li>
     * </ul>
     */
    public static ExamStructureDocument restoreTemplateOwned(ExamStructureDocument incoming,
            ExamStructureDocument stored, ExamStructureDocument templateDoc) {
        Map<String, ExamSection> storedSections = new HashMap<>();
        Map<String, QuestionBlock> storedBlocks = new HashMap<>();
        index(stored, storedSections, storedBlocks);
        Map<String, QuestionBlock> templateGroups = new LinkedHashMap<>();
        if (templateDoc != null) {
            index(templateDoc, new HashMap<>(), new HashMap<>());
            collectTemplateGroups(templateDoc, templateGroups);
        }

        List<ExamPart> parts = new ArrayList<>();
        for (ExamPart part : incoming.parts()) {
            List<ExamSection> sections = new ArrayList<>();
            for (ExamSection s : part.sections()) {
                sections.add(restoreSection(s, storedSections, storedBlocks, templateGroups));
            }
            List<ExamPartOption> options = new ArrayList<>();
            for (ExamPartOption o : part.options()) {
                List<ExamSection> oSections = new ArrayList<>();
                for (ExamSection s : o.sections()) {
                    oSections.add(restoreSection(s, storedSections, storedBlocks, templateGroups));
                }
                List<QuestionBlock> oBlocks = new ArrayList<>();
                for (QuestionBlock b : o.blocks()) {
                    oBlocks.add(restoreBlock(b, storedBlocks, templateGroups));
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
            for (ExamSection s : part.sections()) {
                sections.put(s.id(), s);
                s.blocks().forEach(b -> blocks.put(b.id(), b));
            }
            for (ExamPartOption o : part.options()) {
                for (ExamSection s : o.sections()) {
                    sections.put(s.id(), s);
                    s.blocks().forEach(b -> blocks.put(b.id(), b));
                }
                o.blocks().forEach(b -> blocks.put(b.id(), b));
            }
        }
    }

    private static void collectTemplateGroups(ExamStructureDocument doc,
            Map<String, QuestionBlock> groups) {
        for (ExamPart part : doc.parts()) {
            for (ExamSection s : part.sections()) {
                for (QuestionBlock b : s.blocks()) {
                    if (b.templateGroupCode() != null) {
                        groups.putIfAbsent(b.templateGroupCode(), b);
                    }
                }
            }
            for (ExamPartOption o : part.options()) {
                for (ExamSection s : o.sections()) {
                    for (QuestionBlock b : s.blocks()) {
                        if (b.templateGroupCode() != null) {
                            groups.putIfAbsent(b.templateGroupCode(), b);
                        }
                    }
                }
                for (QuestionBlock b : o.blocks()) {
                    if (b.templateGroupCode() != null) {
                        groups.putIfAbsent(b.templateGroupCode(), b);
                    }
                }
            }
        }
    }

    private static ExamSection restoreSection(ExamSection s, Map<String, ExamSection> storedSections,
            Map<String, QuestionBlock> storedBlocks, Map<String, QuestionBlock> templateGroups) {
        ExamSection stored = s.id() == null ? null : storedSections.get(s.id());
        GroupOrder groupOrder = stored != null ? stored.groupOrder() : s.groupOrder();
        List<QuestionBlock> blocks = new ArrayList<>();
        for (QuestionBlock b : s.blocks()) {
            blocks.add(restoreBlock(b, storedBlocks, templateGroups));
        }
        return new ExamSection(s.id(), s.subjectCode(), s.questionCount(), s.knowledgeScope(),
                s.answerQuotas(), s.matrix(), groupOrder, s.choiceGroup(), blocks);
    }

    private static QuestionBlock restoreBlock(QuestionBlock b, Map<String, QuestionBlock> storedBlocks,
            Map<String, QuestionBlock> templateGroups) {
        QuestionBlock stored = b.id() == null ? null : storedBlocks.get(b.id());
        if (stored != null) {
            return new QuestionBlock(b.id(), b.name(), b.questionType(), b.questionCount(),
                    b.setCount(), b.setSize(), b.knowledgeScope(), b.matrix(), b.taskTypeCode(),
                    b.childAnswerType(), stored.templateGroupCode(), stored.answerTypeLocked(),
                    b.requiredPointsPerQuestion(), stored.shuffle());
        }
        if (b.templateGroupCode() != null && templateGroups.containsKey(b.templateGroupCode())) {
            QuestionBlock group = templateGroups.get(b.templateGroupCode());
            return new QuestionBlock(b.id(), b.name(), b.questionType(), b.questionCount(),
                    b.setCount(), b.setSize(), b.knowledgeScope(), b.matrix(), b.taskTypeCode(),
                    b.childAnswerType(), b.templateGroupCode(), group.answerTypeLocked(),
                    b.requiredPointsPerQuestion(), group.shuffle());
        }
        return b;
    }
}
