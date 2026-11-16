package vn.unicom.thi360.exam.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.exam.domain.ExamBlockRef;
import vn.unicom.thi360.exam.domain.ExamPart;
import vn.unicom.thi360.exam.domain.ExamPartOption;
import vn.unicom.thi360.exam.domain.ExamSection;
import vn.unicom.thi360.exam.domain.KnowledgeScope;
import vn.unicom.thi360.exam.domain.OptionKind;
import vn.unicom.thi360.exam.domain.QuestionBlock;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * Dựng lại {@code exam_block_ref} của bản làm việc ({@code version_no IS NULL}) từ tài liệu
 * cấu trúc — spec 4.4.1: mỗi khối câu hỏi một dòng, đủ khoá tra cứu cho BE-5a/BE-5b.
 *
 * <p>Các khoá danh mục (môn, dạng bài, kiến thức, năng lực, tổ hợp) lưu theo code trong tài
 * liệu; projector nhận resolver code → item id để điền cột uuid. Code không tra được → cột null
 * (bản nháp được phép tham chiếu lỏng; kiểm tra cấu hình sẽ bắt sau).
 */
@Component
public class ExamBlockRefProjector {

    /** Resolver code → id giá trị danh mục (theo nhóm), null khi không có. */
    public record CodeResolver(Function<String, UUID> subjectByCode,
                               Function<String, UUID> taskTypeByCode,
                               Function<UUID, List<UUID>> knowledgeLeaves,
                               Function<String, UUID> competencyByCode,
                               Function<String, UUID> combinationByCode) {
    }

    /**
     * @param parts tài liệu cấu trúc đã chuẩn hóa (mọi id đã gán)
     * @param resolver tra cứu danh mục
     * @return một dòng {@link ExamBlockRef} cho mỗi {@link QuestionBlock}, theo thứ tự duyệt
     */
    public List<ExamBlockRef> project(UUID examId, List<ExamPart> parts, CodeResolver resolver) {
        List<ExamBlockRef> rows = new ArrayList<>();
        if (parts == null) {
            return rows;
        }
        Map<String, UUID> subjectCache = new HashMap<>();
        for (ExamPart part : parts) {
            if (part == null) {
                continue;
            }
            String partId = part.id();
            for (ExamSection section : part.sections()) {
                projectSections(examId, rows, resolver, subjectCache, partId, null, section);
            }
            for (ExamPartOption option : part.options()) {
                if (option == null) {
                    continue;
                }
                OptionCtx ctx = new OptionCtx(option.code(), optionCombination(option, resolver),
                        option.kind() == OptionKind.SUBJECT ? option.code() : null);
                for (ExamSection section : option.sections()) {
                    projectSections(examId, rows, resolver, subjectCache, partId, ctx, section);
                }
                for (QuestionBlock block : option.blocks()) {
                    projectBlock(examId, rows, resolver, subjectCache, partId, ctx, null, null, block);
                }
            }
        }
        return rows;
    }

    /** Option kind COMBINATION: {@code code} là mã tổ hợp trong nhóm SUBJECT_COMBINATION. */
    private static UUID optionCombination(ExamPartOption option, CodeResolver resolver) {
        if (option.kind() != OptionKind.COMBINATION || resolver.combinationByCode() == null) {
            return null;
        }
        return resolver.combinationByCode().apply(option.code());
    }

    private record OptionCtx(String optionCode, UUID combinationItemId, String subjectCode) {
    }

    private void projectSections(UUID examId, List<ExamBlockRef> rows, CodeResolver resolver,
            Map<String, UUID> subjectCache, String partId, OptionCtx option, ExamSection section) {
        if (section == null) {
            return;
        }
        for (QuestionBlock block : section.blocks()) {
            projectBlock(examId, rows, resolver, subjectCache, partId, option, section.id(), section,
                    block);
        }
    }

    private void projectBlock(UUID examId, List<ExamBlockRef> rows, CodeResolver resolver,
            Map<String, UUID> subjectCache, String partId, OptionCtx option,
            String sectionId, ExamSection section, QuestionBlock block) {
        if (block == null) {
            return;
        }
        KnowledgeScope sectionScope = section == null ? null : section.knowledgeScope();
        KnowledgeScope scope = block.knowledgeScope() != null ? block.knowledgeScope() : sectionScope;
        boolean inherited = block.knowledgeScope() == null;
        String subjectCode = section != null ? section.subjectCode()
                : option == null ? null : option.subjectCode();
        rows.add(new ExamBlockRef(
                Ids.newId(),
                examId,
                null,
                block.id() == null ? "" : block.id(),
                partId,
                option == null ? null : option.optionCode(),
                sectionId,
                resolveCached(subjectCache, resolver.subjectByCode(), subjectCode),
                block.questionType() != null ? block.questionType().name() : null,
                answerTypes(block, section),
                resolve(resolver.taskTypeByCode(), block.taskTypeCode()),
                knowledgeItemIds(scope, resolver),
                knowledgeMode(scope),
                inherited,
                null,
                competencyItemIds(scope, resolver),
                block.requiredPointsPerQuestion(),
                option == null ? null : option.combinationItemId()));
    }

