package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

@Schema(description = "Yêu cầu bắt đầu một lượt thi mới hoặc nối lại lượt đang mở (LT §5.2)")
public record StartAttemptRequest(
        @NotNull(message = "entitlementId là bắt buộc")
        @Schema(description = "Id quyền dùng bài thi", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID entitlementId,
        @Valid
        @Schema(description = "Lựa chọn trước khi bắt đầu (nếu bài có phần chọn trước)", nullable = true)
        PreChoiceInput preChoice) {

    public record PreChoiceInput(
            @Schema(description = "Mã phương án hoặc mã tổ hợp đã chọn", requiredMode = Schema.RequiredMode.REQUIRED)
            String optionCode,
            @Schema(description = "Danh sách mã môn nếu phương án yêu cầu chọn N trong M môn", nullable = true)
            List<String> subjectCodes) {
        public PreChoiceInput {
            subjectCodes = subjectCodes == null ? null : List.copyOf(subjectCodes);
        }
    }
}
