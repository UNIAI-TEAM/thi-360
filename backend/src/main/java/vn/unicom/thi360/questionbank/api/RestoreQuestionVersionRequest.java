package vn.unicom.thi360.questionbank.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Khôi phục một phiên bản cũ: chép bản chụp vào bản làm việc, xử lý như một lần lưu")
public record RestoreQuestionVersionRequest(@NotNull Long expectedRevision) {}
