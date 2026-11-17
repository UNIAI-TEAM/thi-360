package vn.unicom.thi360.questionbank.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.questionbank.api.PersonRef;
import vn.unicom.thi360.questionbank.api.QuestionImportHistoryEntry;
import vn.unicom.thi360.questionbank.infrastructure.QuestionImport;
import vn.unicom.thi360.questionbank.infrastructure.QuestionImportRepository;

@Service
public class QuestionImportHistoryService {
    private final QuestionImportRepository imports;
    private final UserDirectory users;

    public QuestionImportHistoryService(QuestionImportRepository imports, UserDirectory users) {
        this.imports = imports;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<QuestionImportHistoryEntry> history() {
        List<QuestionImport> rows = imports.findTop200ByOrderByImportedAtDesc();
        Map<UUID, UserDirectory.UserSummary> authors = users.findByIds(rows.stream()
                .map(QuestionImport::getImportedBy).filter(id -> id != null).distinct().toList());
        return rows.stream().map(row -> toEntry(row,
                row.getImportedBy() == null ? null : authors.get(row.getImportedBy()))).toList();
    }

    QuestionImportHistoryEntry toEntry(QuestionImport row, UserDirectory.UserSummary author) {
        PersonRef person = row.getImportedBy() == null ? null
                : new PersonRef(row.getImportedBy(), author == null ? "" : author.displayName());
        return new QuestionImportHistoryEntry(row.getId(), row.getImportedAt(), person, row.getFileName(),
                row.getCreatedCount(), row.getSingleCount(), row.getSetCount(), row.getSetChildCount(),
                row.getSkippedDuplicateCount(), row.getNewVersionCount(), row.getErrorRowCount(),
                row.getMissingKnowledgeCount(), row.getDurationMs(), row.getErrors());
    }
}
