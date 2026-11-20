package vn.unicom.thi360.commerce.entitlement.access.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "Mã sản phẩm cần hỏi quyền dùng (TM 10.2)")
public record ExamAccessQueryRequest(
        @Schema(description = "Mã sản phẩm, tối đa 48; mã trùng chỉ trả một dòng",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.examAccess.codes.required}")
        @Size(max = 48, message = "{validation.examAccess.codes.size}")
        List<@NotBlank(message = "{validation.examAccess.code.blank}") String> codes) {
}
