package vn.unicom.thi360.shared.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

@Schema(description = "Kết quả phân trang")
public record PageResponse<T>(
        @Schema(description = "Danh sách phần tử của trang hiện tại")
        List<T> items,
        @Schema(description = "Chỉ số trang (bắt đầu từ 0)", example = "0")
        int page,
        @Schema(description = "Kích thước trang", example = "20")
        int size,
        @Schema(description = "Tổng số phần tử", example = "0")
        long totalItems,
        @Schema(description = "Tổng số trang", example = "0")
        int totalPages
) {
    public PageResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    public static <E, D> PageResponse<D> from(Page<E> page, Function<E, D> mapper) {
        List<D> mapped = page.getContent().stream().map(mapper).toList();
        return new PageResponse<>(
                mapped,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
