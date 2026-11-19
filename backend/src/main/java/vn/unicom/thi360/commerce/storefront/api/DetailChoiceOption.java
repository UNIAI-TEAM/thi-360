package vn.unicom.thi360.commerce.storefront.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Một lựa chọn trong phạm vi mua: nhánh, môn, môn tự chọn hoặc mã tổ hợp")
public record DetailChoiceOption(
        @Schema(description = "BRANCH, SUBJECT, SUBJECT_CHOICE hoặc COMBINATION", requiredMode = Schema.RequiredMode.REQUIRED)
        String kind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(description = "Nhánh của mã tổ hợp (QDA); null nếu không có", nullable = true) String branch,
        @Schema(description = "SUBJECT_CHOICE: học viên chọn bấy nhiêu môn trong danh sách", nullable = true)
        Integer chooseSubjects,
        @Schema(description = "SUBJECT_CHOICE: số câu mỗi môn theo số môn đã chọn", nullable = true)
        List<Integer> countsByPick,
        @Schema(description = "Các môn của lựa chọn, theo thứ tự (với mã tổ hợp là thứ tự làm bài)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<SubjectRef> subjects) {

    public DetailChoiceOption {
        subjects = subjects == null ? List.of() : List.copyOf(subjects);
        countsByPick = countsByPick == null ? null : List.copyOf(countsByPick);
    }
}
