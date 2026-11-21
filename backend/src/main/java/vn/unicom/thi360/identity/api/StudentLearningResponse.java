package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.identity.spi.StudentLearningProvider.AttemptLearningStatus;
import vn.unicom.thi360.identity.spi.StudentLearningProvider.EntitlementLearningSource;
import vn.unicom.thi360.identity.spi.StudentLearningProvider.EntitlementLearningStatus;

@Schema(description = "Dữ liệu hồ sơ học tập của học viên (quyền sử dụng và lịch sử lượt thi)")
public record StudentLearningResponse(
        @Schema(description = "ID tài khoản", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID userId,
        @Schema(description = "Cho biết dữ liệu học tập đã sẵn sàng từ module thi hay chưa", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean dataAvailable,
        @Schema(description = "Quyền sử dụng và gói bài thi", requiredMode = Schema.RequiredMode.REQUIRED)
        List<StudentLearningEntitlement> entitlements,
        @Schema(description = "Lịch sử lượt thi", requiredMode = Schema.RequiredMode.REQUIRED)
        List<StudentLearningAttempt> attempts,
        @Schema(description = "Kết quả đánh giá năng lực", requiredMode = Schema.RequiredMode.REQUIRED)
        List<Object> assessments,
        @Schema(description = "Lịch sử ôn luyện", requiredMode = Schema.RequiredMode.REQUIRED)
        List<Object> practice,
        @Schema(description = "Yêu cầu hỗ trợ", requiredMode = Schema.RequiredMode.REQUIRED)
        List<Object> supportRequests
) {

    public StudentLearningResponse {
        entitlements = entitlements == null ? List.of() : List.copyOf(entitlements);
        attempts = attempts == null ? List.of() : List.copyOf(attempts);
        assessments = assessments == null ? List.of() : List.copyOf(assessments);
        practice = practice == null ? List.of() : List.copyOf(practice);
        supportRequests = supportRequests == null ? List.of() : List.copyOf(supportRequests);
    }

    @Schema(name = "StudentLearningEntitlement", description = "Một dòng quyền sử dụng bài thi của học viên")
    public record StudentLearningEntitlement(
            @Schema(description = "ID quyền sử dụng", requiredMode = Schema.RequiredMode.REQUIRED)
            UUID entitlementId,
            @Schema(description = "ID bài thi", requiredMode = Schema.RequiredMode.REQUIRED)
            UUID examId,
            @Schema(types = {"string", "null"}, description = "Mã bài thi")
            String examCode,
            @Schema(types = {"string", "null"}, description = "Tên bài thi")
            String examName,
            @Schema(description = "Nguồn cấp quyền", requiredMode = Schema.RequiredMode.REQUIRED)
            EntitlementLearningSource source,
            @Schema(description = "Tổng số lượt được cấp", requiredMode = Schema.RequiredMode.REQUIRED)
            int attemptsTotal,
            @Schema(description = "Số lượt đã sử dụng", requiredMode = Schema.RequiredMode.REQUIRED)
            int attemptsUsed,
            @Schema(description = "Số lượt đang giữ cho lượt thi mở", requiredMode = Schema.RequiredMode.REQUIRED)
            int attemptsReserved,
            @Schema(description = "Số lượt đã thu hồi", requiredMode = Schema.RequiredMode.REQUIRED)
            int attemptsRevoked,
            @Schema(description = "Số lượt còn lại", requiredMode = Schema.RequiredMode.REQUIRED)
            int attemptsLeft,
            @Schema(description = "Trạng thái quyền sử dụng", requiredMode = Schema.RequiredMode.REQUIRED)
            EntitlementLearningStatus status
    ) {
    }

    @Schema(name = "StudentLearningAttempt", description = "Một dòng lịch sử lượt thi của học viên")
    public record StudentLearningAttempt(
            @Schema(description = "ID lượt thi", requiredMode = Schema.RequiredMode.REQUIRED)
            UUID attemptId,
            @Schema(description = "ID bài thi", requiredMode = Schema.RequiredMode.REQUIRED)
            UUID examId,
            @Schema(description = "Tên bài thi", requiredMode = Schema.RequiredMode.REQUIRED)
            String examName,
            @Schema(description = "Mã kỳ thi", requiredMode = Schema.RequiredMode.REQUIRED)
            String examKindCode,
            @Schema(description = "Phạm vi bài thi", requiredMode = Schema.RequiredMode.REQUIRED)
            String scope,
            @Schema(types = {"integer", "null"}, description = "Số thứ tự lượt thi")
            Integer attemptNo,
            @Schema(description = "Trạng thái lượt thi", requiredMode = Schema.RequiredMode.REQUIRED)
            AttemptLearningStatus status,
            @Schema(types = {"string", "null"}, format = "date-time", description = "Thời điểm bắt đầu lượt thi")
            Instant startedAt,
            @Schema(types = {"string", "null"}, format = "date-time", description = "Thời điểm kết thúc lượt thi")
            Instant finishedAt,
            @Schema(types = {"number", "null"}, description = "Điểm hiển thị (chỉ có khi trạng thái là SCORED)")
            BigDecimal score,
            @Schema(types = {"number", "null"}, description = "Thang điểm tối đa (chỉ có khi trạng thái là SCORED)")
            BigDecimal maxScore
    ) {
    }
}
