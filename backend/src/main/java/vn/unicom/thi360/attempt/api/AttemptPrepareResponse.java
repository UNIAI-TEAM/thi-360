package vn.unicom.thi360.attempt.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.domain.AttemptTiming;
import vn.unicom.thi360.attempt.domain.NavigationMode;
import vn.unicom.thi360.commerce.spi.GrantSource;

@Schema(description = "Thông tin chuẩn bị trước khi bắt đầu lượt thi (LT §5.1)")
public record AttemptPrepareResponse(
        @Schema(description = "Quyền dùng được chọn", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID entitlementId,
        @Schema(description = "Bài thi gắn với quyền dùng", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID examId,
        @Schema(description = "Mã bài thi", requiredMode = Schema.RequiredMode.REQUIRED)
        String examCode,
        @Schema(description = "Tên bài thi", requiredMode = Schema.RequiredMode.REQUIRED)
        String examName,
        @Schema(description = "Kỳ thi", requiredMode = Schema.RequiredMode.REQUIRED)
        ExamKindView examKind,
        @Schema(description = "Phạm vi bài thi: FULL, PART hay SUBJECT", requiredMode = Schema.RequiredMode.REQUIRED)
        String scope,
        @Schema(description = "Mã phần nếu là bài theo phần", nullable = true)
        String partCode,
        @Schema(description = "Môn nếu là bài theo môn", nullable = true)
        SubjectView subject,
        @Schema(description = "STANDARD hoặc REVIEW", requiredMode = Schema.RequiredMode.REQUIRED)
        String buildMode,
        @Schema(description = "Nguồn cấp quyền dùng; null khi hết lượt", nullable = true)
        GrantSourceView grantSource,
        @Schema(description = "Hướng dẫn làm bài đã đóng băng theo phiên bản", nullable = true)
        String instructions,
        @Schema(description = "Kiểu đồng hồ: PER_PART hoặc SHARED", requiredMode = Schema.RequiredMode.REQUIRED)
        AttemptTiming timing,
        @Schema(description = "Tổng thời lượng dự kiến (phút)", nullable = true)
        Integer durationMinutes,
        @Schema(description = "Chế độ di chuyển trong bài", requiredMode = Schema.RequiredMode.REQUIRED)
        NavigationMode navigationMode,
        @Schema(description = "Số lượt còn lại trên quyền dùng", requiredMode = Schema.RequiredMode.REQUIRED)
        int attemptsLeft,
        @Schema(description = "Trạng thái phục vụ hiện tại của bài: PUBLISHED hoặc DISCONTINUED",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String servingStatus,
        @Schema(description = "Lượt đang mở (STARTING hoặc IN_PROGRESS) nếu có", nullable = true)
        OpenAttemptView openAttempt,
        @Schema(description = "Nhóm bài tự luận bị lược khỏi lượt thi tự động", requiredMode = Schema.RequiredMode.REQUIRED)
        List<OmittedGroupView> omittedGroups,
        @Schema(description = "Danh sách phần theo thứ tự cố định của bài", requiredMode = Schema.RequiredMode.REQUIRED)
        List<PreparePartView> parts,
        @Schema(description = "Lựa chọn cần quyết định trước khi bắt đầu; null nếu không cần", nullable = true)
        PreChoiceView preChoice,
        @Schema(description = "Lựa chọn sẽ gặp sau khi vào làm (phần mở muộn)", requiredMode = Schema.RequiredMode.REQUIRED)
        List<AfterStartChoiceView> afterStartChoices) {

    public AttemptPrepareResponse {
        omittedGroups = omittedGroups == null ? List.of() : List.copyOf(omittedGroups);
        parts = parts == null ? List.of() : List.copyOf(parts);
        afterStartChoices = afterStartChoices == null ? List.of() : List.copyOf(afterStartChoices);
    }

    public record ExamKindView(UUID id, String code, String nameVi, String nameEn) {}

    public record SubjectView(UUID id, String code, String nameVi, String nameEn) {}

    public record GrantSourceView(GrantSource source, String orderCode, Instant grantedAt) {}

    public record OpenAttemptView(UUID attemptId, AttemptState state) {}

    public record OmittedGroupView(String name, BigDecimal points) {}

    public record PreparePartView(
            String partCode,
            int ord,
            String name,
            boolean required,
            ChoiceTiming choiceTiming,
            Integer durationMinutes,
            Integer questionCount,
            BigDecimal maxPoints) {}

    public record PreChoiceView(
            String partCode,
            String kind,
            String preselectedCode,
            List<ChoiceOptionView> options) {
        public PreChoiceView {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    public record AfterStartChoiceView(
            String partCode,
            int ord,
            String partName,
            String kind,
            List<ChoiceOptionView> options) {
        public AfterStartChoiceView {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    public record ChoiceOptionView(
            String code,
            String name,
            String kind,
            Integer chooseSubjects,
            List<Integer> countsByPick,
            List<ChoiceSubjectView> subjects) {
        public ChoiceOptionView {
            countsByPick = countsByPick == null ? List.of() : List.copyOf(countsByPick);
            subjects = subjects == null ? List.of() : List.copyOf(subjects);
        }
    }

    public record ChoiceSubjectView(String code, String nameVi, String nameEn) {}
}
