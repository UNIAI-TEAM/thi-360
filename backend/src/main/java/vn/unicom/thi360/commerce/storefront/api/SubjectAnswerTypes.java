package vn.unicom.thi360.commerce.storefront.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Các dạng câu và số câu từng dạng của một môn trong một lựa chọn (AD-78): "
        + "mỗi môn tính riêng, không cộng các môn lại với nhau")
public record SubjectAnswerTypes(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SubjectRef subject,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<AnswerTypeGroup> answerTypes) {

    public SubjectAnswerTypes {
        answerTypes = answerTypes == null ? List.of() : List.copyOf(answerTypes);
    }
}
