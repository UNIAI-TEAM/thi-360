package vn.unicom.thi360.catalog.spi;

import java.util.List;
import java.util.UUID;

/**
 * Mã tổ hợp đang hiệu lực của nhóm kiểu COMBINATION: giá trị danh mục + kỳ thi + danh sách môn
 * (theo thứ tự lưu).
 */
public record CatalogCombinationView(
        UUID itemId,
        String itemCode,
        String itemNameVi,
        String itemNameEn,
        String examItemCode,
        String examItemNameVi,
        String examItemNameEn,
        int versionNo,
        List<String> subjectCodes
) {
    public CatalogCombinationView {
        subjectCodes = subjectCodes == null ? List.of() : List.copyOf(subjectCodes);
    }
}
