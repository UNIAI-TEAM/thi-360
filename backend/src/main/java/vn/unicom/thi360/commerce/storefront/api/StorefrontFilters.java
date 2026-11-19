package vn.unicom.thi360.commerce.storefront.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Bộ lọc của danh mục (TM 10.2): mỗi giá trị kèm số sản phẩm đang bán; giá trị có số 0 không có mặt")
public record StorefrontFilters(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<KindFilter> kinds,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<FormatFilter> formats,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<SubjectFilter> subjects,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PartFilter> parts) {

    public StorefrontFilters {
        kinds = kinds == null ? List.of() : List.copyOf(kinds);
        formats = formats == null ? List.of() : List.copyOf(formats);
        subjects = subjects == null ? List.of() : List.copyOf(subjects);
        parts = parts == null ? List.of() : List.copyOf(parts);
    }

    @Schema(description = "Kỳ thi; tham số kind của danh sách là code")
    public record KindFilter(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nameVi,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nameEn,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long count) {
    }

    @Schema(description = "Hình thức; tham số format của danh sách là code (FULL, PART, SUBJECT)")
    public record FormatFilter(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long count) {
    }

    @Schema(description = "Môn; tham số subject của danh sách là code")
    public record SubjectFilter(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nameVi,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nameEn,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long count) {
    }

    @Schema(description = "Phần của bài thi theo phần (PART); mã phần chỉ có nghĩa trong một kỳ thi, nên cặp (kindCode, code) "
            + "là khóa. Chỉ bài thi theo phần mới có mã phần: bài thi FULL tìm theo hình thức FULL, không theo phần. "
            + "Tham số part của danh sách là code, đi kèm kind, và chỉ khớp bài thi theo phần")
    public record PartFilter(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String kindCode,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
            @Schema(description = "Tên phần, nội dung bài thi nên không dịch", requiredMode = Schema.RequiredMode.REQUIRED)
            String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long count) {
    }
}
