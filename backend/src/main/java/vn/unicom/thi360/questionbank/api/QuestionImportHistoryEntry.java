package vn.unicom.thi360.questionbank.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.questionbank.infrastructure.QuestionImportErrorLine;

/** Result of an import, also returned by the newest-first history endpoint. */
public record QuestionImportHistoryEntry(UUID id, Instant importedAt, PersonRef importedBy, String fileName,
        int createdCount, int singleCount, int setCount, int setChildCount, int skippedDuplicateCount,
        int newVersionCount, int errorRowCount, int missingKnowledgeCount, long durationMs,
        List<QuestionImportErrorLine> errors) {}
