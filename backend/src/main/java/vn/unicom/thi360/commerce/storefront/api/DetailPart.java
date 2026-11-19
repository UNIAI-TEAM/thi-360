package vn.unicom.thi360.commerce.storefront.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Một phần của bài thi, theo thứ tự làm bài")
public record DetailPart(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int order,
        @Schema(description = "Tên phần, nội dung bài thi nên không dịch", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(nullable = true) IntRange questionCount,
        @Schema(nullable = true) IntRange durationMinutes,
        @Schema(nullable = true) DecimalRange maxPoints,
        @Schema(description = "true: phần bắt buộc; false: học viên tự chọn", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean required,
        @Schema(description = "Các dạng câu và số câu từng dạng của các nhóm cố định của phần (ai cũng làm); "
                + "lựa chọn và bể môn không cộng vào đây mà nằm ở choices; rỗng khi bản chưa đóng băng chúng",
                requiredMode = Schema.RequiredMode.REQUIRED) List<AnswerTypeGroup> answerTypes) {

    public DetailPart {
        answerTypes = answerTypes == null ? List.of() : List.copyOf(answerTypes);
    }
}
