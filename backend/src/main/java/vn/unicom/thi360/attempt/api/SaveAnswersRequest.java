package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Schema(description = "Yêu cầu lưu nháp câu trả lời, cờ đánh dấu và vị trí đang xem (LT §7.1)")
public record SaveAnswersRequest(
        @Size(max = 50, message = "{validation.attempt.answers.changes.max}")
        @Schema(description = "Danh sách thay đổi câu trả lời (tối đa 50)", nullable = true)
        List<
                        @NotNull(message = "{validation.attempt.answers.change.required}")
                        @Valid
                        AnswerChange>
                changes,
        @Size(max = 50, message = "{validation.attempt.answers.flags.max}")
        @Schema(description = "Danh sách thay đổi cờ đánh dấu xem lại (tối đa 50)", nullable = true)
        List<
                        @NotNull(message = "{validation.attempt.answers.flag.required}")
                        @Valid
                        FlagChange>
                flags,
        @Valid
        @Schema(description = "Vị trí phần và câu đang xem", nullable = true)
        AttemptPosition position) {

    public SaveAnswersRequest {
        changes =
                changes == null
                        ? List.of()
                        : Collections.unmodifiableList(new ArrayList<>(changes));
        flags =
                flags == null
                        ? List.of()
                        : Collections.unmodifiableList(new ArrayList<>(flags));
    }
}
