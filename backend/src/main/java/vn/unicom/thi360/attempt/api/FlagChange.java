package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Thay đổi cờ đánh dấu xem lại của một câu hỏi trong lượt thi (LT §7.1)")
public record FlagChange(
        @NotNull(message = "{validation.attempt.answers.itemId.required}")
        @Schema(description = "Id câu hỏi trong lượt", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID itemId,
        @Schema(description = "Trạng thái cờ đánh dấu xem lại", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean flagged) {}
