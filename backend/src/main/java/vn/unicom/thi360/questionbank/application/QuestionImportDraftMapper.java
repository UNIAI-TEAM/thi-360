package vn.unicom.thi360.questionbank.application;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.questionbank.api.KnowledgeRef;
import vn.unicom.thi360.questionbank.api.SaveQuestionRequest;
import vn.unicom.thi360.questionbank.domain.QuestionBody;
import vn.unicom.thi360.questionbank.domain.importing.ImportCatalog;
import vn.unicom.thi360.questionbank.domain.importing.ImportCatalogOption;
import vn.unicom.thi360.questionbank.domain.importing.ImportQuestionDraft;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/** Converts parsed drafts using the same catalog snapshot that validated them. */
@Component
public class QuestionImportDraftMapper {
    record Mapped(SaveQuestionRequest request, QuestionService.ImportClassification classification) {}

    Mapped map(ImportQuestionDraft draft, ImportCatalog catalog) {
        List<KnowledgeRef> knowledge = draft.knowledge().stream()
                .map(ref -> new KnowledgeRef(ref.nodeId(), ref.code(), null, ref.primary())).toList();
        List<UUID> tags = draft.tagCodes().stream().map(code -> itemId(catalog.tags(), code)).toList();
        QuestionBody body = draft.body();
        if (draft.type() == QuestionType.QUESTION_SET) {
            var children = draft.children().stream().map(child -> new QuestionBody.ChildQuestion(
                    child.type(), child.content(), child.body(), child.solution(), child.points(),
                    itemId(catalog.grades(), child.gradeCode()), itemId(catalog.difficulties(), child.difficultyCode()),
                    child.knowledge().isEmpty() ? null : child.knowledge().stream().map(ref -> ref.nodeId()).toList(),
                    null)).toList();
            body = new QuestionBody.QuestionSetBody(QuestionType.QUESTION_SET, draft.content(), children);
        }
        SaveQuestionRequest request = new SaveQuestionRequest(draft.type(), draft.subjectCode(), draft.gradeCode(),
                draft.difficultyCode(), draft.taskTypeCode(), null, draft.points(), knowledge, draft.tagCodes(),
                draft.content(), draft.solution(), draft.commonMistakes(), List.of(), body, draft.sourceCode());
        var classification = new QuestionService.ImportClassification(itemId(catalog.subjects(), draft.subjectCode()),
                itemId(catalog.grades(), draft.gradeCode()), itemId(catalog.difficulties(), draft.difficultyCode()),
                itemId(catalog.taskTypes(), draft.taskTypeCode()), knowledge, tags);
        return new Mapped(request, classification);
    }

    private UUID itemId(List<ImportCatalogOption> options, String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return options.stream().filter(item -> item.active()
                        && item.code().trim().toUpperCase(Locale.ROOT).equals(normalized))
                .map(ImportCatalogOption::itemId).findFirst()
                .orElseThrow(() -> new IllegalStateException("Validated catalog code is missing: " + code));
    }
}
