package vn.unicom.thi360.questionbank.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Lệnh đổi trạng thái một câu (Sẵn sàng / Ngừng); expectedRevision bắt buộc (D13)")
public record QuestionStatusRequest(@NotNull Long expectedRevision) {}
