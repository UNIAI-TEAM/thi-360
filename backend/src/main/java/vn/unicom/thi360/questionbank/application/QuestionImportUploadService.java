package vn.unicom.thi360.questionbank.application;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.unicom.thi360.questionbank.domain.importing.CsvImportSheetReader;
import vn.unicom.thi360.questionbank.domain.importing.ImportColumn;
import vn.unicom.thi360.questionbank.domain.importing.ImportColumnMapper;
import vn.unicom.thi360.questionbank.domain.importing.ImportDictionary;
import vn.unicom.thi360.questionbank.domain.importing.ImportFileException;
import vn.unicom.thi360.questionbank.domain.importing.ImportFileType;
import vn.unicom.thi360.questionbank.domain.importing.ImportFiles;
import vn.unicom.thi360.questionbank.domain.importing.ImportIssueCode;
import vn.unicom.thi360.questionbank.domain.importing.ImportLimits;
import vn.unicom.thi360.questionbank.domain.importing.ImportSheet;
import vn.unicom.thi360.questionbank.domain.importing.XlsxImportSheetReader;
import vn.unicom.thi360.questionbank.infrastructure.QuestionImportUpload;
import vn.unicom.thi360.questionbank.infrastructure.QuestionImportUploadRepository;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.storage.ObjectStorage;

/** Stores a validated source file before opening any database row lock. */
@Service
public class QuestionImportUploadService {
    private static final Logger log = LoggerFactory.getLogger(QuestionImportUploadService.class);
    private static final Duration RETENTION = Duration.ofHours(24);

    private final QuestionImportUploadRepository uploads;
    private final ObjectStorage storage;
    private final CurrentUserProvider users;
    private final Clock clock;
    private final ImportLimits limits;

    public QuestionImportUploadService(QuestionImportUploadRepository uploads, ObjectStorage storage,
            CurrentUserProvider users, Clock clock,
            @Value("${thi360.questionbank.import.max-file-bytes:5242880}") long maxFileBytes,
            @Value("${thi360.questionbank.import.max-rows:2000}") int maxRows) {
        this.uploads = uploads;
        this.storage = storage;
        this.users = users;
        this.clock = clock;
        this.limits = new ImportLimits(maxFileBytes, maxRows);
    }

    public record Upload(UUID id, String fileName, ImportFileType fileType, String sheetName,
            int rowCount, List<ImportColumn> columns, Map<String, String> suggestedMapping) {}

    public Upload upload(MultipartFile file) throws ImportFileException {
        if (file == null || file.isEmpty()) {
            throw new ImportFileException(ImportIssueCode.IMPORT_EMPTY_FILE, "Empty import file");
        }
        if (file.getSize() > limits.maxFileBytes()) {
            throw new ImportFileException(ImportIssueCode.IMPORT_FILE_TOO_LARGE, "Import file exceeds the size limit");
        }
        String fileName = file.getOriginalFilename();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ImportFileException(ImportIssueCode.IMPORT_INVALID_FILE, "Cannot read import file", e);
        }
        ImportSheet sheet = read(fileName, bytes);
        if (sheet.rows().isEmpty()) {
            throw new ImportFileException(ImportIssueCode.IMPORT_EMPTY_FILE, "Import file contains no data rows");
        }
        UUID actor = users.requireCurrentUser().id();
        UUID id = UUID.randomUUID();
        String key = "question-imports/" + id;
        Instant now = clock.instant();
        var row = new QuestionImportUpload(id, key, fileName, sheet.fileType(), sheet.sheetName(),
                sheet.rowCount(), sheet.columns(), now.plus(RETENTION), actor, now);
        storage.put(key, bytes, "application/octet-stream");
        try {
            uploads.saveAndFlush(row);
        } catch (RuntimeException ex) {
            try {
                storage.delete(key);
            } catch (RuntimeException cleanup) {
                log.warn("Could not remove source object after upload persistence failed", cleanup);
            }
            throw ex;
        }
        return new Upload(id, fileName, sheet.fileType(), sheet.sheetName(), sheet.rowCount(), sheet.columns(),
                ImportColumnMapper.suggestMapping(sheet.columns(), ImportDictionary.standard()).byColumnKey());
    }

    public ImportSheet read(String fileName, byte[] bytes) throws ImportFileException {
        ImportFileType type = ImportFiles.checkCommon(fileName, bytes, limits);
        return switch (type) {
            case XLSX -> new XlsxImportSheetReader().read(fileName, bytes, limits);
            case CSV -> new CsvImportSheetReader().read(fileName, bytes, limits);
        };
    }
}
