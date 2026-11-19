package vn.unicom.thi360.commerce.storefront.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tên hai ngôn ngữ của một giá trị danh mục; nội dung bài thi (tên phần) giống nhau ở cả hai")
public record LocalizedName(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nameVi,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nameEn) {
}
