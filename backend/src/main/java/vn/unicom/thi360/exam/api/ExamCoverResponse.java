package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "ExamCoverUpload")
public record ExamCoverResponse(UUID assetId,
        @Schema(description = "Opens the image; the cover is attached by PUT listing, not by this upload") String url) { }
