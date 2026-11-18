package vn.unicom.thi360.questionbank.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.application.QuestionAssetService;
import vn.unicom.thi360.questionbank.application.QuestionAssetService.DownloadedAsset;
import vn.unicom.thi360.questionbank.domain.QuestionAssetKind;
import vn.unicom.thi360.shared.security.RequirePermission;

/**
 * API tài nguyên câu hỏi (spec 4.5): {@code POST} nhận {@code kind=IMAGE|ATTACHMENT}
 * {@code Content-Disposition} {@code inline} cho ảnh / {@code attachment} cho tệp đính kèm,
 * quyền một-trong {@code QUESTION.VIEW, EXAM.VIEW} (xem trước đề chỉ cần EXAM.VIEW).
 * Nội dung trả về là {@link Resource} để Spring MVC tự xử lý byte range
 * ({@code 206 Partial Content}, {@code Accept-Ranges: bytes}) cho tua audio/video.
 * Giới hạn cỡ 5/20 MB (413), tần suất 60 tệp/10 phút/người (429), loại theo chữ ký byte.
 */
@Tag(name = "Admin Question Assets", description = "Ảnh và tệp đính kèm của ngân hàng câu hỏi")
@RestController
@RequestMapping("/api/v1/admin/question-assets")
public class AdminQuestionAssetController {

    private final QuestionAssetService assetService;

    public AdminQuestionAssetController(QuestionAssetService assetService) {
        this.assetService = assetService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(Permissions.QUESTION_MODIFY)
    @Operation(summary = "Tải ảnh (≤5 MB, PNG/JPEG/WebP) hoặc tệp đính kèm (≤20 MB) của câu hỏi")
    public QuestionAssetUploadResponse upload(
            @Parameter(description = "IMAGE cho ảnh trong nội dung; ATTACHMENT cho tệp đính kèm", required = true)
            @RequestParam("kind") QuestionAssetKind kind,
            @Parameter(description = "Tệp tải lên; loại kiểm theo chữ ký byte, không theo Content-Type", required = true)
            @RequestParam("file") MultipartFile file) {
        return assetService.upload(kind, file);
    }

    @GetMapping("/{id}")
    @RequirePermission(anyOf = {Permissions.QUESTION_VIEW, Permissions.EXAM_VIEW})
    @Operation(summary = "Phục vụ nội dung tài nguyên (inline cho ảnh, attachment cho tệp)")
    public ResponseEntity<Resource> download(@PathVariable UUID id) {
        DownloadedAsset downloaded = assetService.download(id);
        QuestionAssetKind kind = downloaded.asset().getKind();
        ContentDisposition disposition = ContentDisposition
                .builder(kind == QuestionAssetKind.IMAGE ? "inline" : "attachment")
                .filename(downloaded.asset().getFileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(downloaded.asset().getMime()))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                // Không đặt Content-Length: Spring tự tính cho 200 (độ dài tệp) và cho
                // 206 (độ dài vùng byte); đặt tay sẽ khiến 206 mang độ dài cả tệp.
                .body(new ByteArrayResource(downloaded.content()));
    }
}
