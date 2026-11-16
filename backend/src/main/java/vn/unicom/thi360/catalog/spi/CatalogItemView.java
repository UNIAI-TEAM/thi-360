package vn.unicom.thi360.catalog.spi;

import java.util.UUID;

public record CatalogItemView(
        UUID id,
        UUID groupId,
        String groupCode,
        String code,
        /** Mã đã chuẩn hóa (định danh danh mục: chữ thường, bỏ khoảng trắng đầu cuối). */
        String codeNormalized,
        int value,
        String nameVi,
        String nameEn,
        CatalogStatus status,
        CatalogStatus effectiveStatus,
        boolean system,
        UUID parentId,
        short depth,
        String path
) {
}
