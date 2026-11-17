package vn.unicom.thi360.questionbank.domain.importing;

import java.util.UUID;

/**
 * Một giá trị danh mục mà dòng nhập có thể tham chiếu bằng mã hoặc tên (spec 4.6). Giá trị
 * đã ngừng ({@code active = false}) được coi như không có: lỗi chặn {@code *_UNKNOWN}
 * (spec 4.5 "danh mục không có hoặc đã ngừng").
 */
public record ImportCatalogOption(UUID itemId, String code, String name, boolean active) {

    public ImportCatalogOption {
        if (itemId == null) {
            throw new IllegalArgumentException("itemId");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code");
        }
    }
}
