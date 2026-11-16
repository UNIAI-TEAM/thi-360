package vn.unicom.thi360.questionbank.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.application.QuestionImportCommitService;
import vn.unicom.thi360.questionbank.application.QuestionImportHistoryService;
import vn.unicom.thi360.questionbank.application.QuestionImportUploadService;
import vn.unicom.thi360.questionbank.application.QuestionImportValidationService;
import vn.unicom.thi360.questionbank.domain.importing.ImportFileException;
import vn.unicom.thi360.questionbank.domain.importing.QuestionImportTemplate;
import vn.unicom.thi360.shared.security.RequirePermission;

@Tag(name = "Question imports", description = "Nhập câu hỏi từ Excel hoặc CSV")
@RestController
@RequestMapping("/api/v1/admin/question-imports")
public class QuestionImportController {
    private final QuestionImportUploadService uploadService;
    private final QuestionImportValidationService validationService;
    private final QuestionImportCommitService commitService;
    private final QuestionImportHistoryService historyService;

    public QuestionImportController(QuestionImportUploadService uploadService,
            QuestionImportValidationService validationService, QuestionImportCommitService commitService,
            QuestionImportHistoryService historyService) {
        this.uploadService = uploadService;
        this.validationService = validationService;
        this.commitService = commitService;
        this.historyService = historyService;
    }

    @GetMapping(value = "/template", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @RequirePermission(Permissions.QUESTION_MODIFY)
    @Operation(summary = "Tải tệp mẫu nhập câu hỏi XLSX", responses = @ApiResponse(responseCode = "200",
            description = "Tệp mẫu XLSX", content = @Content(
                    mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    schema = @Schema(type = "string", format = "binary"))))
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"question-import-template.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(QuestionImportTemplate.create());
    }

    @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission(Permissions.QUESTION_MODIFY)
    @Operation(summary = "Tải tệp câu hỏi lên để kiểm tra; tệp được giữ 24 giờ")
    public QuestionImportUploadService.Upload upload(@RequestPart("file") MultipartFile file)
            throws ImportFileException {
        return uploadService.upload(file);
    }

    public record ValidateRequest(UUID uploadId, Map<String, String> mapping) {}

    @PostMapping("/uploads/{id}/validate")
    @RequirePermission(Permissions.QUESTION_MODIFY)
    @Operation(summary = "Kiểm tra tệp nhập mà không ghi câu hỏi")
    public QuestionImportValidationResponse validate(@PathVariable UUID id,
            @RequestBody ValidateRequest request) throws ImportFileException {
        if (request.uploadId() != null && !id.equals(request.uploadId())) {
            throw new vn.unicom.thi360.shared.api.BusinessRuleException("IMPORT_MAPPING_INVALID");
        }
        return QuestionImportValidationResponse.from(validationService.validate(id, request.mapping()));
    }

    @PostMapping("/uploads/{id}/commit")
    @RequirePermission(Permissions.QUESTION_MODIFY)
    @Operation(summary = "Nhập câu hỏi; Idempotency-Key bắt buộc")
    public QuestionImportHistoryEntry commit(@PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody QuestionImportCommitService.Request request) {
        return commitService.commit(id, key, request);
    }

    @GetMapping
    @RequirePermission(Permissions.QUESTION_VIEW)
    @Operation(summary = "Lịch sử nhập mới nhất trước", description = "Trả tối đa 200 lần nhập mới nhất.")
    public List<QuestionImportHistoryEntry> history() {
        return historyService.history();
    }
}
