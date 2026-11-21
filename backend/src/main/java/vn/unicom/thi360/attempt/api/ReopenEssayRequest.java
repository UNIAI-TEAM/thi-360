package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "ReopenEssayRequest", description = "Mở lại bản chấm tự luận đã hoàn tất để chấm lại")
public record ReopenEssayRequest(
        @NotBlank(message = "{validation.grading.reason.notBlank}")
        @Size(max = 1000, message = "{validation.grading.reason.size}")
        String reason) {}
