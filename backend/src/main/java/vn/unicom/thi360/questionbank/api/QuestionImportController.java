package vn.unicom.thi360.questionbank.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.application.QuestionImportUploadService;
import vn.unicom.thi360.questionbank.domain.importing.ImportFileException;
import vn.unicom.thi360.shared.security.RequirePermission;

@Tag(name = "Question imports", description = "Nhập câu hỏi từ Excel hoặc CSV")
@RestController
@RequestMapping("/api/v1/admin/question-imports")
public class QuestionImportController {
    private final QuestionImportUploadService uploadService;

    public QuestionImportController(QuestionImportUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission(Permissions.QUESTION_MODIFY)
    @Operation(summary = "Tải tệp câu hỏi lên để kiểm tra; tệp được giữ 24 giờ")
    public QuestionImportUploadService.Upload upload(@RequestPart("file") MultipartFile file)
            throws ImportFileException {
        return uploadService.upload(file);
    }
}
