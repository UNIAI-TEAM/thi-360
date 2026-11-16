package vn.unicom.thi360.exam.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Một dạng câu hỏi cố định; tên lấy từ danh mục QUESTION_FORMAT khi có, ngược lại tên mặc định trong mã nguồn")
public record QuestionTypeOption(
        @Schema(description = "Mã dạng câu", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Tên hiển thị", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(description = "Có thể tạo câu dạng này; false chỉ khi giá trị danh mục bị ngừng (spec D1)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean active
) {
}
