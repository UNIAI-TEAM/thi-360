package vn.unicom.thi360.commerce.storefront.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Các lựa chọn của một phần trong phạm vi mua")
public record DetailChoiceGroup(
        @Schema(description = "Tên phần có lựa chọn", requiredMode = Schema.RequiredMode.REQUIRED) String partName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<DetailChoiceOption> options) {

    public DetailChoiceGroup {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
