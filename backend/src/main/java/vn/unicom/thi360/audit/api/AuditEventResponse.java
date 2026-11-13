package vn.unicom.thi360.audit.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEventView;
import vn.unicom.thi360.audit.spi.AuditOutcome;

@Schema(description = "Bản ghi nhật ký thao tác")
public record AuditEventResponse(
        @Schema(description = "ID sự kiện (UUID v7)", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(description = "Thời điểm phát sinh (UTC)", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant occurredAt,
        @Schema(description = "ID người thực hiện")
        UUID actorId,
        @Schema(description = "Tên đăng nhập người thực hiện đã lưu")
        String actorUsername,
        @Schema(description = "Danh sách vai trò tại thời điểm thao tác", requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> actorRoles,
        @Schema(description = "Loại tác nhân thực hiện", requiredMode = Schema.RequiredMode.REQUIRED)
        AuditActorType actorType,
        @Schema(description = "Tên module phát sinh", example = "identity", requiredMode = Schema.RequiredMode.REQUIRED)
        String module,
        @Schema(description = "Mã hành động", example = "LOGIN", requiredMode = Schema.RequiredMode.REQUIRED)
        String action,
        @Schema(description = "Loại đối tượng tác động", example = "USER")
        String targetType,
        @Schema(description = "ID đối tượng tác động")
        String targetId,
        @Schema(description = "Nhãn hiển thị của đối tượng")
        String targetLabel,
        @Schema(description = "Giá trị trước khi thay đổi")
        Map<String, Object> before,
        @Schema(description = "Giá trị sau khi thay đổi")
        Map<String, Object> after,
        @Schema(description = "Lý do thao tác")
        String reason,
        @Schema(description = "Kết quả thao tác", requiredMode = Schema.RequiredMode.REQUIRED)
        AuditOutcome outcome,
        @Schema(description = "Mã lỗi nếu thất bại")
        String errorCode,
        @Schema(description = "Mã truy vết yêu cầu (X-Request-Id)")
        String traceId,
        @Schema(description = "Địa chỉ IP nguồn")
        String ip
) {
    public static AuditEventResponse from(AuditEventView view) {
        return new AuditEventResponse(
                view.id(),
                view.occurredAt(),
                view.actorId(),
                view.actorUsername(),
                view.actorRoles(),
                view.actorType(),
                view.module(),
                view.action(),
                view.targetType(),
                view.targetId(),
                view.targetLabel(),
                view.before(),
                view.after(),
                view.reason(),
                view.outcome(),
                view.errorCode(),
                view.traceId(),
                view.ip());
    }
}