    /** Các dạng câu có trong khối: questionType + dạng con của bộ câu; khối chưa ghi → quota của section. */
    private static List<String> answerTypes(QuestionBlock block, ExamSection section) {
        Set<String> types = new LinkedHashSet<>();
        if (block.questionType() != null) {
            types.add(block.questionType().name());
        }
        if (block.childAnswerType() != null) {
            types.add(block.childAnswerType().name());
        }
        if (types.isEmpty() && section != null && section.answerQuotas() != null) {
            section.answerQuotas().items().forEach(q -> {
                if (q.answerType() != null) {
                    types.add(q.answerType().name());
                }
            });
        }
        return List.copyOf(types);
    }

    private static List<UUID> knowledgeItemIds(KnowledgeScope scope, CodeResolver resolver) {
        if (scope == null || scope.nodeIds() == null || scope.nodeIds().isEmpty()) {
            return null;
        }
        Set<UUID> ids = new LinkedHashSet<>();
        boolean expand = Boolean.TRUE.equals(scope.includeDescendants());
        for (UUID nodeId : scope.nodeIds()) {
            if (nodeId == null) {
                continue;
            }
            if (expand && resolver.knowledgeLeaves() != null) {
                List<UUID> leaves = resolver.knowledgeLeaves().apply(nodeId);
                if (leaves != null && !leaves.isEmpty()) {
                    ids.addAll(leaves);
                    continue;
                }
            }
            ids.add(nodeId);
        }
        return ids.isEmpty() ? null : List.copyOf(ids);
    }

    private static String knowledgeMode(KnowledgeScope scope) {
        return scope == null || scope.match() == null ? null : scope.match().name();
    }

    private static List<UUID> competencyItemIds(KnowledgeScope scope, CodeResolver resolver) {
        if (scope == null || scope.competencyCodes() == null || scope.competencyCodes().isEmpty()
                || resolver.competencyByCode() == null) {
            return null;
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (String code : scope.competencyCodes()) {
            UUID id = resolver.competencyByCode().apply(code);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids.isEmpty() ? null : List.copyOf(ids);
    }

    private static UUID resolveCached(Map<String, UUID> cache, Function<String, UUID> fn, String code) {
        if (code == null || code.isBlank() || fn == null) {
            return null;
        }
        return cache.computeIfAbsent(code, fn);
    }

    private static UUID resolve(Function<String, UUID> fn, String code) {
        if (code == null || code.isBlank() || fn == null) {
            return null;
        }
        return fn.apply(code);
    }

    /** Tổng hợp cột thống kê trên bảng exam (question_count, has_essay, has_english_option, combination_count). */
    public record ExamStructureSummary(int questionCount, boolean hasEssay,
            boolean hasEnglishOption, int combinationCount) {
    }

    public ExamStructureSummary summarize(List<ExamPart> parts) {
        int questionCount = 0;
        boolean hasEssay = false;
        boolean hasEnglishOption = false;
        Set<String> combinationKeys = new LinkedHashSet<>();
        if (parts != null) {
            for (ExamPart part : parts) {
                if (part == null) {
                    continue;
                }
                for (ExamSection s : part.sections()) {
                    questionCount += blockQuestions(s.blocks());
                    hasEssay |= hasEssay(s.blocks(), s);
                    if ("ANH".equals(s.subjectCode())) {
                        hasEnglishOption = true;
                    }
                }
                for (ExamPartOption o : part.options()) {
                    if (o == null) {
                        continue;
                    }
                    if (o.kind() == OptionKind.COMBINATION) {
                        combinationKeys.add(o.code() == null ? "" : o.code());
                    }
                    if (o.kind() == OptionKind.SUBJECT && "ANH".equals(o.code())) {
                        hasEnglishOption = true;
                    }
                    for (ExamSection s : o.sections()) {
                        questionCount += blockQuestions(s.blocks());
                        hasEssay |= hasEssay(s.blocks(), s);
                        if ("ANH".equals(s.subjectCode())) {
                            hasEnglishOption = true;
                        }
                    }
                    questionCount += blockQuestions(o.blocks());
                    hasEssay |= hasEssay(o.blocks(), null);
                }
            }
        }
        return new ExamStructureSummary(questionCount, hasEssay, hasEnglishOption, combinationKeys.size());
    }

    private static int blockQuestions(List<QuestionBlock> blocks) {
        int count = 0;
        for (QuestionBlock b : blocks) {
            if (b != null && b.questionCount() != null) {
                count += b.questionCount();
            }
        }
        return count;
    }

    private static boolean hasEssay(List<QuestionBlock> blocks, ExamSection section) {
        for (QuestionBlock b : blocks) {
            if (b == null) {
                continue;
            }
            if (b.questionType() == QuestionType.ESSAY || b.childAnswerType() == QuestionType.ESSAY) {
                return true;
            }
        }
        if (section != null && section.answerQuotas() != null) {
            return section.answerQuotas().items().stream()
                    .anyMatch(q -> q.answerType() == QuestionType.ESSAY);
        }
        return false;
    }
}
