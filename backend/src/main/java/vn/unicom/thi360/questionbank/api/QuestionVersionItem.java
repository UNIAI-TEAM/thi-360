package vn.unicom.thi360.questionbank.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import vn.unicom.thi360.questionbank.domain.QuestionSnapshot;

@Schema(description = "Một phiên bản trong lịch sử câu hỏi")
public record QuestionVersionItem(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int versionNo,
        @Schema(description = "WORKING (đang sửa), CURRENT (đang phục vụ), SUPERSEDED, RETIRED",
                requiredMode = Schema.RequiredMode.REQUIRED) String state,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant savedAt,
        PersonRef savedBy,
        Instant readyAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) QuestionSnapshot snapshot) {}
