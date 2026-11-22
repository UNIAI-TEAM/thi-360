package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.unicom.thi360.exam.application.ExamCoverService;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.security.RequirePermission;

@Tag(name = "Exam listing", description = "Ảnh bìa của bài thi")
@RestController
@RequestMapping("/api/v1/admin/exams/{examId}/cover")
public class ExamCoverController {
    private final ExamCoverService covers;

    public ExamCoverController(ExamCoverService covers) {
        this.covers = covers;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(Permissions.EXAM_MODIFY)
    @Operation(operationId = "uploadExamCover",
            summary = "Tải ảnh bìa (jpg, png, webp; tối đa 2 MB, 4.096 px; cạnh ngắn từ 480 px)",
            description = "Chỉ tạo ảnh. PUT listing mới gắn ảnh vào bài.")
    public ExamCoverResponse upload(@PathVariable UUID examId,
            @Parameter(description = "Tệp ảnh; loại nhận theo chữ ký byte, không theo đuôi", required = true)
            @RequestParam("file") MultipartFile file) {
        return covers.upload(examId, file);
    }

    @GetMapping("/{assetId}")
    @RequirePermission(Permissions.EXAM_VIEW)
    @Operation(operationId = "getExamCover", summary = "Xem ảnh bìa bản nháp")
    @ApiResponse(responseCode = "200", description = "Image bytes",
            content = @Content(mediaType = "image/*", schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<byte[]> download(@PathVariable UUID examId, @PathVariable UUID assetId) {
        ExamCoverService.Cover cover = covers.open(examId, assetId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(cover.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(cover.content());
    }
}
