package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.attempt.application.AttemptAssetService;
import vn.unicom.thi360.questionbank.spi.QuestionAssetContentPort.AssetContent;
import vn.unicom.thi360.questionbank.spi.QuestionAssetContentPort.AssetKind;

@RestController
@RequestMapping("/api/v1/me/attempts")
@Tag(name = "Attempts", description = "Chuẩn bị, bắt đầu và đọc trạng thái lượt thi")
public class AttemptAssetController {

    private final AttemptAssetService attemptAssetService;

    public AttemptAssetController(AttemptAssetService attemptAssetService) {
        this.attemptAssetService = attemptAssetService;
    }

    @GetMapping("/{attemptId}/assets/{assetId}")
    @Operation(summary = "Tải tài nguyên câu hỏi (ảnh, tệp đính kèm) thuộc lượt thi của thí sinh")
    @ApiResponse(responseCode = "200", description = "Nội dung tệp tài nguyên")
    public ResponseEntity<Resource> downloadAttemptAsset(
            @PathVariable UUID attemptId, @PathVariable UUID assetId) {
        AssetContent asset = attemptAssetService.loadAttemptAsset(attemptId, assetId);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(asset.mime());
        } catch (InvalidMediaTypeException ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        ContentDisposition disposition =
                ContentDisposition.builder(
                                asset.kind() == AssetKind.IMAGE ? "inline" : "attachment")
                        .filename(asset.fileName(), StandardCharsets.UTF_8)
                        .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new ByteArrayResource(asset.content()));
    }
}
