package vn.unicom.thi360.exam.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Thao tác thuần trên {@link ExamStructureDocument}: gán/sinh lại id và khôi phục các trường
 * thuộc mẫu ({@code answerTypeLocked}, {@code templateGroupCode}) khi PUT cấu trúc.
 * Khi PBI-10 sinh đề, câu chùm được đảo như một khối và các câu con giữ nguyên thứ tự.
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
        Map<String, QuestionBlock> oldBlocks = new HashMap<>();
        Map<String, String> blockIds = new HashMap<>();
        if (regenerate) {
            index(doc, oldBlocks);
            oldBlocks.keySet().stream().filter(key -> key != null && !key.isBlank())
                    .forEach(key -> blockIds.put(key, UUID.randomUUID().toString()));
        }
        List<ExamPart> parts = new ArrayList<>();
        for (ExamPart part : doc.parts()) {
            parts.add(mapPart(part, regenerate, blockIds));
        }
        return new ExamStructureDocument(parts, doc.examQuotas());
    }

    private static String id(String current, boolean regenerate) {
        return (regenerate || current == null || current.isBlank())
                ? UUID.randomUUID().toString() : current;
    }

    private static ExamPart mapPart(ExamPart part, boolean regenerate, Map<String, String> blockIds) {
        if (part == null) {
            return null;
        }
        List<ExamSection> sections = new ArrayList<>();
        for (ExamSection s : part.sections()) {
            sections.add(mapSection(s, regenerate, blockIds));
        }
        List<ExamSection> subjectPool = new ArrayList<>();
        for (ExamSection s : part.subjectPool()) {
            subjectPool.add(mapSection(s, regenerate, blockIds));
        }
        List<ExamPartOption> options = new ArrayList<>();
        for (ExamPartOption o : part.options()) {
            options.add(mapOption(o, regenerate, blockIds));
        }
        return new ExamPart(id(part.id(), regenerate), part.order(), part.name(),
                part.durationMinutes(), part.questionCount(), part.questionCountOnPaper(),
                part.questionCountRequired(), part.participation(), part.maxPoints(), sections, options,
                subjectPool, part.codeSource());
    }

    private static ExamSection mapSection(ExamSection s, boolean regenerate, Map<String, String> blockIds) {
        if (s == null) {
            return null;
        }
        List<QuestionBlock> blocks = new ArrayList<>();
        for (QuestionBlock b : s.blocks()) {
            blocks.add(mapBlock(b, regenerate, blockIds));
        }
        return new ExamSection(id(s.id(), regenerate), s.subjectCode(), s.questionCount(),
                s.knowledgeScope(), s.answerQuotas(), s.matrix(), s.choiceGroup(), blocks,
                mapTopicQuotas(s.topicQuotas(), blockIds));
    }


    private static ExamPartOption mapOption(ExamPartOption o, boolean regenerate, Map<String, String> blockIds) {
        if (o == null) {
            return null;
        }
        List<ExamSection> sections = new ArrayList<>();
        for (ExamSection s : o.sections()) {
            sections.add(mapSection(s, regenerate, blockIds));
        }
        List<QuestionBlock> blocks = new ArrayList<>();
        for (QuestionBlock b : o.blocks()) {
            blocks.add(mapBlock(b, regenerate, blockIds));
        }
        return new ExamPartOption(id(o.id(), regenerate), o.kind(), o.code(), o.name(),
                o.chooseSubjects(), o.countsByPick(), sections, blocks, o.expertConfirmed());
    }


    private static QuestionBlock mapBlock(QuestionBlock b, boolean regenerate, Map<String, String> blockIds) {
        if (b == null) {
            return null;
        }
        String blockId = b.id() == null ? null : blockIds.get(b.id());
        return new QuestionBlock(blockId == null ? id(b.id(), regenerate) : blockId, b.name(), b.questionType(),
                b.questionCount(), b.setCount(), b.setSize(), b.knowledgeScope(), b.matrix(),
                b.childAnswerType(), b.templateGroupCode(), b.answerTypeLocked(),
                b.requiredPointsPerQuestion(), b.tagCodes(), b.expectedScoring(), b.shuffleUnits(),
                b.shuffleEditable(), b.slots(), b.childConditions(), b.itemShape(), b.answerFormats(),
                b.omitted(), mapTopicQuotas(b.topicQuotas(), blockIds), null);
    }

    private static List<TopicQuota> mapTopicQuotas(List<TopicQuota> quotas, Map<String, String> blockIds) {
        if (blockIds.isEmpty()) {
            return quotas;
        }
        return quotas.stream().map(q -> new TopicQuota(q.id(), q.name(), q.nodeIds(), q.count(),
                q.basis(), q.enforcement(), q.gradeCodes(), q.difficultyCodes(),
                q.groupIds().stream().map(groupId -> blockIds.getOrDefault(groupId, groupId)).toList(),
                q.percent()))
                .toList();
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
        Map<String, QuestionBlock> storedBlocks = new HashMap<>();
        index(stored, storedBlocks);

        List<ExamPart> parts = new ArrayList<>();
        for (ExamPart part : incoming.parts()) {
            if (part == null) {
                parts.add(null);
                continue;
            }
            List<ExamSection> sections = new ArrayList<>();
            for (ExamSection s : part.sections()) {
                sections.add(s == null ? null : restoreSection(s, storedBlocks, templateOwned));
            }
            List<ExamSection> subjectPool = new ArrayList<>();
            for (ExamSection s : part.subjectPool()) {
                subjectPool.add(s == null ? null : restoreSection(s, storedBlocks, templateOwned));
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
                            : restoreSection(s, storedBlocks, templateOwned));
                }
                List<QuestionBlock> oBlocks = new ArrayList<>();
                for (QuestionBlock b : o.blocks()) {
                    oBlocks.add(b == null ? null : restoreBlock(b, storedBlocks, templateOwned));
                }
                options.add(new ExamPartOption(o.id(), o.kind(), o.code(), o.name(),
                        o.chooseSubjects(), o.countsByPick(), oSections, oBlocks, o.expertConfirmed()));
            }
            parts.add(new ExamPart(part.id(), part.order(), part.name(), part.durationMinutes(),
                    part.questionCount(), part.questionCountOnPaper(), part.questionCountRequired(),
                    part.participation(), part.maxPoints(), sections, options, subjectPool,
                    part.codeSource()));
        }
        return new ExamStructureDocument(parts, incoming.examQuotas());
    }
    private static ExamSection restoreSection(ExamSection s,
            Map<String, QuestionBlock> storedBlocks, boolean templateOwned) {
        List<QuestionBlock> blocks = new ArrayList<>();
        for (QuestionBlock b : s.blocks()) {
            blocks.add(b == null ? null : restoreBlock(b, storedBlocks, templateOwned));
        }
        return new ExamSection(s.id(), s.subjectCode(), s.questionCount(), s.knowledgeScope(),
                s.answerQuotas(), s.matrix(), s.choiceGroup(), blocks, s.topicQuotas());
    }

    private static void index(ExamStructureDocument doc, Map<String, QuestionBlock> blocks) {
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
                s.blocks().forEach(b -> {
                    if (b != null) {
                        blocks.putIfAbsent(b.id(), b);
                    }
                });
            }
            for (ExamSection s : part.subjectPool()) {
                if (s != null) {
                    s.blocks().forEach(b -> {
                        if (b != null) {
                            blocks.putIfAbsent(b.id(), b);
                        }
                    });
                }
            }
            for (ExamPartOption o : part.options()) {
                if (o == null) {
                    continue;
                }
                for (ExamSection s : o.sections()) {
                    if (s == null) {
                        continue;
                    }
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


    private static QuestionBlock restoreBlock(QuestionBlock b, Map<String, QuestionBlock> storedBlocks,
            boolean templateOwned) {
        if (b == null) {
            return null;
        }
        QuestionBlock stored = b.id() == null ? null : storedBlocks.get(b.id());
        if (stored == null && !templateOwned && !Boolean.TRUE.equals(b.shuffleEditable())) {
            return b;
        }
        return new QuestionBlock(b.id(), b.name(), b.questionType(), b.questionCount(),
                b.setCount(), b.setSize(), b.knowledgeScope(), b.matrix(), b.childAnswerType(),
                stored != null ? stored.templateGroupCode() : templateOwned ? null : b.templateGroupCode(),
                stored != null ? Boolean.TRUE.equals(stored.answerTypeLocked())
                        : !templateOwned && Boolean.TRUE.equals(b.answerTypeLocked()),
                b.requiredPointsPerQuestion(), b.tagCodes(), b.expectedScoring(), b.shuffleUnits(),
                stored != null && Boolean.TRUE.equals(stored.shuffleEditable()),
                b.slots(), b.childConditions(), b.itemShape(), b.answerFormats(),
                b.omitted(), b.topicQuotas(), null);
    }
}
