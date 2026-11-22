package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
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
import vn.unicom.thi360.attempt.application.AttemptSolutionAssetService;
import vn.unicom.thi360.questionbank.spi.QuestionAssetContentPort.AssetContent;
import vn.unicom.thi360.questionbank.spi.QuestionAssetContentPort.AssetKind;

@RestController
@RequestMapping("/api/v1/me/attempts")
@Tag(name = "My Attempts Solution Assets", description = "Tải ảnh/tệp trong lời giải câu hỏi khi xem lại FULL_SOLUTION (BS §15)")
public class AttemptSolutionAssetController {

    private final AttemptSolutionAssetService attemptSolutionAssetService;

    public AttemptSolutionAssetController(AttemptSolutionAssetService attemptSolutionAssetService) {
        this.attemptSolutionAssetService = Objects.requireNonNull(attemptSolutionAssetService);
    }

    @GetMapping("/{attemptId}/solution-assets/{assetId}")
    @Operation(summary = "Tải ảnh hoặc tệp thuộc lời giải câu hỏi trong lượt đã chấm (FULL_SOLUTION)")
    @ApiResponse(
            responseCode = "200",
            description = "Nội dung tệp lời giải",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<Resource> downloadSolutionAsset(
            @PathVariable UUID attemptId, @PathVariable UUID assetId) {
        AssetContent asset = attemptSolutionAssetService.loadSolutionAsset(attemptId, assetId);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(asset.mime());
        } catch (InvalidMediaTypeException ignored) {
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
