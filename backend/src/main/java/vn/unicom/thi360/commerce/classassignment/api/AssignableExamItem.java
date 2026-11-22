package vn.unicom.thi360.commerce.classassignment.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import vn.unicom.thi360.commerce.storefront.api.ExamKindRef;
import vn.unicom.thi360.commerce.storefront.api.LocalizedName;

@Schema(description = "Bài thi đang xuất bản có thể chọn giao cho lớp học (UNI-2774)")
public record AssignableExamItem(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID examId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ExamKindRef examKind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String scope,
        @Schema(nullable = true) LocalizedName scopeLabel,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String buildMode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int versionNo,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String defaultResultView,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean activeInClass) {}
