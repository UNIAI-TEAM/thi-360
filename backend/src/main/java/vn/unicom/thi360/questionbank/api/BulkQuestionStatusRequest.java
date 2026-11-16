package vn.unicom.thi360.questionbank.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

@Schema(description = "Lệnh đổi trạng thái hàng loạt; id trùng được bỏ trùng, không cần expectedRevision")
public record BulkQuestionStatusRequest(
        @NotEmpty List<UUID> ids) {}
